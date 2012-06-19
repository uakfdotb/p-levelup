package com.perennate.games.levelup;

import com.perennate.games.levelup.engine.Game;
import com.perennate.games.levelup.engine.GamePlayerListener;

public abstract class View implements GamePlayerListener, Runnable {
	protected Game game;
	protected int pid;
	protected GameClient client;
	
	public View(Game game) {
		this.game = game;
		game.addListener(this);
		pid = -1;
	}
	
	public void setPID(int pid) {
		this.pid = pid;
	}
	
	public void setClient(GameClient client) {
		this.client = client;
	}
	
	public abstract void eventGameUpdated();
	public abstract void eventConnectError(String message);
	public abstract void eventTerminateError(String message);
	public abstract void eventPlayError(String message);
	public abstract void eventJoined(boolean success);
	public abstract void eventGameLoaded();
	
	public int getPlayer() {
		return pid;
	}
}
