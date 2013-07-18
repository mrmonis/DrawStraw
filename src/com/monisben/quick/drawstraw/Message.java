package com.monisben.quick.drawstraw;

import java.io.UnsupportedEncodingException;

/**
 * A message passed between client and server
 * 
 * @author Benjamin
 * 
 */
public class Message {

	// Public message codes to determine what is being said
	public static final int HEAD_NAME = 1;
	public static final int HEAD_DRAW = 2;
	public static final int SER_UPDATE = 100;

	// Empty message string
	public static final String EMPTY = "NONE";
	
	// Used to delaminate the data
	private static final String SPLIT = "/";
	private static final String DATA_UNPACK = "\\?";
	private static final String DATA_PACK = "?";

	// The header and data
	public int header;
	public String[] data;

	/* Create the message */
	public Message(int header, String[] data) {
		this.header = header;
		this.data = new String[data.length];
		System.arraycopy(data, 0, this.data, 0, data.length);
	}
	
	/* Create the message by unpacking the data string */
	public Message(int header, String packedData) {
		this.header = header;
		unpack(packedData);
	}

	/* Returns one long string representing the message */
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append(header);
		builder.append(SPLIT);
		builder.append(pack());
		return builder.toString();
	}

	/* Packs the data into a string representation */
	public String pack() {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < this.data.length; i++) {
			builder.append(this.data[i]);
			if (i < (data.length - 1)) {
				builder.append(DATA_PACK);
			}
		}
		return builder.toString();
	}

	/* Unpacks the data and stores */
	public void unpack(String data) {
		this.data = data.split(DATA_UNPACK);
	}

	/* Checks what type of message this message is */
	public boolean is(int headerCode) {
		return header == headerCode;
	}

	/* Encodes this message */
	public byte[] encode() throws UnsupportedEncodingException {
		return this.toString().getBytes("US-ASCII");
	}

	/* Decodes a message from the given bytes */
	public static Message decode(byte[] data)
			throws UnsupportedEncodingException {
		// Get the raw string
		String raw = new String(data, "US-ASCII");
		
		// Split the header apart
		String[] split = raw.split(SPLIT);
		int head = Integer.parseInt(split[0]);
		String dat = "";
		if (split.length > 1) {
			dat = split[1];
		}
		return new Message(head, dat);
	}

}
