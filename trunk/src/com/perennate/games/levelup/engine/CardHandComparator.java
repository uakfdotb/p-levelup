package com.perennate.games.levelup.engine;

import java.util.Comparator;

//orders a card in the hand by value and then suit
public class CardHandComparator implements Comparator {
	int trumpSuit;
	int trumpValue;
	
	public CardHandComparator(int trumpSuit, int trumpValue) {
		this.trumpSuit = trumpSuit;
		this.trumpValue = trumpValue;
	}
	
	public int compare(Object o1, Object o2) {
		Card a = (Card) o1;
		Card b = (Card) o2;
		boolean aTrump = a.isTrump(trumpSuit, trumpValue);
		boolean bTrump = b.isTrump(trumpSuit, trumpValue);
		
		if(a.suit != b.suit) {
			if(aTrump && !bTrump) return -1;
			else if(!aTrump && bTrump) return 1;
			else if(!aTrump && !bTrump)	return (a.suit - b.suit) * 13;
		}
		
		//they are in the same suit
		if(aTrump) {
			return a.getTrumpWeight(trumpSuit, trumpValue) - b.getTrumpWeight(trumpSuit, trumpValue);
		} else {
			return a.value - b.value;
		}
	}
}