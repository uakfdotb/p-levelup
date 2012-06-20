package com.perennate.games.levelup.engine;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

//CardTuple represents an amount of a certain type of card
// for example, a double is represented by card=Card and amount=2
//combinations for tricks are represented by a list of CardTuple instances
public class CardTuple {
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
	
	public static List<Card> extractCards(List<CardTuple> trick) {
		List<Card> cards = new ArrayList<Card>();
		for(CardTuple tuple : trick) {
			cards.add(tuple.getCard());
		}
		
		return cards;
	}
	
	public static List<Integer> extractAmounts(List<CardTuple> trick) {
		List<Integer> amounts = new ArrayList<Integer>();
		for(CardTuple tuple : trick) {
			amounts.add(tuple.getAmount());
		}
		
		return amounts;
	}
	
	//creates a trick, which is just a list of card tuples
	//this trick must be in order by the value of the cards
	public static List<CardTuple> createTrick(List<Card> cards, List<Integer> amounts) {
		if(cards.size() != amounts.size()) return null;
		
		List<CardTuple> trick = new ArrayList<CardTuple>();
		
		for(int i = 0; i < cards.size(); i++) {
			//make sure the card isn't already in the list
			// if it is already there, simply add amount to the existing entry
			int existingIndex = findCard(cards.get(i), trick);
			
			if(existingIndex == -1) {
				trick.add(new CardTuple(cards.get(i), amounts.get(i)));
			} else {
				trick.get(existingIndex).amount += amounts.get(i);
			}
		}
		
		//sort the trick by suit, then value within suit
		Collections.sort(trick, new GameCardTupleComparator());
		
		return trick;
	}
	
	//searches the trick for a card
	//returns -1 if the card is not found, or the index in the trick otherwise
	public static int findCard(Card card, List<CardTuple> trick) {
		for(int i = 0; i < trick.size(); i++) {
			if(trick.get(i).getCard().equals(card)) {
				return i;
			}
		}
		
		return -1;
	}
	
	//compares two tricks and sees if the amounts are equal
	//false if inequal, true otherwise
	public static boolean compareCardTupleStructure(List<CardTuple> a, List<CardTuple> b) {
		if(a.size() != b.size()) return false;
		
		for(int i = 0; i < a.size(); i++) {
			if(a.get(i).amount != b.get(i).amount) return false;
		}
		
		return true;
	}
	
	public static void writeTrick(List<CardTuple> trick, OutputStream outStream) throws IOException {
		DataOutputStream out = new DataOutputStream(outStream);
		
		out.write((byte) trick.size());
		
		for(CardTuple tuple : trick) {
			out.write((byte) tuple.card.getId());
			out.write((byte) tuple.amount);
		}
	}
	
	public static List<CardTuple> readTrick(InputStream inStream) throws IOException {
		DataInputStream in = new DataInputStream(inStream);
		
		int size = in.read();
		List<CardTuple> trick = new ArrayList<CardTuple>(size);
		
		for(int i = 0; i < size; i++) {
			CardTuple tuple = new CardTuple(new Card(in.read()), in.read());
			trick.add(tuple);
		}
		
		return trick;
	}
}