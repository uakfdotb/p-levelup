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
	
	GamePaintThread paintThread;

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
		
		paintThread = new GamePaintThread(uglyPanel.gamePanel, uglyPanel.buttonsPanel);
		paintThread.start();

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
		paintThread.repaint();
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
		paintThread.terminate();
		view.shutdown();
		view.getClient().terminate("window closed");
	}
	
	public void windowActivated(WindowEvent e) {
		repaint();
	}
	
	public void windowClosing(WindowEvent e) {}
	public void windowDeactivated(WindowEvent e) {}
	public void windowDeiconified(WindowEvent e) {}
	public void windowIconified(WindowEvent e) {}
	public void windowOpened(WindowEvent e) {}	
}

class GamePaintThread extends Thread {
	//use paintImmediately to repaint the game instead of repaint
	//but if it's too slow once or twice then only repaint again once
	GamePanel gamePanel;
	GameButtonsPanel buttonsPanel;
	
	boolean doRepaint;
	boolean terminate;
	
	public GamePaintThread(GamePanel gamePanel, GameButtonsPanel buttonsPanel) {
		this.gamePanel = gamePanel;
		this.buttonsPanel = buttonsPanel;
		doRepaint = false;
		terminate = false;
	}
	
	public void repaint() {
		synchronized(this) {
			doRepaint = true;
			this.notifyAll();
		}
	}
	
	public void terminate() {
		terminate = true;
		repaint();
	}
	
	public void run() {
		while(!terminate) {
			synchronized(this) {
				while(!doRepaint) {
					try {
						this.wait();
					} catch(InterruptedException e) {}
				}
				
				doRepaint = false;
			}
			
			if(!terminate) {
				buttonsPanel.updateButtons();
				gamePanel.paintImmediately(0, 0, gamePanel.getWidth(), gamePanel.getHeight());
			}
		}
	}
}