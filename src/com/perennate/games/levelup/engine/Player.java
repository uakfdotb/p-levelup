package com.perennate.games.levelup.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Player {
	Game game;
	List<Card> hand;
	int level;
	
	public Player(Game game) {
		this.game = game;
		hand = new ArrayList<Card>();
		level = 2;
	}
	
	public void addCard(Card card) {
		hand.add(card);
		Collections.sort(hand, new CardHandComparator(Card.SUIT_NONE, game.getCurrentLevel()));
	}
	
	public int countCards(Card card) {
		return Card.countCards(card, hand);
	}
	
	//this is based on game suit so should not be called until state is STATE_PLAYING
	public int countSuit(int suit) {
		return Card.countSuit(suit, hand);
	}
	
	public void removeCard(Card card, int amount) {
		int numRemoved = 0;
		
		for(int j = 0; j < hand.size(); j++) {
			if(hand.get(j).equals(card)) {
				numRemoved++;
				
				if(numRemoved >= amount) break;
			}
		}
	}
	
	public void removeCards(List<Card> cards, List<Integer> amounts) {
		for(int i = 0; i < cards.size(); i++) {
			removeCard(cards.get(i), amounts.get(i));
		}
	}
	
	public void calculateGameSuit(int trumpSuit, int trumpValue) {
		Card.calculateGameSuit(trumpSuit, trumpValue, hand);
	}
	
	//each integer in trickType is how many of a certain card there is
	//for example, a double is {2}
	//4H 4H 5H 5H would be {2, 2}
	//returns number of tricks found matching the specified type
	public int searchTrick(int suit, int[] trickType) {
		int startingValue = -1;
		int typeIndex = 0;
		int cardsFound = 0;
		
		int tricksFound = 0;
		
		for(int i = 0; i < hand.size(); i++) {
			Card card = hand.get(i);
			
			if(card.gameSuit == suit) {
				if(startingValue == -1) {
					startingValue = card.value;
					cardsFound = 1;
				} else {
					//make sure that this is the next card we're searching for
					if(card.value == startingValue + typeIndex) {
						cardsFound++;
					} else {
						startingValue = -1;
						typeIndex = 0;
						cardsFound = 0;
						continue;
					}
				}
				
				//update typeIndex if needed
				if(cardsFound > trickType[typeIndex]) {
					typeIndex++;
					
					if(typeIndex >= trickType.length) {
						startingValue = -1;
						typeIndex = 0;
						cardsFound = 0;
						tricksFound++;
					}
				}
			} else {
				startingValue = -1;
				typeIndex = 0;
				cardsFound = 0;
			}
		}
		
		return tricksFound;
	}
	
	public List<Card> getHand() {
		return hand;
	}
	
	public int getLevel() {
		return level;
	}
	
	public void levelUp() {
		level++;
	}
}
