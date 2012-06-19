package com.perennate.games.levelup.uglyview;

import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.HashMap;

import javax.swing.JFrame;
import javax.swing.JPanel;

import com.perennate.games.levelup.LevelUp;

public class UglyFrame extends JFrame implements WindowListener {
	UglyView view;
	HashMap<String, JPanel> panels;
	//panels
	LoginPanel loginPanel;
	UglyPanel uglyPanel;

	public UglyFrame(UglyView view) {
		super(LevelUp.LEVELUP_VERSION_STRING);
		
		this.view = view;
		panels = new HashMap<String, JPanel>();
	}

	public void init() {
		loginPanel = new LoginPanel(view);
		addScreen("login", loginPanel);
		
		uglyPanel = new UglyPanel(view);
		addScreen("game", uglyPanel);

		setScreen("login");
		pack();
		addWindowListener(this);
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setVisible(true);
	}

	public void addScreen(String name, JPanel pane) {
		panels.put(name, pane);
	}

	public void gameUpdated() {
		uglyPanel.buttonsPanel.updateButtons();
		uglyPanel.gamePanel.repaint();
	}
	
	public void setScreen(String name) {
		getContentPane().removeAll();
		getContentPane().add(panels.get(name));
		panels.get(name).revalidate();
		repaint();
		pack();
		requestFocus();
		
		LevelUp.debug("[UglyFrame] Set screen to " + name + " (" + panels.get(name) + ")");
		LevelUp.debug("[UglyFrame] Size: " + getSize() + ", " + panels.get(name).getSize());
	}

	public void windowClosed(WindowEvent e) {
		//shut down client and all
		view.shutdown();
		view.getClient().quit();
	}
	
	public void windowActivated(WindowEvent e) {}
	public void windowClosing(WindowEvent e) {}
	public void windowDeactivated(WindowEvent e) {}
	public void windowDeiconified(WindowEvent e) {}
	public void windowIconified(WindowEvent e) {}
	public void windowOpened(WindowEvent e) {}
	
}