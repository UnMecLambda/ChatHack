package fr.upem.net.tcp.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.Logger;

import fr.upem.net.tcp.reader.Message;
import fr.upem.net.tcp.reader.MessageReader;
import fr.upem.net.tcp.reader.Reader;

class Context {

	private enum State {
		LOGIN, CONNECT, ERROR
	};

	final private SelectionKey key;
	final private SocketChannel sc;
	final private ByteBuffer bbin = ByteBuffer.allocate(ServerChat.BUFFER_SIZE);
	final private ByteBuffer bbout = ByteBuffer.allocate(ServerChat.BUFFER_SIZE);
	final private ServerChat server;
	final private Reader messageReader;
	static final Logger logger = Logger.getLogger(Context.class.getName());

	final HashMap<String, Boolean> map = new HashMap<>();
	String name;
	final Queue<Message> queue = new LinkedList<>();

	private State state = State.LOGIN;
	private boolean closed = false;

	Context(ServerChat server, SelectionKey key) {
		this.key = key;
		this.sc = (SocketChannel) key.channel();
		this.server = server;
		// TODO
		messageReader = new MessageReader(bbin);
	}

	/**
	 * Process the content of bbin
	 *
	 * The convention is that bbin is in write-mode before the call to process and
	 * after the call
	 *
	 */
	private void processIn() {
		// TODO
		for (;;)
			switch (messageReader.process()) {
			case DONE:
				Message value = (Message) messageReader.get();
				messageReader.reset();
				switch (state) {
				case LOGIN:
					switch (value.indice) {
					case Message.CONNEXION_SANS_MDP:
						connexion(server.connexionSansMdp(value.getNom()), value.getNom());
						break;
					case Message.CONNEXION_AVEC_MDP:
						connexion(server.connexionAvecMdp(value.getNom(), value.getPassWord(), this), value.getNom());
						break;
					default:
						silentlyClose();
						return;
					}
					break;

				case CONNECT:
					server.broadcast(Message.createNomMessage(value.getNom(), value.getMessage()), this);
					break;

				case ERROR:
				default:
					silentlyClose();
					return;
				}

				return;
			case REFILL:
				return;

			case ERROR:
			default:
				silentlyClose();
				return;

			}
	}

	private void connexion(Boolean b, String name) {
		if (b) {
			state = State.CONNECT;
			this.name = name;
			queue.add(Message.createValidationConnexion(true));
		} else
			queue.add(Message.createValidationConnexion(false));
	}

	/**
	 * Add a message to the message queue, tries to fill bbOut and updateInterestOps
	 *
	 * @param msg
	 */
	void queueMessage(Message msg) {
		if(msg.indice==Message.DEMANDE_CONNEXION_PRIVEE_DU_SERVEUR)
			map.put(msg.getNom(), true);
		queue.add(msg);
		processOut();
		updateInterestOps();
	}

	/**
	 * Try to fill bbout from the message queue
	 *
	 */
	private void processOut() {
		// TODO
		while (!queue.isEmpty() && bbout.remaining() >= Integer.BYTES) {
			bbout.put(queue.remove().getBuffer());
		}
	}

	/**
	 * Update the interestOps of the key looking only at values of the boolean
	 * closed and of both ByteBuffers.
	 *
	 * The convention is that both buffers are in write-mode before the call to
	 * updateInterestOps and after the call. Also it is assumed that process has
	 * been be called just before updateInterestOps.
	 */

	private void updateInterestOps() {
		int newInterestOps = 0;
		if (bbin.hasRemaining() && !closed)
			newInterestOps |= SelectionKey.OP_READ;
		if (bbout.position() != 0 || !queue.isEmpty())
			newInterestOps |= SelectionKey.OP_WRITE;
		if (newInterestOps == 0) {
			silentlyClose();
		} else {
			key.interestOps(newInterestOps);
		}
	}

	void silentlyClose() {
		try {
			sc.close();
		} catch (IOException e) {
			// ignore exception
		}
	}

	/**
	 * Performs the read action on sc
	 *
	 * The convention is that both buffers are in write-mode before the call to
	 * doRead and after the call
	 *
	 * @throws IOException
	 */
	void doRead() throws IOException {
		if (sc.read(bbin) == -1) {
			System.out.println("Oups");
			closed = true;
		}
		processIn();
		updateInterestOps();
	}

	/**
	 * Performs the write action on sc
	 *
	 * The convention is that both buffers are in write-mode before the call to
	 * doWrite and after the call
	 *
	 * @throws IOException
	 */

	void doWrite() throws IOException {
		bbout.flip();
		sc.write(bbout);
		bbout.compact();
		processOut();
		updateInterestOps();
	}

}