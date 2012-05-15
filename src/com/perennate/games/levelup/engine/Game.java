package com.perennate.games.levelup.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Game {
	public static int STATE_INIT = 0;
	public static int STATE_DEALING = 1;
	public static int STATE_BETTING = 2;
	public static int STATE_PLAYING = 3;
	
	int numPlayers;
	int numDecks;
	
	int state;
	List<Card> deck;
	List<Card> bottom;
	List<Player> players;
	
	//current game fields
	int currentLevel;
	int currentDealer; //dealer will get the bottom
	
	//dealing fields
	int lastPlayerDealt;
	
	//betting fields
	List<Bet> bets;
	int betCountDown;
	
	int trumpSuit;
	
	//playing fields
	int startingPlayer;
	int trickCards;
	int nextPlayer;
	
	Trick openingPlay;
	List<Trick> plays;
	
	public Game(int numPlayers) {
		this.numPlayers = numPlayers;
		numDecks = numPlayers / 2;
		
		players = new ArrayList<Player>();
		for(int i = 0; i < numPlayers; i++) {
			players.add(new Player(this));
		}
	}
	
	public void init() {
		state = STATE_INIT;
		deck = Card.getCards(numDecks);
		bottom = new ArrayList<Card>();
		bets = new ArrayList<Bet>();
	}
	
	//make a card declaration (bet)
	//returns false if the declaration is illegal
	public boolean declare(int player, int suit, int amount) {
		if(state != STATE_DEALING && state != STATE_BETTING) return false;
		
		if(amount > bets.get(bets.size() - 1).getAmount() && players.get(player).countCards(new Card(currentLevel, suit)) >= amount) {
			//also make sure that the player has not already made a bet
			// in this case it is valid if another player has bet in between
			//and this bet must have a different suit
			for(int i = 0; i < bets.size(); i++) {
				Bet bet = bets.get(i);
				
				if(bet.player == player) {
					if(i < bets.size() - 1) {
						//remove this bet because player has made a new one
						bets.remove(i);
						i--;
						continue;
					} else {
						return false;
					}
				} else if(bet.suit == suit) {
					if(i < bets.size() - 1) {
						//remove this bet because there is a new one
						bets.remove(i);
						i--;
						continue;
					} else {
						//can't overturn the same suit
						return false;
					}
				}
			}
			
			//add a new bet onto the arraylist
			//this can be defended against if the first player has more of the same card
			betCountDown = 0;
			bets.add(new Bet(player, suit, amount));
			return true;
		} else {
			return false;
		}
	}
	
	//withdraw a bet
	//only legal if it has been overturned
	public void withdrawDeclaration(int player) {
		if(state != STATE_DEALING && state != STATE_BETTING) return;
		
		for(int i = 0; i < bets.size() - 1; i++) {
			if(bets.get(i).player == player) {
				betCountDown = 0;
				bets.remove(i);
				return;
			}
		}
	}
	
	public void defendDeclaration(int player, int amount) {
		if(state != STATE_DEALING && state != STATE_BETTING) return;
		
		//look for the previous declaration
		Bet bet = null;
		int betIndex = -1;
		for(int i = 0; i < bets.size() - 1; i++) {
			if(bets.get(i).player == player) {
				bet = bets.get(i);
				betIndex = i;
				break;
			}
		}
		
		if(bet == null) return;
		
		if(amount >= bets.get(bets.size() - 1).getAmount() && Card.countCards(new Card(currentLevel, bet.suit), players.get(player).getHand()) >= amount) {
			//this is acceptable, delete all bets after the found one
			betCountDown = 0;
			bet.amount = amount;
			
			while(bets.size() > betIndex) {
				bets.remove(betIndex + 1);
			}
		}
	}
	
	//cards is a list of unique cards
	//amount is the number of each unique card played
	public boolean playTrick(int player, List<Card> cards, List<Integer> amounts) {
		if(cards.isEmpty() || amounts.size() != cards.size()) return false;
		
		if(player == startingPlayer && player == nextPlayer && plays.size() == 0) {
			//first, amounts of each card must be equal
			int amount = amounts.get(0);
			for(Integer x : amounts) {
				if(x != amount) return false;
			}
			
			//suit must be same
			//player must also have enough of each card
			//use game suit so that trump are considered one suit
			int suit = cards.get(0).gameSuit;
			
			for(Card card : cards) {
				if(card.gameSuit != suit) return false;
				//search player's hand
				else if(players.get(player).countCards(card) < amount) return false;
			}
			
			//if there's multiple cards, they must be consecutive
			Collections.sort(cards, new CardSuitWeightComparator(trumpSuit, currentLevel)); //sort by value
			
			for(int i = 0; i < cards.size() - 1; i++) {
				if(cards.get(i).value != cards.get(i + 1).value - 1) {
					return false;
				}
			}
			
			//seems alright
			//remove player's cards
			players.get(player).removeCards(cards, amounts);
			
			//update information for this trick
			trickCards = amount * cards.size();
			openingPlay = new Trick(cards, amounts);
			
			return true;
		} else if(player == nextPlayer) {
			int trickSuit = openingPlay.getCards().get(0).gameSuit;
			int trickAmount = openingPlay.getAmounts().get(0);
			
			//match previous play
			//same number of cards
			int totalCards = 0;
			for(Integer x : amounts) {
				totalCards += x;
			}
			
			if(totalCards != trickCards) return false;
			
			//match suit if possible
			int suitTotal = players.get(player).countSuit(trickSuit);
			
			int totalSuitCards = 0;
			for(int i = 0; i < cards.size(); i++) {
				if(cards.get(i).gameSuit == trickSuit) {
					totalSuitCards+= amounts.get(i);
				}
			}
			
			if(totalSuitCards != suitTotal && totalSuitCards != trickCards) return false;
			
			if(trickAmount > 1) {
				//follow doubles, triples, etc. if possible
				int numSingle = tricksFound();
			}
			
			//remove player's cards
			players.get(player).removeCards(cards, amounts);
			
			//add to plays for this trick
			plays.add(new Trick(cards, amounts));
			
			//calculate the next player
			nextPlayer = (nextPlayer + 1) % players.size();
			return true;
		} else {
			return false;
		}
	}
	
	//returns milliseconds, maximum time to wait until next update
	public int update() {
		if(state == STATE_INIT) {
			//create bottom
			for(int i = 0; i < 6; i++) {
				bottom.add(deck.remove(0));
			}
			
			state = STATE_DEALING;
			return 1000;
		} else if(state == STATE_DEALING) {
			if(deck.size() == 0) {
				state = STATE_BETTING;
				return 1000;
			} else {
				//deal card to the next player
				lastPlayerDealt = (lastPlayerDealt + 1) % players.size();
				Player player = players.get(lastPlayerDealt);
				Card card = deck.remove(0);
				player.addCard(card);
				
				return 500;
			}
		} else if(state == STATE_BETTING) {
			if((!bets.isEmpty() && betCountDown >= 20) || (bets.size() == 1 && bets.get(0).amount == numDecks)) {
				trumpSuit = bets.get(bets.size() - 1).suit;
				
				for(Player player : players) {
					player.calculateGameSuit(trumpSuit, currentLevel);
				}
				
				state = STATE_PLAYING;
				plays = new ArrayList<Trick>();
			}
			else
				betCountDown++;
			
			return 500;
		} else if(state == STATE_PLAYING) {
			
		}
	}
	
	public int getCurrentLevel() {
		return currentLevel;
	}
}

class Bet {
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

class Trick {
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

//for this comparator, all cards must be the same suit
class CardSuitWeightComparator implements Comparator {
	int trumpSuit;
	int trumpValue;
	
	public CardSuitWeightComparator(int trumpSuit, int trumpValue) {
		this.trumpSuit = trumpSuit;
		this.trumpValue = trumpValue;
	}
	
	public int compare(Object o1, Object o2) {
		Card a = (Card) o1;
		Card b = (Card) o2;
		
		if(a.gameSuit != Card.SUIT_TRUMP) { 
			return a.value - b.value;
		} else {
			return a.getTrumpWeight(trumpSuit, trumpValue) - b.getTrumpWeight(trumpSuit, trumpValue);
		}
	}
}