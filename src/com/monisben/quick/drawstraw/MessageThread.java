package com.monisben.quick.drawstraw;

import java.util.concurrent.LinkedBlockingQueue;

public class MessageThread extends Thread {

	// A blocking queue for inputing messages (thread-safe)
	protected final LinkedBlockingQueue<Message> mMessageQueue;
	
	// A flag to control messages
	protected volatile Thread mRunner;

	public abstract interface MessageListener {
		public void setResponse(String response);
		public void threadKilled(MessageThread thread);
	}
	
	public MessageListener mListener;
	
	public MessageThread() {
		mMessageQueue = new LinkedBlockingQueue<Message>(Integer.MAX_VALUE);
	}
	
	public MessageThread(MessageListener listener) {
		this();
		// Get the listener
		mListener = listener;
	}

	public synchronized void startThread() {
		if (mRunner == null) {
			mRunner = new Thread(this, getName());
			mRunner.start();
		}
	}

	public synchronized void stopThread() {
		if (mRunner != null) {
			Thread moribund = mRunner;
			mRunner = null;
			moribund.interrupt();
			mListener.threadKilled(this);
		}
	}
	
	public synchronized boolean isRunning() {
		return (mRunner != null);
	}

	/* Add a message to the queue */
	public synchronized void setMessage(Message message) {
		this.interrupt();
		mMessageQueue.add(message);
	}

}
