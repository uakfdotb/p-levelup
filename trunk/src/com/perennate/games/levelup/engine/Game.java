package com.perennate.games.levelup.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.perennate.games.levelup.LevelUp;

public class Game {
	public static int STATE_INIT = 0;
	public static int STATE_DEALING = 1;
	public static int STATE_BETTING = 2;
	public static int STATE_PLAYING = 3;
	public static int STATE_ROUNDOVER = 4;
	public static int STATE_GAMEOVER = 5;
	
	boolean controller; //whether or not this instance is the server
	List<GamePlayerListener> listeners;
	
	int numPlayers;
	int numDecks;
	
	int state;
	List<Card> deck;
	List<Card> bottom;
	List<Player> players;
	
	//current game fields
	int currentLevel;
	int currentDealer; //dealer will get the bottom
	boolean firstRound;
	
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
	
	//roundover fields
	int roundOverCounter;
	
	public Game(int numPlayers, boolean controller) {
		this.numPlayers = numPlayers;
		this.controller = controller;
		numDecks = numPlayers / 2;
		
		players = new ArrayList<Player>();
		for(int i = 0; i < numPlayers; i++) {
			players.add(new Player(this));
		}
		
		listeners = new ArrayList<GamePlayerListener>();
		
		firstRound = true;
		currentDealer = 0;
		setState(STATE_INIT);
	}
	
	public void init() {
		deck = Card.getCards(numDecks);
		bottom = new ArrayList<Card>();
		bets = new ArrayList<Bet>();
		plays = new ArrayList<Trick>();
		
		for(Player player : players) {
			player.init();
		}
		
		currentLevel = players.get(currentDealer).getLevel();
		
		betCountDown = 0;
		roundOverCounter = 0;
	}
	
	public void println(String message) {
		LevelUp.println("[Game] " + message);
	}
	
	public void setState(int newState) {
		state = newState;
		
		if(state == STATE_INIT) {
			init();
		} else if(state == STATE_PLAYING) {
			Bet winningBet = bets.get(bets.size() - 1);
			
			//set the trump suit
			trumpSuit = winningBet.suit;
			
			//fix the hands of the players to be in correct order with new trumps
			for(Player player : players) {
				player.calculateGameSuit(trumpSuit, currentLevel);
			}
			
			//set the current dealer for the first round
			if(firstRound) {
				currentDealer = winningBet.player;
			}
			
			//figure out player teams if needed (depends on game mode)
			//to do this, we see if the first player is on the same team as the dealer
			// and then update everyone accordingly
			if(firstRound) {
				boolean firstPlayerDefending = currentDealer % 2 == 0;
				
				for(int i = 0; i < players.size(); i++) {
					if(i % 2 == 0) players.get(i).defending = firstPlayerDefending;
					else players.get(i).defending = !firstPlayerDefending;
				}
			}
			
			//set first player of first round
			nextPlayer = currentDealer;
			startingPlayer = nextPlayer;
		}
		
		for(GamePlayerListener listener : listeners) {
			listener.eventGameStateChange(state);
		}
	}
	
	public void addListener(GamePlayerListener listener) {
		listeners.add(listener);
	}
	
	public void removeListener(GamePlayerListener listener) {
		listeners.remove(listener);
	}
	
	public boolean playerJoined(int id, String name) {
		if(id >= 0 && id < players.size() && players.get(id).name == null) {
			players.get(id).name = name;
			
			for(GamePlayerListener listener : listeners) {
				listener.eventPlayerJoined(id, name);
			}
			
			return true;
		} else {
			return false;
		}
	}
	
	public void playerLeft(int id) {
		if(id >= 0 && id < players.size()) {
			players.get(id).name = null;
			
			for(GamePlayerListener listener : listeners) {
				listener.eventPlayerLeft(id);
			}
		}
	}
	
	//make a card declaration (bet)
	//returns false if the declaration is illegal
	public boolean declare(int player, int suit, int amount) {
		if(state != STATE_DEALING && state != STATE_BETTING) return false;
		
		LevelUp.println("[Game] Player " + player + " is attempting to declare.");
		
		if((bets.isEmpty() || amount > bets.get(bets.size() - 1).getAmount())
				&& (!controller || players.get(player).countCards(new Card(suit, currentLevel)) >= amount)) {
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
						LevelUp.debug("[Game] Declare failed; attempted to revise existing bet.");
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
						LevelUp.debug("[Game] Declare failed: attempted to overturn same suit.");
						return false;
					}
				}
			}
			
			//add a new bet onto the arraylist
			//this can be defended against if the first player has more of the same card
			betCountDown = 0;
			bets.add(new Bet(player, suit, amount));
			
			for(GamePlayerListener listener : listeners) {
				listener.eventDeclare(player, suit, amount);
			}
			
			return true;
		} else {
			LevelUp.debug("[Game] Declare failed because it doesn't beat previous bet or player does not have the cards.");
			return false;
		}
	}
	
	//withdraw a bet
	//only legal if it has been overturned
	public boolean withdrawDeclaration(int player) {
		if(state != STATE_DEALING && state != STATE_BETTING) return false;
		
		//use bets.size() - 1 because we cannot withdraw the last declaration
		for(int i = 0; i < bets.size() - 1; i++) {
			if(bets.get(i).player == player) {
				betCountDown = 0;
				bets.remove(i);
				
				for(GamePlayerListener listener : listeners) {
					listener.eventWithdrawDeclaration(player);
				}
				
				return true;
			}
		}
		
		return false;
	}
	
	public boolean defendDeclaration(int player, int amount) {
		if(state != STATE_DEALING && state != STATE_BETTING) return false;
		
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
		
		if(bet == null) return false;
		
		//make sure that the current amount is greater or equal to the previous bet
		// and that the player has enough cards (we can only check the latter if we are controller)
		if(amount >= bets.get(bets.size() - 1).getAmount() &&
				(!controller || players.get(player).countCards(new Card(bet.suit, currentLevel)) >= amount)) {
			//this is acceptable, delete all bets after the found one
			betCountDown = 0;
			bet.amount = amount;
			
			while(bets.size() > betIndex) {
				bets.remove(betIndex + 1);
			}
			
			for(GamePlayerListener listener : listeners) {
				listener.eventDefendDeclaration(player, amount);
			}
			
			return true;
		} else {
			return false;
		}
	}
	
	//cards is a list of unique cards
	//amount is the number of each unique card played
	public boolean playTrick(int player, Trick trick) {
		println("Player " + player + " is attempting to play a trick.");
		
		List<Card> cards = trick.getCards();
		List<Integer> amounts = trick.getAmounts();
		
		if(cards.isEmpty() || amounts.size() != cards.size()) {
			return false;
		}
		
		if(player == startingPlayer && player == nextPlayer && plays.size() == 0) {
			//first, amounts of each card must be equal
			int amount = amounts.get(0);
			for(Integer x : amounts) {
				if(x != amount) {
					println("Played card amounts are not equal.");
					return false;
				}
			}
			
			//suit must be same
			//player must also have enough of each card
			//use game suit so that trump are considered one suit
			int suit = cards.get(0).gameSuit;
			
			for(Card card : cards) {
				if(card.gameSuit != suit) {
					println("Played cards are of different suits.");
					return false;
				}
				
				//search player's hand
				else if(controller && players.get(player).countCards(card) < amount) {
					println("Played cards are not in player's hand.");
					return false;
				}
			}
			
			//if there's multiple cards, they must be consecutive
			Collections.sort(cards, new CardSuitWeightComparator(trumpSuit, currentLevel)); //sort by value
			
			for(int i = 0; i < cards.size() - 1; i++) {
				if(cards.get(i).gameValue != cards.get(i + 1).gameValue - 1) {
					println("Played cards are not consecutive.");
					return false;
				}
			}
			
			//seems alright
			//remove player's cards
			players.get(player).removeCards(cards, amounts);
			
			//add to plays for this trick
			plays.add(new Trick(cards, amounts));
			
			//update information for this trick
			trickCards = amount * cards.size();
			openingPlay = new Trick(cards, amounts);

			for(GamePlayerListener listener : listeners) {
				listener.eventPlayCards(player, cards, amounts);
			}
			
			//calculate the next player
			nextPlayer = (nextPlayer + 1) % players.size();
			
			return true;
		} else if(player == nextPlayer) {
			if(controller) {
				int trickSuit = openingPlay.getCards().get(0).gameSuit;
				int trickAmount = openingPlay.getAmounts().get(0);
				
				//make sure player has the cards
				if(controller) {
					for(int i = 0; i < cards.size(); i++) {
						if(players.get(player).countCards(cards.get(i)) < amounts.get(i)) {
							println("Played cards are not in player's hand.");
							return false;
						}
					}
				}
				
				//match previous play
				//same number of cards
				int totalCards = 0;
				for(Integer x : amounts) {
					totalCards += x;
				}
				
				if(totalCards != trickCards) {
					println("Number of played cards does not equal opening trick size.");
					return false;
				}
				
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
				if(totalSuitCards != suitTotal && totalSuitCards != trickCards) {
					println("Played cards do not follow suit.");
					return false;
				}
				
				//follow doubles and triples if possible
				// this only applies if the player has not exhausted the suit
				if(trickAmount > 1 && totalSuitCards == trickCards) {
					//first make sure player isn't avoiding playing the enter combination
					if(players.get(player).searchTrick(trickSuit, openingPlay.getAmounts())) {
						if(!amounts.equals(openingPlay.getAmounts())) {
							println("Played cards avoids playing combination.");
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
								
								if(!foundReplacement) {
									println("Player did not play replacement for the cards appropriately.");
									return false;
								}
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
										println("Played cards do not follow tuples.");
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
			
			//check if this play is over, or maybe even the round
			boolean roundOver = false;
			
			if(plays.size() == numPlayers) {
				int winningPlayer = compareField();
				
				//give the winner points
				players.get(winningPlayer).points += fieldPoints();
				
				plays.clear();
				LevelUp.println("hi: " + plays.size());
				startingPlayer = winningPlayer;
				nextPlayer = winningPlayer;
				
				//determine if the round is over (all player's hands are empty)
				//we have to loop through each player because this might not be
				// a controller instance, and so we might not know all of the cards.
				roundOver = true;
				
				for(Player it_player : players) {
					if(!it_player.getHand().isEmpty()) roundOver = false;
				}
				
				if(roundOver) {
					//this round is over
					//first give winner double the points on bottom
					players.get(winningPlayer).points += bottomPoints() * 2;
					
					//if we are the controller, we have to make sure that
					// all clients have the bottom cards before the above
					// code is executed so that everyone remains in sync.
					//so we call all our listeners here
					for(GamePlayerListener listener : listeners) {
						listener.eventBottom(bottom);
					}
					
					//we delay calling roundOver until later because otherwise
					// clients won't receive the cards for the last trick
					// until after the state is changed away from playing.
				}
			}
			
			//make sure to update listeners last because we
			// might have had to notify them about the bottom
			// before this
			for(GamePlayerListener listener : listeners) {
				listener.eventPlayCards(player, cards, amounts);
			}
			
			//call roundOver if needed here so that cards
			// are updated first
			if(roundOver) {
				roundOver();
			}
			
			return true;
		} else {
			println("It's not the player's turn!");
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
			int twoSuit = two.getCards().get(0).gameSuit;
			
			if(oneSuit != twoSuit && twoSuit != Card.SUIT_TRUMP) {
				LevelUp.debug("[Game] compareTrick: true because two's cards do not match one's");
				return true;
			}
			
			//make sure two is all of the same suit
			for(int i = 0; i < two.getCards().size(); i++) {
				if(twoSuit != two.getCards().get(i).gameSuit) {
					LevelUp.debug("[Game] compareTrick: true because two's cards are split across suits,");
					return true;
				}
			}
			
			//at this point first hand is either same suit as second or second is all trump
			//confirm that a the second trick is in sequential order
			if(two.getAmounts().size() > 1) {
				for(int i = 0; i < two.getAmounts().size() - 1; i++) {
					if(two.getCards().get(i).gameValue != two.getCards().get(i + 1).gameValue - 1) {
						LevelUp.debug("[Game] compareTrick: true because two is not in sequence");
						return true;
					}
				}
			}
			
			if(twoSuit == Card.SUIT_TRUMP && oneSuit != Card.SUIT_TRUMP) {
				//if in sequence, and amount matches, then the basic suit would be trumped regardless of card value
				LevelUp.debug("[Game] compareTrick: false because two trumps one");
				return false;
			} else {
				for(int i = 0; i < one.getCards().size(); i++) {
					//if trump, compare trump weight
					//otherwise compare value
					// gameValue does what we want
					if(two.getCards().get(i).gameValue <= one.getCards().get(i).gameValue) {
						LevelUp.debug("[Game] compareTrick: true because two's cards do not beat one's");
						return true;
					}
				}
				
				//if all line up cards are greater than the first tricks, the second hand wins
				LevelUp.debug("[Game] compareTrick: false because two beats one");
				return false;
			}
		} else {
			LevelUp.debug("[Game] compareTrick: true because amounts inequal");
			return true;
		}
	}
	
	//returns the id of the winning player for a set of plays
	public int compareField() {
		int top_play = 0;
		
		for(int i = 1; i < plays.size(); i++) {
			if(!(compareTrick(plays.get(top_play), plays.get(i)))) {
				top_play = i;
			}
		}
		
		return (top_play + startingPlayer) % players.size();
	}
	
	//calculates the number of points on the field
	public int fieldPoints() {
		int points = 0;
		
		for(Trick trick : plays) {
			for(int i = 0; i < trick.getCards().size(); i++) {
				points += trick.getCards().get(i).getPoints() * trick.getAmounts().get(i);
			}
		}
		
		return points;
	}
	
	//calculates the number of points in the bottom
	public int bottomPoints() {
		int points = 0;
		
		for(Card card : bottom) {
			points += card.getPoints();
		}
		
		return points;
	}
	
	public void roundOver() {
		//count total points on attacking side
		int attackingPoints = 0;
		for(Player player : players) {
			if(!player.defending) {
				attackingPoints += player.points;
			}
		}
		
		//negative is towards defending, positive is towards attacking, >=0 switches sides
		int winner;
		
		if(attackingPoints == 0) {
			winner = -3;
		} else {
			winner = attackingPoints / (numDecks * 20) - 2;
		}
		
		LevelUp.debug("[Game] Winner is " + winner + " (attacking got " + attackingPoints + " points)");
		
		//level up the appropriate amount
		//current level for the next round will be set in init()
		if(winner >= 0) {
			// also switch teams
			for(Player player : players) {
				if(!player.defending) {
					player.levelUp(winner);
				}
				
				player.defending = !player.defending; //switch teams
			}
			
			//update dealer
			currentDealer = (currentDealer + 1) % players.size();
		} else {
			for(Player player : players) {
				if(player.defending) {
					player.levelUp(-winner);
				}
			}
			
			//update dealer
			currentDealer = (currentDealer + 2) % players.size();
		}
		
		//only update the state if we are the controller
		//otherwise we'll update the state once when we do it here
		// and again when notified by server
		if(controller) {
			if(!gameOver()) {
				setState(STATE_ROUNDOVER);
			} else {
				setState(STATE_GAMEOVER);
			}
		}
		
		//no matter what, next round is not the first round anymore, so update
		firstRound = false;
		
		//start the round over counter at zero
		roundOverCounter = 0;
	}
	
	public boolean gameOver() {
		for(Player player : players) {
			if(player.level == 16) return true;
		}
		
		return false;
	}
	
	//returns milliseconds, maximum time to wait until next update
	// or -1 to destroy this Game (if game is over or on error).
	//should only be called for controller Game instance
	public int update() {
		if(state == STATE_INIT) {
			//check if we have all the players
			for(int i = 0; i < players.size(); i++) {
				if(players.get(i).name == null) {
					//still waiting on more players
					return 1000;
				}
			}
			
			//we have enough players
			
			//calculate the number of cards to put on bottom
			//currently, to do this, we start be calculating the number
			// of extra cards that will be remaining after splitting
			// the deck among all of the players during dealing.
			//then, we add the number of players until we arrive at
			// a number just strictly less than ten.
			//there can't be zero in the bottom though
			int numBottomCards = deck.size() % players.size();
			
			while(numBottomCards + players.size() < 10) {
				numBottomCards += players.size();
			}
			
			if(numBottomCards == 0) numBottomCards = players.size();
			
			LevelUp.debug("[Game] Putting " + numBottomCards + " on the bottom for " + players.size() + " players and " + deck.size() + " cards");
			
			//create the bottom
			for(int i = 0; i < numBottomCards; i++) {
				bottom.add(deck.remove(0));
			}
			
			lastPlayerDealt = (currentDealer - 1) % players.size();
			setState(STATE_DEALING);
			return 1000;
		} else if(state == STATE_DEALING) {
			if(deck.size() == 0) {
				setState(STATE_BETTING);
				return 1000;
			} else {
				//deal card to the next player
				lastPlayerDealt = (lastPlayerDealt + 1) % players.size();
				Player player = players.get(lastPlayerDealt);
				Card card = deck.remove(0);
				player.addCard(card);
				
				for(GamePlayerListener listener : listeners) {
					if(listener.getPlayer() == lastPlayerDealt) {
						listener.eventDealtCard(card);
					}
				}
				
				return 100;
			}
		} else if(state == STATE_BETTING) {
			if((!bets.isEmpty() && betCountDown >= 20) || (bets.size() == 1 && bets.get(0).amount == numDecks)) {
				setState(STATE_PLAYING);
			} else {
				betCountDown++;
				
				for(GamePlayerListener listener : listeners) {
					listener.eventUpdateBetCounter(betCountDown);
				}
			}
			
			return 500;
		} else if(state == STATE_PLAYING) {
			return 2000;
		} else if(state == STATE_ROUNDOVER || state == STATE_GAMEOVER) {
			roundOverCounter++;
			
			if(roundOverCounter >= 30) {
				setState(STATE_INIT);
				return 1000;
			} else {
				for(GamePlayerListener listener : listeners) {
					listener.eventUpdateRoundOverCounter(roundOverCounter);
				}
				
				return 500;
			}
		} else {
			//error!
			System.out.println("[GAME] Unknown state: " + state);
			return -1;
		}
	}
	
	public int getCurrentLevel() {
		return currentLevel;
	}
	
	public Player getPlayer(int i) {
		return players.get(i);
	}
	
	public void setBetCounter(int newCounter) {
		betCountDown = newCounter;
	}
	
	public void setRoundOverCounter(int newCounter) {
		roundOverCounter = newCounter;
	}
	
	public int getNextPlayer() {
		return nextPlayer;
	}
	
	public int getStartingPlayer() {
		return startingPlayer;
	}
	
	public int getState() {
		return state;
	}
	
	public Card constructCard(int suit, int value) {
		Card card = new Card(suit, value);
		card.calculateGameSuit(trumpSuit, currentLevel);
		return card;
	}
	
	public void setBottom(List<Card> bottom) {
		this.bottom = bottom;
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
