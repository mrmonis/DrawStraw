package com.monisben.quick.drawstraw;

import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.monisben.quick.drawstraw.ClientRequestThread.ClientListener;
import java.awt.FlowLayout;
import javax.swing.JTextArea;

public class DrawStrawWindow implements ActionListener, ClientListener {

	// UI elements
	private JFrame frmDrawStraw;
	private JTextField name1TextField;
	private JTextField name2TextField;
	private JTextField name3TextField;
	private JTextField clientNameTextField;
	private JTextArea serverResponseTextField;
	private JButton submitButton;
	private JButton drawButton;

	// Socket for communication with server
	private ClientRequestThread mThread;
	private JTextField drawStrawText;

	// Flag for connection
	private boolean mConnected;
	
	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					DrawStrawWindow window = new DrawStrawWindow();
					window.frmDrawStraw.setVisible(true);
					
					// Connect to the server
					window.connect();
					
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/* Attempts to connect to the server */
	protected void connect() {
		// Attempt a server connection
		try {
			// Create if null
			if (mThread == null) {
				mThread = new ClientRequestThread(this);
			}
			// Then start
			mThread.startThread();
			mThread.connect();
			mConnected = true;
		} catch (Exception e) {
			setServerResponse("Could not connect to server");
		}

	}

	/* Sets the servers response message */
	protected void setServerResponse(String string) {
		serverResponseTextField.setText(string);
	}

	/**
	 * Create the application.
	 */
	public DrawStrawWindow() {
		// Show the UI
		initialize();
	}

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {
		frmDrawStraw = new JFrame();
		frmDrawStraw.setTitle("Draw Straw");
		frmDrawStraw.setBounds(100, 100, 390, 320);
		frmDrawStraw.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		JPanel mainPanel = new JPanel();
		frmDrawStraw.getContentPane().add(mainPanel, BorderLayout.CENTER);
		mainPanel.setLayout(null);

		JPanel namePanel = new JPanel();
		namePanel.setBounds(10, 55, 200, 140);
		mainPanel.add(namePanel);
		namePanel.setLayout(null);

		name1TextField = new JTextField();
		name1TextField.setEditable(false);
		name1TextField.setBounds(20, 38, 155, 20);
		namePanel.add(name1TextField);
		name1TextField.setColumns(10);

		name2TextField = new JTextField();
		name2TextField.setEditable(false);
		name2TextField.setBounds(20, 69, 155, 20);
		namePanel.add(name2TextField);
		name2TextField.setColumns(10);

		name3TextField = new JTextField();
		name3TextField.setEditable(false);
		name3TextField.setBounds(20, 100, 155, 20);
		namePanel.add(name3TextField);
		name3TextField.setColumns(10);

		JLabel nameLabel = new JLabel("Names:");
		nameLabel.setBounds(10, 11, 46, 14);
		namePanel.add(nameLabel);

		JPanel clientPanel = new JPanel();
		clientPanel.setBounds(10, 11, 352, 33);
		mainPanel.add(clientPanel);
		clientPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));

		JLabel lblYourName = new JLabel("Your name: ");
		clientPanel.add(lblYourName);

		clientNameTextField = new JTextField();
		clientPanel.add(clientNameTextField);
		clientNameTextField.setColumns(10);

		submitButton = new JButton("Submit");
		submitButton.addActionListener(this);
		submitButton.setActionCommand("submit");
		clientPanel.add(submitButton);

		JPanel serverStatusPanel = new JPanel();
		serverStatusPanel.setBounds(10, 206, 352, 64);
		mainPanel.add(serverStatusPanel);
		serverStatusPanel.setLayout(null);

		JLabel statusLabel = new JLabel("Status: ");
		statusLabel.setBounds(10, 11, 46, 14);
		serverStatusPanel.add(statusLabel);

		serverResponseTextField = new JTextArea();
		serverResponseTextField.setWrapStyleWord(true);
		serverResponseTextField.setRows(10);
		serverResponseTextField.setEditable(false);
		serverResponseTextField.setBounds(55, 11, 287, 45);
		serverStatusPanel.add(serverResponseTextField);
		serverResponseTextField.setColumns(10);

		JPanel drawStrawPanel = new JPanel();
		drawStrawPanel.setBounds(222, 55, 140, 140);
		mainPanel.add(drawStrawPanel);
		drawStrawPanel.setLayout(null);

		JLabel drawStrawLabel = new JLabel("Short straw:");
		drawStrawLabel.setBounds(10, 11, 119, 14);
		drawStrawPanel.add(drawStrawLabel);

		drawStrawText = new JTextField();
		drawStrawText.setEditable(false);
		drawStrawText.setBounds(10, 36, 119, 54);
		drawStrawPanel.add(drawStrawText);
		drawStrawText.setColumns(10);
		
		drawButton = new JButton("Draw Straw");
		drawButton.addActionListener(this);
		drawButton.setBounds(10, 101, 119, 23);
		drawButton.setActionCommand("draw");
		drawStrawPanel.add(drawButton);
		
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		// Submit was clicked
		if (e.getActionCommand().equals("submit")) {
			// Button clicked, do stuff
			String name = clientNameTextField.getText();
			
			if (!mConnected) {
				connect();
			}

			mThread.setMessage(new Message(Message.HEAD_NAME, name));
			setServerResponse("Requesting data...");
			submitButton.setEnabled(false);
		}
		
		if(e.getActionCommand().equals("draw")) {
			if(!mConnected) {
				connect();
			}
			
			mThread.setMessage(new Message(Message.HEAD_DRAW, ""));
		}

	}

	/* Set the server response to the given response */
	@Override
	public void setResponse(String message) {
		setServerResponse(message);
	}

	@Override
	public void onClientClosed() {
		// Reset UI elements
		submitButton.setEnabled(true);
		setServerResponse("Server connection lost");

	}

	/* Update each field based on what was sent */
	@Override
	public void onUpdateClient(String[] data) {
		if (data != null) {
			name1TextField.setText(data[0]);
			name2TextField.setText(data[1]);
			name3TextField.setText(data[2]);
			drawStrawText.setText(data[3]);
		}
		
		submitButton.setEnabled(true);
		setServerResponse("Data received");
	}

	/* Thread was killed, just  */
	@Override
	public void threadKilled(MessageThread thread) {
		mThread = null;
		submitButton.setEnabled(true);
		mConnected = false;
	}
}
