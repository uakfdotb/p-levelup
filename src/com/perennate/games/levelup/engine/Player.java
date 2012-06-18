package com.perennate.games.levelup.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Player {
	String name; //null designates no player
	
	Game game;
	List<Card> hand;
	int level;
	
	//round-dependent constants
	int points;
	boolean defending;
	
	public Player(Game game) {
		name = null;
		this.game = game;
		hand = new ArrayList<Card>();
		level = 2;
		defending = false;
	}
	
	public void init() {
		points = 0;
	}
	
	public void addCard(Card card) {
		hand.add(card);
		Collections.sort(hand, new CardHandComparator(game.getTrumpSuit(), game.getCurrentLevel()));
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
				hand.remove(j);
				j--;
				
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
		Collections.sort(hand, new CardHandComparator(trumpSuit, trumpValue));
	}
	
	//each integer in trickType is how many of a certain card there is
	//for example, a double is {2}
	//4H 4H 5H 5H would be {2, 2}
	//returns number of tricks found matching the specified type
	public boolean searchTrick(int suit, List<Integer> trickType) {
		List<CardTuple> tuples = getTuples(suit);
		
		int lastValue = -1;
		int trickIndex = 0;
		
		for(CardTuple tuple : tuples) {
			if(tuple.getCard().gameSuit == suit && tuple.getAmount() > trickType.get(trickIndex)) {
				if(lastValue != -1 && tuple.getCard().gameValue == lastValue + 1) {
					lastValue++;
					trickIndex++;
					
					if(trickIndex >= trickType.size()) {
						return true;
					}
				} else {
					lastValue = tuple.getCard().gameValue;
					trickIndex = 1;
				}
			}
		}
		
		return false;
	}
	
	//searches for tuples in the player's hand
	public List<CardTuple> getTuples(int suit) {
		List<CardTuple> array = new ArrayList<CardTuple>();
		
		Card currentCard = null;
		int count = 0;
		
		for(int i = 0; i < hand.size(); i++) {
			Card card = hand.get(i);
			
			if(card.gameSuit == suit) {
				if(currentCard != null && card.equals(currentCard)) {
					count++;
				} else {
					if(count >= 2) {
						array.add(new CardTuple(currentCard, count));
					}
					
					currentCard = card;
					count = 1;
				}
			}
		}
		
		if(count >= 2) {
			array.add(new CardTuple(currentCard, count));
		}
		
		return array;
	}
	
	public List<Card> getHand() {
		return hand;
	}
	
	public int getLevel() {
		return level;
	}
	
	public void levelUp(int amount) {
		level += amount;
	}
	
	public String getName() {
		return name;
	}
	
	public String getHandString() {
		return Card.toString(hand);
	}
}
