package com.perennate.games.levelup.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
	
	//gameSuit is set after betting is over
	//in this case, the trump value and suit are all represented by SUIT_TRUMP
	int gameSuit;
	
	public Card(int value, int suit) {
		this.value = value;
		this.suit = suit;
		gameSuit = SUIT_NONE;
	}
	
	//create card based on a unique ID
	public Card(int id) {
		if(id == 53 || id == 54) {
			value = id - 38;
			suit = SUIT_TRUMP;
		} else {
			value = id % 13;
			suit = id / 13;
		}
	}
	
	public boolean isTrump(int trumpSuit, int trumpValue) {
		return suit == SUIT_TRUMP || suit == trumpSuit || value == trumpValue;
	}
	
	public int getTrumpWeight(int trumpSuit, int trumpValue) {
		if(!isTrump(trumpSuit, trumpValue)) return -1;
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
	
	public boolean equals(Object o) {
		if(o instanceof Card) {
			Card c = (Card) o;
			
			if(value == c.value && suit == c.suit) return true;
			else return false;
		} else return false;
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
			if(card.suit == trumpSuit || card.value == trumpValue) card.gameSuit = Card.SUIT_TRUMP;
			else card.gameSuit = card.suit;
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
}