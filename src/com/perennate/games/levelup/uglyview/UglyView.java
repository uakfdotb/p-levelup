package com.perennate.games.levelup.uglyview;

import java.util.List;

import javax.swing.JOptionPane;

import com.perennate.games.levelup.GameClient;
import com.perennate.games.levelup.LevelUp;
import com.perennate.games.levelup.View;
import com.perennate.games.levelup.engine.Card;
import com.perennate.games.levelup.engine.Game;

public class UglyView extends View {
	UglyFrame frame;
	boolean hasJoined;
	boolean isShutdown;
	
	public UglyView(Game game) {
		super(game);
		
		hasJoined = false;
		isShutdown = false;
		frame = new UglyFrame(this);
		frame.init();
	}
	
	public void eventUglyError(String message) {
		if(!isShutdown) {
			JOptionPane.showMessageDialog(frame,
					message,
				    "Client error",
				    JOptionPane.ERROR_MESSAGE);
		}
	}
	
	public void eventGameError(String message) {
		//todo: show message in console
	}
	
	public GameClient getClient() {
		return client;
	}
	
	public UglyFrame getFrame() {
		return frame;
	}
	
	public Game getGame() {
		return game;
	}
	
	protected void shutdown() {
		isShutdown = true;
	}
	
	public void eventGameUpdated() {
		synchronized(game) {
			if(game.getState() == Game.STATE_PLAYING) {
				if(pid == game.getNextPlayer()) {
					LevelUp.println("[View] It is now your turn.");
					LevelUp.println("[View] Your cards:" + game.getPlayer(pid).getHandString());
				} else {
					LevelUp.println("[View] It is Player " + game.getNextPlayer() + "'s turn.");
				}
			}
		}
		
		//GamePanel has it's own synchronization
		frame.gameUpdated();
	}
	
	public void eventConnectError(String message) {
		if(!isShutdown) {
			JOptionPane.showMessageDialog(frame,
					message,
				    "Connection error",
				    JOptionPane.ERROR_MESSAGE);
			
			frame.setScreen("login");
		}
	}
	
	public void eventTerminateError(String message) {
		if(!isShutdown) {
			hasJoined = false;
			
			JOptionPane.showMessageDialog(frame,
					message,
				    "Connection terminated",
				    JOptionPane.ERROR_MESSAGE);
			
			frame.setScreen("login");
		}
	}
	
	public void eventPlayError(String message) {
		LevelUp.println("[View] Server says you made an invalid play: " + message);
		
		if(!isShutdown) {
			JOptionPane.showMessageDialog(frame,
					"The server says you made an error in playing: " + message,
				    "You suck at the game",
				    JOptionPane.ERROR_MESSAGE);
			
			frame.repaint();
		}
	}
	
	public void eventJoined(boolean success) {
		LevelUp.println("[View] Join response: " + success);
		
		if(success) {
			hasJoined = true;
			frame.setScreen("game");
		} else {
			JOptionPane.showMessageDialog(frame,
					"The server refused to let you join the game.",
				    "Join rejected",
				    JOptionPane.ERROR_MESSAGE);
		}
	}
	
	public void eventGameLoaded() {
		
	}
	
	//for GamePlayerListener
	
	public void eventPlayerJoined(int pid, String name) {
		
	}
	
	public void eventPlayerLeft(int pid) {

	}
	
	public void eventGameStateChange(int newState) {
		
	}
	
	public void eventDeclare(int pid, int suit, int amount) {

	}
	
	public void eventWithdrawDeclaration(int pid) {

	}
	
	public void eventDefendDeclaration(int pid, int amount) {

	}
	
	public void eventPlayCards(int pid, List<Card> cards, List<Integer> amounts) {

	}
	
	public void eventDealtCard(Card card) {

	}
	
	public void eventUpdateBetCounter(int newCounter) {

	}
	
	public void eventBottom(List<Card> cards) {

	}
	
	public void eventSelectBottom(List<Card> cards) {

	}
	
	public void eventUpdateRoundOverCounter(int newCounter) {

	}
	
	public void run() {
		
	}
}
