package com.perennate.games.levelup;

import javax.swing.JApplet;

public class LUApplet extends JApplet {
	public void init() {
		LevelUp.APPLET = this;
		
		Launcher launcher = new Launcher();
		add(launcher);
		setSize(launcher.launch.getPreferredSize());
	}
}
