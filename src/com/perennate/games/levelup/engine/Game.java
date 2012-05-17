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
	
	boolean controller; //whether or not this instance is the server
	
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
	
	public Game(int numPlayers, boolean controller) {
		this.numPlayers = numPlayers;
		this.controller = controller;
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
		
		if(amount > bets.get(bets.size() - 1).getAmount() && (!controller || players.get(player).countCards(new Card(currentLevel, suit)) >= amount)) {
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
		
		if(amount >= bets.get(bets.size() - 1).getAmount() && (!controller || players.get(player).countCards(new Card(currentLevel, bet.suit)) >= amount)) {
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
				else if(!controller || players.get(player).countCards(card) < amount) return false;
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
			if(controller) {
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
						totalSuitCards += amounts.get(i);
					}
				}
				
				//if total cards in suit that player played is not
				// equal to the total cards in suit or the total trick
				// cards, then this in an invalid play
				if(totalSuitCards != suitTotal && totalSuitCards != trickCards) return false;
				
				//follow doubles and triples if possible
				// this only applies if the player has not exhausted the suit
				if(trickAmount > 1 && totalSuitCards == trickCards) {
					//first make sure player isn't avoiding playing the enter combination
					if(players.get(player).searchTrick(trickSuit, openingPlay.getAmounts())) {
						if(!amounts.equals(openingPlay.getAmounts())) {
							return false;
						} else {
							//order the cards because player has played entire combination so might beat it
							Collections.sort(cards, new CardSuitWeightComparator(trumpSuit, currentLevel)); //sort by value
						}
					} else {
						//if not combination, make sure player plays as many individual parts as possible
						
						//find all the tuples
						List<CardTuple> tuples = players.get(player).getTuples(trickSuit);
						
						//now find tuples that the player has played
						// because player doesn't have to play entire tuple, we must
						// also keep track of the number of cards within the tuple played
						List<CardTuple> tuplesPlayed = new ArrayList<CardTuple>();
						List<Integer> numCardsPlayed = new ArrayList<Integer>();
						for(int i = 0; i < amounts.size(); i++) {
							if(amounts.get(i) >= 2) {
								numCardsPlayed.add(amounts.get(i));
								
								//find corresponding card tuple
								for(CardTuple tuple : tuples) {
									if(tuple.getCard().equals(cards.get(i))) {
										tuplesPlayed.add(tuple);
										break;
									}
								}
							}
						}
						
						//now loop through each amount (which should all be the same)
						// and make sure player didn't avoid something needed to play
						for(int i = 0; i < openingPlay.getAmounts().size(); i++) {
							int currAmount = openingPlay.getAmounts().get(i);
							
							//see if player has exact number
							boolean hasExact = false;
							for(CardTuple tuple : tuples) {
								if(tuple.getAmount() == currAmount) {
									hasExact = true;
									break;
								}
							}
							
							if(hasExact) {
								//player does have the exact number of cards
								// now just make sure he played it or an appropriate replacement
								boolean foundReplacement = false;
								for(int j = 0; j < numCardsPlayed.size(); j++) {
									if(numCardsPlayed.get(j) >= currAmount) {
										//player did play replacement
										
										//first update both the hand and played tuple
										CardTuple tuple = tuplesPlayed.get(j);
										tuple.amount -= currAmount;
										numCardsPlayed.set(j, numCardsPlayed.get(j) - currAmount);
										
										//remove from hand tuples if needed
										if(tuple.amount <= 1) {
											//remove this tuple from hand tuples list because it's no longer a tuple
											tuples.remove(tuple);
										}
										
										//remove from played tuples if needed
										if(numCardsPlayed.get(j) <= 1) {
											//in this case, the played tuple is now either a single or gone completely
											// so remove from the played list
											numCardsPlayed.remove(j);
											tuplesPlayed.remove(j);
										}
										
										foundReplacement = true;
										break;
									}
								}
								
								if(!foundReplacement) return false;
							} else {
								//player doesn't have exact number
								// then just make sure that player has played any lower-order tuples
								int remaining = currAmount;
								
								while(remaining >= 2) {
									if(!tuplesPlayed.isEmpty()) {
										//calculate the amount played for this round
										int numPlayed = numCardsPlayed.get(0);
										if(numPlayed > remaining) {
											numPlayed = remaining;
										}
										
										remaining -= numPlayed;
										
										//first update both the hand and played tuple
										CardTuple tuple = tuplesPlayed.get(0);
										tuple.amount -= numPlayed;
										numCardsPlayed.set(0, numCardsPlayed.get(0) - numPlayed);
										
										//remove from hand tuples if needed
										if(tuple.amount <= 1) {
											//remove this tuple from hand tuples list because it's no longer a tuple
											tuples.remove(tuple);
										}
										
										//remove from played tuples if needed
										if(numCardsPlayed.get(0) <= 1) {
											//remove this tuple from played tuples because it's a single or gone completely
											numCardsPlayed.remove(0);
											tuplesPlayed.remove(0);
										}
									} else if(!tuples.isEmpty()) {
										//player has tuples remaining but didn't play them
										return false;
									} else {
										//no more tuples, so we're done here
										break;
									}
								}
							}
						}
					}
				}
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
	
	//this will compare two Tricks and return the better of the two.
	//The trick put in first is given priority meaning if they are equal, then the first trick will win. 
	//If amount isn't the same, then the first trick wins.
	//True means first trick is better
	public boolean compareTrick(Trick one, Trick two) {
		if(one.getAmounts().equals(two.getAmounts())) {
			//check that all the suits in two match
			int oneSuit = one.getCards().get(0).gameSuit;
			boolean trumpCheck = false;
			for(int i=0; i < two.getCards().size(); i++) {
				if(two.getCards().get(i).gameSuit != oneSuit && trumpCheck == false) {
					if(trumpCheck == false && two.getCards().get(i).gameSuit == Card.SUIT_TRUMP){
						trumpCheck = true;
					} else if (two.getCards().get(i).gameSuit != Card.SUIT_TRUMP && trumpCheck == true) {
						return true;
					}
				} else {
					return true;
				}
			}
			
			//at this point first hand is either same suit as second or second is all trump
			//confirm that a the second trick is in sequential order
			if(two.getAmounts().size()>1) {
				int j=0;
				for(int i=0; i<two.getAmounts().size()-1; i++) {
					if(two.getCards().get(j).value == ( two.getCards.get( j+two.getAmounts().get(i) ).value - 1 )) {
						j+=two.getAmounts().get(i);
					} else {
						//second hand is not a sequence
						return true;
					}
				}
			}
			
			if(trumpCheck){
				//if in sequence, and amount matchs, then the basic suit would be trumped regardless of card value
				return false;
			} else {
				for(int i=0; i<one.getCards().size(); i++) {
					if(two.getCards().get(i).value <= one.getCards().get(i).value) {
						return true;
					}
				}
				//if all line up cards are greater than the first tricks, the second hand wins
				return false;
			}
		} else {
			return true;
		}
	}
	
	//returns the id of the winning player for a set of plays
	public int compareField() {
		int top_play = 0;
		for(int i=1; i<plays.size(); i++) {
			if(!(compareTrick( plays(top_play), plays(i) ))) {
				top_play=i;
			}
		}
		return (top_play+startingPlayer) % players.size();
	}
	
	//returns milliseconds, maximum time to wait until next update
	// only called for controller Game instance
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
class CardSuitWeightComparator implements Comparator<Card> {
	int trumpSuit;
	int trumpValue;
	
	public CardSuitWeightComparator(int trumpSuit, int trumpValue) {
		this.trumpSuit = trumpSuit;
		this.trumpValue = trumpValue;
	}
	
	public int compare(Card a, Card b) {
		if(a.gameSuit != Card.SUIT_TRUMP) { 
			return a.value - b.value;
		} else {
			return a.getTrumpWeight(trumpSuit, trumpValue) - b.getTrumpWeight(trumpSuit, trumpValue);
		}
	}
}
