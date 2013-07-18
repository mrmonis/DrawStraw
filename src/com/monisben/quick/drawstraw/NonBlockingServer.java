package com.monisben.quick.drawstraw;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class NonBlockingServer implements Runnable {

	// Used by client to connect
	public static final int SERVER_PORT = 9000;
	public static final String SERVER_IP = "50.101.48.41";

	// The number of clients being drawn for
	private static final int NUM_CLIENTS = 3;

	// Servers address and port
	private InetAddress mHostAddress;
	private int mPort;

	// Channel that accepts socket channels
	private ServerSocketChannel mChannel;

	// Selector for the server
	private Selector mSelector;

	private ByteBuffer mBuffer;

	// The number of channels which have accepted a connection
	private Map<SocketChannel, String> mClients;

	// Changes are done in another thread
	private List<ChangeRequest> mChanges = new LinkedList<ChangeRequest>();

	// The loser of the draw
	private String mLoser;

	// A flag indicating we are on draw cooldown
	private volatile boolean mCooldown;
	private static final long COOLDOWN_TIME = 30000;

	public NonBlockingServer(InetAddress hostAddress, int port)
			throws IOException {
		// Initialize name and client list
		mClients = new LinkedHashMap<SocketChannel, String>(NUM_CLIENTS);

		// Initialize variables
		mBuffer = ByteBuffer.allocate(1024);
		mHostAddress = hostAddress;
		mPort = port;
		mSelector = initSelector();

	}

	private Selector initSelector() throws IOException {
		// Create the selector
		Selector socketSelector = SelectorProvider.provider().openSelector();

		// Create a new non-blocking server socket channel
		mChannel = ServerSocketChannel.open();
		mChannel.configureBlocking(false);

		// Bind the server socket to the specified address and port
		InetSocketAddress isa = new InetSocketAddress(mHostAddress, mPort);
		mChannel.socket().bind(isa);

		// Register the server socket channel, indicating interest in accepting
		// connections
		mChannel.register(socketSelector, SelectionKey.OP_ACCEPT);

		return socketSelector;
	}

	/* Start the server */
	public static void main(String[] args) {
		try {
			new Thread(new NonBlockingServer(null, SERVER_PORT)).start();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		// Loop forever
		while (true) {
			try {

				// Iterate over the change list, making all necessary key
				// changes
				synchronized (this.mChanges) {
					Iterator<ChangeRequest> changes = mChanges.iterator();
					while (changes.hasNext()) {
						ChangeRequest change = (ChangeRequest) changes.next();
						switch (change.type) {
						case ChangeRequest.CHANGEOPS:
							SelectionKey key = change.socket.keyFor(mSelector);

							key.interestOps(change.ops);

							break;
						case ChangeRequest.REGISTER:
							change.socket.register(mSelector, change.ops);
							break;
						}
					}
					mChanges.clear();
				}

				mSelector.select();

				Iterator<SelectionKey> selectedKeys = mSelector.selectedKeys()
						.iterator();
				while (selectedKeys.hasNext()) {
					// Get the current iterator and remove the item
					SelectionKey key = selectedKeys.next();
					selectedKeys.remove();

					// Make sure key is valid
					if (!key.isValid()) {
						continue;
					}

					// Check the key's event
					if (key.isAcceptable()) {
						accept(key);
					} else if (key.isReadable()) {
						read(key);
					} else if (key.isWritable()) {
						write(key);
					}
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}

	/* Accepts a connection or closes it */
	private void accept(SelectionKey key) throws IOException {
		// For an accept to be pending, the channel must be a server socket
		// channel
		ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key
				.channel();

		synchronized (mClients) {
			if (mClients.size() <= NUM_CLIENTS) {
				// Accept the connection if less than NUM_CLIENTS, and make it
				// non-blocking
				SocketChannel socketChannel = serverSocketChannel.accept();
				socketChannel.configureBlocking(false);

				// Register the new channel with the Selector, indicating we
				// would
				// like to be notified when there's data waiting to be read
				synchronized (mChanges) {
					mChanges.add(new ChangeRequest(socketChannel,
							ChangeRequest.REGISTER, SelectionKey.OP_READ));
				}

				// Add to the list of channels
				mClients.put(socketChannel, Message.EMPTY);

			} else {
				// Close the channel and cancel the key
				key.channel().close();
				key.cancel();
			}
		}

	}

	/* Picks a random connection from the list */
	private void draw() {
		synchronized (mClients) {
			// Make sure we arent on cooldown
			if (!mCooldown) {

				// Get the names of each client
				final Collection<String> c = mClients.values();
				String[] values = (String[]) (c
						.toArray(new String[NUM_CLIENTS]));

				for (int i = 0; i < NUM_CLIENTS; i++) {
					if (values[i] == null) {
						values[i] = Message.EMPTY;
					}
				}

				// If none of the names are empty, draw the loser
				if (!values[0].equals(Message.EMPTY)
						&& !values[1].equals(Message.EMPTY)
						&& !values[2].equals(Message.EMPTY)) {

					// Get size and create new random
					int size = mClients.size();
					int item = new Random(System.currentTimeMillis())
							.nextInt(size);

					// Loop until at correct index
					int i = 0;
					for (String name : mClients.values()) {
						if (i == item) {
							// Have looped item times, set the name
							mLoser = name;
						}
						i++;
					}

					// Something was picked, lockout for 30 seconds
					if (mLoser != null) {

						// Set the timeout then wait 30 seconds
						Runnable runner = new Runnable() {

							@Override
							public void run() {
								// Wait for ~ COOLDOWN_TIME milliseconds
								mCooldown = true;
								try {
									Thread.sleep(COOLDOWN_TIME);
								} catch (InterruptedException e) {
									e.printStackTrace();
								} finally {
									mCooldown = false;
								}

							}
						};

						// Start the cooldown thread
						new Thread(runner).start();
					}

				}
			}
		}
	}

	private void read(SelectionKey key) throws IOException {
		SocketChannel socketChannel = (SocketChannel) key.channel();

		// Clear the read buffer
		mBuffer.clear();

		// Attempt to read off channel
		int numRead;
		try {
			numRead = socketChannel.read(mBuffer);
		} catch (IOException e) {
			// The remote forcibly closed connection, cancel the selection key
			// and close the channel
			removeClient(key);
			return;
		}

		if (numRead == -1) {
			// Remote entity shut down cleanly. Do the same on our end and
			// cancel channel
			removeClient(key);
			return;
		}

		// Handle data processing in the worker thread
		byte[] dataCopy = new byte[numRead];
		System.arraycopy(mBuffer.array(), 0, dataCopy, 0, numRead);

		Message request = Message.decode(dataCopy);

		// Handle the head message
		if (request.is(Message.HEAD_NAME)) {
			// Set the data and update each client
			mClients.put(socketChannel, request.data[0]);
			broadcast();
		} else if (request.is(Message.HEAD_DRAW)) {
			// Attempt the draw
			draw();

			// If the loser has changed, do nothing
			if (mLoser != null) {
				broadcast();
			}
		}
	}

	// Removes the key's channel for the list and broadcasts an update
	private void removeClient(SelectionKey key) throws IOException {
		synchronized (mClients) {
			// Remove the clients data
			mClients.remove(key.channel());

			// Close the channel, cancel the key and broadcast the change
			key.channel().close();
			key.cancel();

			// Send a broadcast
			broadcast();
		}

	}

	/* Writes to the given channel */
	private void write(SelectionKey key) throws UnsupportedEncodingException,
			IOException {
		// Grab the socket channel
		SocketChannel socketChannel = (SocketChannel) key.channel();

		// Create a buffer and write to the channel
		Message msg = (Message) key.attachment();
		ByteBuffer buffer = ByteBuffer.wrap(msg.encode());
		socketChannel.write(buffer);

		// Set the channel back to read mode
		synchronized (mChanges) {
			mChanges.add(new ChangeRequest(socketChannel,
					ChangeRequest.CHANGEOPS, SelectionKey.OP_READ));
		}

		// Wakeup the selector
		mSelector.wakeup();
	}

	/* Set the write key for all socket channels */
	public void broadcast() throws ClosedChannelException {
		synchronized (mClients) {
			// Write an update message
			final Collection<String> c = mClients.values();
			String[] values = (String[]) (c
					.toArray(new String[NUM_CLIENTS + 1]));

			// Fill up the data array with each clients name
			for (int i = 0; i < NUM_CLIENTS; i++) {
				if (values[i] == null) {
					values[i] = Message.EMPTY;
				}
			}

			// Attach the loser of the straw draw
			if (mLoser != null) {
				values[NUM_CLIENTS] = mLoser;
			} else {
				values[NUM_CLIENTS] = Message.EMPTY;
			}

			// Create the message
			final Message update = new Message(Message.SER_UPDATE, values);

			// Send to each client
			for (SocketChannel channel : mClients.keySet()) {
				if (channel.isConnected()) {
					// Attach the message to the channels key and then attach
					// the write message
					SelectionKey key = channel.keyFor(mSelector);
					key.attach(update);

					synchronized (mChanges) {
						mChanges.add(new ChangeRequest(channel,
								ChangeRequest.CHANGEOPS, SelectionKey.OP_WRITE));
					}
				}
			}
			// Finally, wake up the selector
			mSelector.wakeup();
		}
	}
}
