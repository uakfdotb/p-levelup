package com.perennate.games.levelup.engine;

import java.util.Comparator;

//orders a card in the hand by value and then suit
public class GameCardHandComparator implements Comparator<Card> {
	public int compare(Card a, Card b) {
		boolean aTrump = a.gameSuit == Card.SUIT_TRUMP;
		boolean bTrump = b.gameSuit == Card.SUIT_TRUMP;
		
		if(a.gameSuit != b.gameSuit) {
			if(aTrump && !bTrump) return -1;
			else if(!aTrump && bTrump) return 1;
			else if(!aTrump && !bTrump)	return a.suit - b.suit;
		}
		
		//they are in the same suit
		return a.gameValue - b.gameValue;
	}
}

class GameCardTupleComparator implements Comparator<CardTuple> {
	Comparator<Card> comparator;
	
	public GameCardTupleComparator() {
		comparator = new GameCardHandComparator();
	}
	
	public int compare(CardTuple a, CardTuple b) {
		return comparator.compare(a.getCard(), b.getCard());
	}
}