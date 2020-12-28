package fr.upem.net.tcp.server;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
//import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.HashMap;
//import java.util.LinkedList;
//import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.upem.net.tcp.parsing.Parsing;
import fr.upem.net.tcp.reader.Message;
//import fr.upem.net.tcp.reader.MessageReader;
//import fr.upem.net.tcp.reader.Reader;

public class ServerChat {

	static final int BUFFER_SIZE = 1_024;
	static final Logger logger = Logger.getLogger(ServerChat.class.getName());

	private final ServerSocketChannel serverSocketChannel;
	private final Random rad = new Random(System.currentTimeMillis());
	private final Selector selector;
	final HashMap<String, String> mapNameMdp;

	public ServerChat(int port, HashMap<String, String> mapNameMdp) throws IOException {
		serverSocketChannel = ServerSocketChannel.open();
		serverSocketChannel.bind(new InetSocketAddress(port));
		selector = Selector.open();
		this.mapNameMdp = mapNameMdp;
	}

	public void launch() throws IOException {
		serverSocketChannel.configureBlocking(false);
		serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
		try (Console c = Console.CreateConsole(serverSocketChannel, selector, this::closeAll)) {
			while (!Thread.interrupted()) {
				printKeys(); // for debug
				System.out.println("Starting select");
				try {
					selector.select(this::treatKey);
				} catch (UncheckedIOException tunneled) {
					throw tunneled.getCause();

				}
				System.out.println("Select finished");
			}
		}
	}

	private void closeAll(Selector e) {
		e.keys().forEach(f -> {
			if (f == null)
				return;
			Context context = ((Context) f.attachment());
			if (context != null)
				context.silentlyClose();
		});
	}

	private void treatKey(SelectionKey key) {
		printSelectedKey(key); // for debug
		try {
			if (key.isValid() && key.isAcceptable()) {
				doAccept(key);
			}
		} catch (IOException ioe) {
			// lambda call in select requires to tunnel IOException
			throw new UncheckedIOException(ioe);
		}
		try {
			if (key.isValid() && key.isWritable()) {
				((Context) key.attachment()).doWrite();
			}
			if (key.isValid() && key.isReadable()) {
				((Context) key.attachment()).doRead();
			}
		} catch (IOException e) {
			logger.log(Level.INFO, "Connection closed with client due to IOException", e);
			silentlyClose(key);
		}
	}

	private void doAccept(SelectionKey key) throws IOException {
		SocketChannel sc = serverSocketChannel.accept();
		if (sc == null)
			return;
		sc.configureBlocking(false);
		SelectionKey clientKey = sc.register(selector, SelectionKey.OP_READ);
		clientKey.attach(new Context(this, clientKey));
	}

	private void silentlyClose(SelectionKey key) {
		Channel sc = (Channel) key.channel();
		try {
			sc.close();
		} catch (IOException e) {
			// ignore exception
		}
	}

	/**
	 * Add a message to all connected clients queue
	 *
	 * @param msg
	 */
	void broadcast(Message msg, Context ctxt) {
		switch (msg.indice) {
		case Message.ENVOIE_MESSAGE_PUBLIC:
			for (SelectionKey key : selector.keys()) {
				Object attachment = key.attachment();
				if (attachment == null)
					continue;
				Context cxt = (Context) attachment;
				cxt.queueMessage(msg);
			}
			break;

		case Message.DEMANDE_CONNEXION_PRIVEE_DU_CLIENT:
			selector.keys().forEach(e -> {
				Context tmp = ((Context) e.attachment());
				if (tmp != null && msg.getNom().equals(tmp.name))
					tmp.queueMessage(Message.createNon(Message.DEMANDE_CONNEXION_PRIVEE_DU_SERVEUR, ctxt.name));
			});
			break;

		case Message.ACCEPTE_LA_CONNEXION_PRIVEE_DU_CLIENT:
			connexionPrive(true, msg, ctxt);
			break;

		case Message.REFUS_DE_CONNEXION_PRIVEE_DU_CLIENT:
			connexionPrive(false, msg, ctxt);
			break;
		default:
			ctxt.silentlyClose();
			return;
		}
	}

	private void connexionPrive(boolean b, Message msg, Context ctxt) {
		selector.keys().forEach(e -> {
			Context tmp = ((Context) e.attachment());
			if (tmp != null && msg.getNom().equals(tmp.name)) {
				if (!tmp.map.getOrDefault(tmp.name, false)) {
					ctxt.silentlyClose();
					return;
				}
				tmp.queueMessage(Message.createValidationConnexionPrive(true, ctxt.name, rad.nextLong()));
			}
		});

	}

	public static void main(String[] args) throws NumberFormatException, IOException {
		if (args.length != 2 && args.length != 1) {
			usage();
			return;
		}
		HashMap<String, String> mapNameMdp = new HashMap<>();
		if (args.length == 2)
			for (String s : Parsing.parsing(args[1]))
				mapNameMdp.put(s.split(": ")[0], s.split(": ")[0]);
		new ServerChat(Integer.parseInt(args[0]), mapNameMdp).launch();
	}

	private static void usage() {
		System.out.println("Usage : ServerSumBetter port");
	}

	/***
	 * Theses methods are here to help understanding the behavior of the selector
	 ***/

	private String interestOpsToString(SelectionKey key) {
		if (!key.isValid()) {
			return "CANCELLED";
		}
		int interestOps = key.interestOps();
		ArrayList<String> list = new ArrayList<>();
		if ((interestOps & SelectionKey.OP_ACCEPT) != 0)
			list.add("OP_ACCEPT");
		if ((interestOps & SelectionKey.OP_READ) != 0)
			list.add("OP_READ");
		if ((interestOps & SelectionKey.OP_WRITE) != 0)
			list.add("OP_WRITE");
		return String.join("|", list);
	}

	public void printKeys() {
		Set<SelectionKey> selectionKeySet = selector.keys();
		if (selectionKeySet.isEmpty()) {
			System.out.println("The selector contains no key : this should not happen!");
			return;
		}
		System.out.println("The selector contains:");
		for (SelectionKey key : selectionKeySet) {
			SelectableChannel channel = key.channel();
			if (channel instanceof ServerSocketChannel) {
				System.out.println("\tKey for ServerSocketChannel : " + interestOpsToString(key));
			} else {
				SocketChannel sc = (SocketChannel) channel;
				System.out.println("\tKey for Client " + remoteAddressToString(sc) + " : " + interestOpsToString(key));
			}
		}
	}

	private String remoteAddressToString(SocketChannel sc) {
		try {
			return sc.getRemoteAddress().toString();
		} catch (IOException e) {
			return "???";
		}
	}

	public void printSelectedKey(SelectionKey key) {
		SelectableChannel channel = key.channel();
		if (channel instanceof ServerSocketChannel) {
			System.out.println("\tServerSocketChannel can perform : " + possibleActionsToString(key));
		} else {
			SocketChannel sc = (SocketChannel) channel;
			System.out.println(
					"\tClient " + remoteAddressToString(sc) + " can perform : " + possibleActionsToString(key));
		}
	}

	private String possibleActionsToString(SelectionKey key) {
		if (!key.isValid()) {
			return "CANCELLED";
		}
		ArrayList<String> list = new ArrayList<>();
		if (key.isAcceptable())
			list.add("ACCEPT");
		if (key.isReadable())
			list.add("READ");
		if (key.isWritable())
			list.add("WRITE");
		return String.join(" and ", list);
	}

	boolean connexionAvecMdp(String name, String passWord, Context context) {
		if (null == mapNameMdp.getOrDefault(name, null))
			return false;
		return true;
	}

	boolean connexionSansMdp(String name) {
		if (selector.keys().stream().filter(e -> {
			Context tmp = ((Context) e.attachment());
			return tmp != null && name.equals(tmp.name);
		}).count() != 0)
			return false;

		if (mapNameMdp.containsKey(name))
			return false;
		return true;
	}
}