package com.perennate.games.levelup.engine;

import java.util.List;

public class Trick {
	List<Card> cards;
	List<Integer> amounts;
	
	public Trick(List<Card> cards, List<Integer> amounts) {
		this.cards = cards;
		this.amounts = amounts;
	}
	
	public List<Card> getCards() {
		return cards;
	}
	
	public List<Integer> getAmounts() {
		return amounts;
	}
}