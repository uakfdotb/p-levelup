package com.perennate.games.levelup.engine;

import java.util.List;

public interface GamePlayerListener {
	public int getPlayer(); //returns the ID of the player we're listening to
	public void eventPlayerJoined(int pid, String name);
	public void eventPlayerLeft(int pid);
	public void eventGameStateChange(int newState);
	
	public void eventDeclare(int pid, int suit, int amount);
	public void eventWithdrawDeclaration(int pid);
	public void eventDefendDeclaration(int pid, int amount);
	public void eventPlayCards(int pid, List<Card> cards, List<Integer> amounts);
	
	public void eventDealtCard(Card card);
	public void eventUpdateBetCounter(int newCounter);

	public void eventBottom(List<Card> cards);
	public void eventSelectBottom(List<Card> cards);
	public void eventUpdateRoundOverCounter(int newCounter);
	
	public void eventPlayerSwapped(int id1, int id2);
	public void eventNewPID(int newPID);
	public void eventResized(int newSize);
}
