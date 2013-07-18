package com.monisben.quick.drawstraw;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Ver. 2.0
 * 
 * Greatly simplified to move all the data management on the server. Just send a
 * message and receive the same response every time!
 * 
 * @author Benjamin Monis
 * 
 */
public class ClientRequestThread extends MessageThread {

	// The selector managing socket channels
	private Selector mSelector;

	// The socket channel which interacts with the server
	private SocketChannel mSocketChannel;

	// A list of PendingChange instances
	private List<ChangeRequest> mChanges = new LinkedList<ChangeRequest>();

	public interface ClientListener extends MessageListener {

		public void onClientClosed();

		public void onUpdateClient(String[] data);
	}

	public ClientRequestThread(ClientListener listener) throws IOException {
		super(listener);

		// Set up the selector
		mSelector = SelectorProvider.provider().openSelector();
	}

	@Override
	public void run() {
		try {

			// Loop until stopped
			while (mRunner == Thread.currentThread()) {

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

				// Select the ready channels
				int readyChannels = mSelector.select();
				if (readyChannels == 0) {
					continue;
				}

				// Extract the keys and get an iterator on them
				Set<SelectionKey> selectedKeys = mSelector.selectedKeys();
				Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

				// Run code based on the key
				while (keyIterator.hasNext()) {
					SelectionKey currentKey = keyIterator.next();

					if (currentKey.isConnectable()) {
						finishConnect(currentKey);
					} else if (currentKey.isReadable()) {
						read(currentKey);
					} else if (currentKey.isWritable()) {
						write(currentKey);
					}

					keyIterator.remove();

				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			// Send stop message and stop thread no matter what
			((ClientListener) mListener).onClientClosed();
			stopThread();
		}
	}

	// Initiate a connection with the server
	public void connect() throws IOException {
		// Create a non-blocking socket channel
		mSocketChannel = SocketChannel.open();
		mSocketChannel.configureBlocking(false);

		// Kick off connection establishment
		mSocketChannel.connect(new InetSocketAddress(InetAddress
				.getByName(NonBlockingServer.SERVER_IP), NonBlockingServer.SERVER_PORT));

		// Register the channel to receive connect events
		synchronized (mChanges) {
			mChanges.add(new ChangeRequest(mSocketChannel,
					ChangeRequest.REGISTER, SelectionKey.OP_CONNECT));
		}

		// Wake up waiting selector
		mSelector.wakeup();
	}

	// Finishes a connection
	private void finishConnect(SelectionKey key) throws IOException {

		mSocketChannel = (SocketChannel) key.channel();

		// Attempt to finish
		try {
			mSocketChannel.finishConnect();

			// Post an empty message
			setMessage(new Message(Message.HEAD_NAME, Message.EMPTY));

		} catch (IOException e) {
			// Set response and cancel key
			mListener.setResponse("Could not connect");
			key.cancel();
		}
	}

	/* Write to the socket channel */
	private void write(SelectionKey key) throws IOException {
		// Get the keys message
		Message msg = (Message) key.attachment();

		// Retrieve the channel and write to it
		mSocketChannel = (SocketChannel) key.channel();
		ByteBuffer buffer = ByteBuffer.wrap(msg.encode());
		mSocketChannel.write(buffer);

		// Set the channel back to read mode
		synchronized (mChanges) {
			mChanges.add(new ChangeRequest(mSocketChannel,
					ChangeRequest.CHANGEOPS, SelectionKey.OP_READ));
		}

		// Wake up waiting selector
		mSelector.wakeup();
	}

	/* Read from the given selection */
	private void read(SelectionKey key) throws IOException {
		// Get the keys channel
		SocketChannel channel = (SocketChannel) key.channel();

		// Create a buffer and read into it
		int numRead;
		ByteBuffer reader = ByteBuffer.allocate(1024);
		try {
			numRead = channel.read(reader);
		} catch (IOException e) {

			// The remote forcibly closed the connection, cancel
			// the selection key and close the channel.
			key.cancel();
			channel.close();
			return;
		}

		if (numRead == -1) {
			// Remote connection closed cleanly
			key.cancel();
			channel.close();
			return;
		}

		// Get the message
		Message response = Message.decode(reader.array());

		// Respond accordingly
		if (response != null && response.is(Message.SER_UPDATE)) {
			((ClientListener) mListener).onUpdateClient(response.data);
		}
	}

	/* Add a message to the server queue */
	@Override
	public synchronized void setMessage(Message message) {

		// Set the selection key to write instead of read
		SelectionKey key = mSocketChannel.keyFor(mSelector);

		// Attach the message and change key to write mode
		key.attach(message);

		synchronized (mChanges) {
			mChanges.add(new ChangeRequest(mSocketChannel,
					ChangeRequest.CHANGEOPS, SelectionKey.OP_WRITE));
		}

		// Wakeup the selector
		mSelector.wakeup();
	}
}
