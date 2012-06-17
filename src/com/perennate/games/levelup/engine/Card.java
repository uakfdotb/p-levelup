package com.perennate.games.levelup.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Card {
	public static int SUIT_NONE = -2;
	public static int SUIT_TRUMP = -1;
	public static int SUIT_CLUBS = 0;
	public static int SUIT_DIAMONDS = 1;
	public static int SUIT_HEARTS = 2;
	public static int SUIT_SPADES = 3;
	
	int value; //2=two, 13=king, 14=ace; 15=small joker; 16=big joker
	int suit;
	
	//gameSuit is updated after betting is over
	//in this case, the trump value and suit are all represented by SUIT_TRUMP
	int gameSuit;
	
	//gameValue is similar...
	int gameValue;
	
	public Card(int suit, int value) {
		this.value = value;
		this.suit = suit;
		gameSuit = suit;
	}
	
	//create card based on a unique ID
	public Card(int id) {
		if(id == 52 || id == 53) {
			value = id - 37;
			suit = SUIT_TRUMP;
		} else {
			value = id % 13 + 2;
			suit = id / 13;
		}
		
		gameSuit = suit;
	}
	
	public boolean isTrump(int trumpSuit, int trumpValue) {
		return suit == SUIT_TRUMP || suit == trumpSuit || value == trumpValue;
	}
	
	public int getTrumpWeight(int trumpSuit, int trumpValue) {
		if(!isTrump(trumpSuit, trumpValue)) return value;
		else {
			if(value == 15 || value == 16) return value + 2;
			else if(value == trumpValue) {
				if(suit == trumpSuit) return 16;
				else return 15;
			} else {
				return value;
			}
		}
	}
	
	//returns the points-value of this card
	public int getPoints() {
		if(value == 5) return 5;
		else if(value == 10 || value == 13) return 10;
		else return 0;
	}
	
	public int getValue() {
		return value;
	}
	
	public int getSuit() {
		return suit;
	}
	
	public boolean equals(Object o) {
		if(o instanceof Card) {
			Card c = (Card) o;
			
			if(value == c.value && suit == c.suit) return true;
			else return false;
		} else return false;
	}
	
	public void calculateGameSuit(int trumpSuit, int trumpValue) {
		if(suit == trumpSuit || value == trumpValue) {
			gameSuit = Card.SUIT_TRUMP;
			gameValue = getTrumpWeight(trumpSuit, trumpValue);
		} else {
			gameSuit = suit;
			gameValue=  value;
		}
	}
	
	public String toString() {
		return getValueString(value) + getSuitString(suit);
	}
	
	public static List<Card> getCards(int numDecks) {
		List<Card> deck = new ArrayList<Card>();
		
		for(int k = 0; k < numDecks; k++) {
			for(int i = 0; i < 54; i++) {
				deck.add(new Card(i));
			}
		}
		
		Collections.shuffle(deck);
		return deck;
	}
	
	public static int countCards(Card card, List<Card> array) {
		int num = 0;
		
		for(Card x : array) {
			if(card.equals(x)) num++;
		}
		
		return num;
	}
	
	public static void calculateGameSuit(int trumpSuit, int trumpValue, List<Card> array) {
		for(Card card : array) {
			card.calculateGameSuit(trumpSuit, trumpValue);
		}
	}
	
	//this uses game suit so that trump are considered one suit
	//game suit is set by player once the game starts
	public static int countSuit(int suit, List<Card> array) {
		int num = 0;
		
		for(Card x : array) {
			if(x.gameSuit == suit) num++;
		}
		
		return num;
	}
	
	public static String getValueString(int value) {
		String valStr;
		
		if(value == 2) valStr = "2";
		else if(value == 3) valStr = "3";
		else if(value == 4) valStr = "4";
		else if(value == 5) valStr = "5";
		else if(value == 6) valStr = "6";
		else if(value == 7) valStr = "7";
		else if(value == 8) valStr = "8";
		else if(value == 9) valStr = "9";
		else if(value == 10) valStr = "10";
		else if(value == 11) valStr = "J";
		else if(value == 12) valStr = "Q";
		else if(value == 13) valStr = "K";
		else if(value == 14) valStr = "A";
		else if(value == 15) valStr = "?-";
		else if(value == 16) valStr = "?+";
		else valStr = "??";
		
		return valStr;
	}
	
	public static int getValueInt(String valueStr) {
		int value;

		if(valueStr.equals("2")) value = 2;
		else if(valueStr.equals("3")) value = 3;
		else if(valueStr.equals("4")) value = 4;
		else if(valueStr.equals("5")) value = 5;
		else if(valueStr.equals("6")) value = 6;
		else if(valueStr.equals("7")) value = 7;
		else if(valueStr.equals("8")) value = 8;
		else if(valueStr.equals("9")) value = 9;
		else if(valueStr.equals("10")) value = 10;
		else if(valueStr.equals("J")) value = 11;
		else if(valueStr.equals("Q")) value = 12;
		else if(valueStr.equals("K")) value = 13;
		else if(valueStr.equals("A")) value = 14;
		else if(valueStr.equals("?-")) value = 15;
		else if(valueStr.equals("?+")) value = 16;
		else value = 0;
		
		return value;
	}
	
	public static String getSuitString(int suit) {
		String suitStr;
		
		if(suit == SUIT_CLUBS) suitStr = "C";
		else if(suit == SUIT_DIAMONDS) suitStr = "D";
		else if(suit == SUIT_HEARTS) suitStr = "H";
		else if(suit == SUIT_SPADES) suitStr = "S";
		else if(suit == SUIT_TRUMP) suitStr = "T";
		else suitStr = "?";
		
		return suitStr;
	}
	
	public static int getSuitInt(String suitStr) {
		int suit;

		if(suitStr.equals("C")) suit = SUIT_CLUBS;
		else if(suitStr.equals("D")) suit = SUIT_DIAMONDS;
		else if(suitStr.equals("H")) suit = SUIT_HEARTS;
		else if(suitStr.equals("S")) suit = SUIT_SPADES;
		else if(suitStr.equals("T")) suit = SUIT_TRUMP;
		else suit = SUIT_NONE;
		
		return suit;
	}
}

class CardTuple {
	Card card;
	int amount;
	
	public CardTuple(Card card, int amount) {
		this.card = card;
		this.amount = amount;
	}
	
	public Card getCard() {
		return card;
	}
	
	public int getAmount() {
		return amount;
	}
}