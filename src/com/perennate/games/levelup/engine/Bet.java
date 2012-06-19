package com.perennate.games.levelup.engine;

public class Bet {
	int player;
	int suit;
	int amount;
	
	public Bet(int player, int suit, int amount) {
		this.player = player;
		this.suit = suit;
		this.amount = amount;
	}
	
	public int getPlayer() {
		return player;
	}
	
	public int getSuit() {
		return suit;
	}
	
	public int getAmount() {
		return amount;
	}
}