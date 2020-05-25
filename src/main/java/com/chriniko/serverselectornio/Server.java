package com.chriniko.serverselectornio;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

public class Server {

	private final static Map<SocketChannel, ByteBuffer> sockets = new ConcurrentHashMap<>();

	public static void main(String[] args) throws Exception {

		CountDownLatch serverReady = new CountDownLatch(1);

		new Thread(() -> {
			try {
				serverReady.await();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new RuntimeException(e);
			}

			//clients();
		}).start();

		server(serverReady);

	}

	// make some incoming connections...
	private static void clients() {
		try {
			Socket[] sockets = new Socket[3000];
			for (int i = 0; i < sockets.length; i++) {
				sockets[i] = new Socket("localhost", 8080);
			}
		} catch (IOException e) {
			System.err.println(e);
		}
	}

	private static void server(CountDownLatch serverReady) {

		try {
			ServerSocketChannel ss = ServerSocketChannel.open();
			ss.bind(new InetSocketAddress(8080));
			ss.configureBlocking(false);

			Selector selector = Selector.open();
			ss.register(selector, SelectionKey.OP_ACCEPT);

			System.out.println("Server is up...");
			serverReady.countDown();

			while (true) {

				int op = selector.select();// blocking
				Set<SelectionKey> selectedKeys = selector.selectedKeys();

				Iterator<SelectionKey> iterator = selectedKeys.iterator();
				while (iterator.hasNext()) {
					SelectionKey selectionKey = iterator.next();
					iterator.remove();

					// process selection
					try {
						if (selectionKey.isValid()) {
							if (selectionKey.isAcceptable()) {
								accept(selectionKey);
							} else if (selectionKey.isReadable()) {
								read(selectionKey);
							} else if (selectionKey.isWritable()) {
								write(selectionKey);
							}
						}
					} catch (IOException e) {
						System.err.println("server error: " + e);
					}

					sockets.keySet().removeIf(rec -> !rec.isOpen());
				}

			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void write(SelectionKey selectionKey) throws IOException {

		System.out.println("!!!write");

		SocketChannel chan = (SocketChannel) selectionKey.channel();
		ByteBuffer buf = sockets.get(chan);

		buf.flip();
		chan.write(buf); // won't always write everything.

		if (!buf.hasRemaining()) {
			buf.compact();
			selectionKey.interestOps(SelectionKey.OP_READ);
		}

	}

	private static void read(SelectionKey selectionKey) throws IOException {
		System.out.println("!!!read");

		SocketChannel chan = (SocketChannel) selectionKey.channel();

		ByteBuffer buf = sockets.get(chan);

		int data = chan.read(buf);

		if (data == -1) {
			close(chan);
		}

		buf.flip();
		transmogrify(buf);

		// now we need to change state of selector to wait for WRITE events
		selectionKey.interestOps(SelectionKey.OP_WRITE);
	}

	private static void accept(SelectionKey selectionKey) throws IOException {

		ServerSocketChannel ssc = (ServerSocketChannel) selectionKey.channel();
		SocketChannel s = ssc.accept();
		// nonblocking, but never null

		System.out.println("!!!accept: " + s);
		s.configureBlocking(false);
		s.register(selectionKey.selector(), SelectionKey.OP_READ);

		sockets.put(s, ByteBuffer.allocateDirect(80));
	}

	private static void close(SocketChannel chan) {
		try {
			chan.close();
		} catch (IOException ignore) {
		}
	}

	private static void transmogrify(ByteBuffer buf) {
		for (int i = 0; i < buf.limit(); i++) {
			buf.put(i, (byte) transmogrify(buf.get()));
		}
	}

	private static int transmogrify(int data) {
		return Character.isLetter(data) ? data ^ ' ' : data;
	}

}
