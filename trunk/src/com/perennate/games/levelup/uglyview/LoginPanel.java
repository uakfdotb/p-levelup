package com.perennate.games.levelup.uglyview;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import com.perennate.games.levelup.GameClient;

public class LoginPanel extends JPanel implements ActionListener {
	UglyView view;
	DescripField nameField;
	DescripField hostField;
	DescripField portField;
	JButton connect;

	public LoginPanel(UglyView view) {
		super();
		this.view = view;

		nameField = new DescripField("Name:", "", 20);
		nameField.field.addActionListener(this);
		hostField = new DescripField("Host:", "", 20);
		hostField.field.addActionListener(this);
		portField = new DescripField("Port:", GameClient.DEFAULT_PORT + "", 20);
		portField.field.addActionListener(this);
		connect = new JButton("Connect");
		connect.addActionListener(this);

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		add(nameField);
		add(hostField);
		add(portField);
		add(connect);
	}

	public void actionPerformed(ActionEvent e) {
		if(e.getSource() == connect || e.getSource() == nameField.field ||
				e.getSource() == hostField.field || e.getSource() == portField.field) {
			try {
				String hostname = hostField.getField();
				int port = Integer.parseInt(portField.getField());
				String name = nameField.getField();
				
				if(name.equalsIgnoreCase("patrick") || name.contains("patrick") || name.contains("Patrick")) {
					name = "Yatrick Pu";
				}
				
				view.getClient().connect(hostname, port);
				view.getClient().sendJoin(name);
			} catch(NumberFormatException nfe) {
				JOptionPane.showMessageDialog(view.getFrame(), "Port must be a number between 0 and 65535.", "Invalid port", JOptionPane.ERROR_MESSAGE);
			}
		}
	}
}
