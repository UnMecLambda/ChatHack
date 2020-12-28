package fr.upem.net.tcp.server;

import java.io.IOException;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.Objects;
import java.util.Scanner;
import java.util.function.Consumer;

public class Console implements AutoCloseable {

	private final ServerSocketChannel serverSocketChannel;
	private final Selector selector;
	private final Consumer<Selector> closedNow;
	private Thread myThread;

	private Console(ServerSocketChannel serverSocketChannel, Selector selector, Consumer<Selector> closedNow) {
		this.serverSocketChannel = Objects.requireNonNull(serverSocketChannel);
		this.selector = Objects.requireNonNull(selector);
		this.closedNow = Objects.requireNonNull(closedNow);
	}

	private void shutdown() throws IOException {
		System.out.println("shutdown Ok.");
		serverSocketChannel.close();
		selector.wakeup();
	}

	private void shutdownNow() throws IOException {
		System.out.println("shutdownnow Ok.");
		shutdown();
		closedNow.accept(selector);
		selector.wakeup();

	}

	private void info() {
		System.out.println(
				"Number of customer is " + selector.keys().stream().filter(e -> e.attachment() != null).count() + ".");
	}

	private void run() {
		try (Scanner scan = new Scanner(System.in)) {
			while (scan.hasNext() && !Thread.interrupted()) {

				String tmp = scan.next();
				if (null == tmp)
					continue;
				switch (tmp.toUpperCase()) {
				case "INFO":
					info();
					continue;
				case "SHUTDOWN":
					shutdown();
					break;
				case "SHUTDOWNNOW":
					shutdownNow();
					break;
				default:
					System.err.println("Je n'ai pas comprie: \"" + tmp + "\"");
				}
			}
		} catch (IOException ioe) {
		}
	}

	/**
	 * close the class
	 */
	public void close() {
		myThread.interrupt();
	}

	/**
	 * create Console
	 *
	 * @param serverSocketChannel
	 * @param selector
	 * @param closedNow
	 *
	 * @return Console
	 *
	 */
	static public Console CreateConsole(ServerSocketChannel serverSocketChannel, Selector selector,
			Consumer<Selector> closedNow) {
		Console console = new Console(serverSocketChannel, selector, closedNow);
		console.myThread = new Thread(console::run);
		console.myThread.start();
		return console;
	}
}
