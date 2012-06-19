package com.perennate.games.levelup.uglyview;

import java.util.HashMap;

import javax.swing.JFrame;
import javax.swing.JPanel;

import com.perennate.games.levelup.LevelUp;

public class UglyFrame extends JFrame {
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
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setVisible(true);
	}

	public void addScreen(String name, JPanel pane) {
		panels.put(name, pane);
	}

	public void gameUpdated() {
		uglyPanel.buttonsPanel.updateButtons();
		repaint();
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
}