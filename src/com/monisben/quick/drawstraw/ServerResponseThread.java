package com.monisben.quick.drawstraw;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.channels.ClosedByInterruptException;

/**
 * Ver 2.0
 * 
 * Server thread now sends back only one message which updates the clients UI
 * appropriately
 * 
 * @author Benjamin
 * 
 */
public class ServerResponseThread extends MessageThread {

	private Socket mSocket;
	private BufferedInputStream mInStream;
	private BufferedOutputStream mOutStream;

	// The name of the client
	public String name;

	public interface ServerResponseListener extends MessageListener {
		public void dataChanged(ServerResponseThread thread, String[] data);
	}

	public ServerResponseThread(ServerResponseListener listener, Socket s) {
		super(listener);
		mSocket = s;
		name = null;
	}

	/* Retrieves the name of the user using this thread */
	public synchronized String getUser() {
		return name;
	}

	@Override
	public void run() {
		try {
			mInStream = new BufferedInputStream(mSocket.getInputStream());
			mOutStream = new BufferedOutputStream(mSocket.getOutputStream());

			// Loop until stopped
			while (mRunner == Thread.currentThread() && !mSocket.isClosed()) {

				// Handle any server side messages first
				Message message = mMessageQueue.poll();

				if (message != null) {
					// Passing back a message
					sendMessage(message);

				} else {
					try {
						// No message so listen
						Message response = getResponse();

						if (response != null && response.is(Message.HEAD_NAME)) {
							// Inform server of name change request
							((ServerResponseListener) mListener).dataChanged(
									this, response.data);
						}
					} catch (ClosedByInterruptException e) {
						// Read failed, close socket
						e.printStackTrace();
						mSocket.close();
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			stopThread();
		}
	}

	/* Get a message from the other side */
	private Message getResponse() throws IOException {

		// Attempt a read
		byte[] buffer = new byte[1024];
		int read = mInStream.read(buffer);

		if (read > 0) {
			// Read data into new byte array of correct size
			byte[] data = new byte[read];
			System.arraycopy(buffer, 0, data, 0, read);

			// Convert back to string
			return Message.decode(data);
		}

		return null;
	}

	/* Sends the given message to the server */
	private void sendMessage(Message message) throws IOException {
		byte[] data = message.encode();
		mOutStream.write(data);
		mOutStream.flush();

	}
}
