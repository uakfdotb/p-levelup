package com.perennate.games.levelup.engine;

import java.util.Comparator;

//orders a card in the hand by value and then suit
public class CardHandComparator implements Comparator<Card> {
	int trumpSuit;
	int trumpValue;
	
	public CardHandComparator(int trumpSuit, int trumpValue) {
		this.trumpSuit = trumpSuit;
		this.trumpValue = trumpValue;
	}
	
	public int compare(Card a, Card b) {
		boolean aTrump = a.isTrump(trumpSuit, trumpValue);
		boolean bTrump = b.isTrump(trumpSuit, trumpValue);
		
		if(a.suit != b.suit || aTrump != bTrump) {
			if(aTrump && !bTrump) return -1;
			else if(!aTrump && bTrump) return 1;
			else if(!aTrump && !bTrump)	return a.suit - b.suit;
		}
		
		//they are in the same suit
		if(aTrump) {
			int difference = a.getTrumpWeight(trumpSuit, trumpValue) - b.getTrumpWeight(trumpSuit, trumpValue);
			
			//make sure that we aren't dealing with cards of the same
			// getTrumpWeight but not suit
			//this happens for trump suit, for the cards that are trump
			// because of value but not because of suit
			if(difference == 0 && !a.equals(b)) {
				return a.suit - b.suit;
			} else {
				return difference;
			}
		} else {
			return a.value - b.value;
		}
	}
}