package com.perennate.games.levelup;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import com.perennate.games.levelup.engine.Card;
import com.perennate.games.levelup.engine.Game;

public class TerminalView extends View {
	boolean hasJoined;
	
	public TerminalView(Game game) {
		super(game);
		
		hasJoined = false;
	}
	
	public void eventGameUpdated() {
		if(game.getState() == Game.STATE_PLAYING) {
			if(pid == game.getNextPlayer()) {
				LevelUp.println("[View] It is now your turn.");
				LevelUp.println("[View] Your cards:" + game.getPlayer(pid).getHandString());
			} else {
				LevelUp.println("[View] It is Player " + game.getNextPlayer() + "'s turn.");
			}
		}
	}
	
	public void eventConnectError(String message) {
		
	}
	
	public void eventTerminateError(String message) {
		hasJoined = false;
	}
	
	public void eventPlayError(String message) {
		LevelUp.println("[View] Server says you made an invalid play: " + message);
	}
	
	public void eventJoined(boolean success) {
		if(success) {
			LevelUp.println("[View] You have joined the game.");
			hasJoined = true;
		} else {
			LevelUp.println("[View] The server rejected your connection.");
		}
	}
	
	public void eventGameLoaded() {
		LevelUp.println("[View] The game has begun.");
	}
	
	//for GamePlayerListener
	
	public void eventPlayerJoined(int pid, String name) {
		LevelUp.println("[View] Player [" + name + "] has joined the game in position " + pid);
	}
	
	public void eventPlayerLeft(int pid) {
		LevelUp.println("[View] Player " + pid + " has left the game");
	}
	
	public void eventGameStateChange(int newState) {
		LevelUp.println("[View] Game state updated to: " + newState);
		
		if(newState == Game.STATE_ROUNDOVER || newState == Game.STATE_GAMEOVER) {
			LevelUp.println("[View] The round is over!");
			LevelUp.println("[View] First team is on " + game.getPlayer(0).getLevel());
			LevelUp.println("[View] Second team is on " + game.getPlayer(1).getLevel());
			
			if(newState == Game.STATE_GAMEOVER) {
				LevelUp.println("[View] The game has ended!");
			}
		}
	}
	
	public void eventDeclare(int pid, int suit, int amount) {
		String name = game.getPlayer(pid).getName();
		LevelUp.println("[View] Player " + pid + " [" + name + "] has declared with " + amount + " of " + Card.getSuitString(suit));
	}
	
	public void eventWithdrawDeclaration(int pid) {
		String name = game.getPlayer(pid).getName();
		LevelUp.println("[View] Player " + pid + " [" + name + "] has withdrawn");
	}
	
	public void eventDefendDeclaration(int pid, int amount) {
		String name = game.getPlayer(pid).getName();
		LevelUp.println("[View] Player " + pid + " [" + name + "] has defended with " + amount);
	}
	
	public void eventPlayCards(int pid, List<Card> cards, List<Integer> amounts) {
		String name = game.getPlayer(pid).getName();
		String print = "[View] Player " + pid + " [" + name + "] has played a trick:";
		
		for(int i = 0; i < cards.size() && i < amounts.size(); i++) {
			print += " " + amounts.get(i) + " of " + cards.get(i) + ",";
		}
		
		LevelUp.println(print);
	}
	
	public void eventDealtCard(Card card) {
		LevelUp.println("[View] You were dealt: " + card);
		LevelUp.println("[View] Your cards:" + game.getPlayer(pid).getHandString());
	}
	
	public void eventUpdateBetCounter(int newCounter) {
		LevelUp.println("[View] Betting ends in " + newCounter);
	}
	
	public void eventBottom(List<Card> cards) {
		if(game.getState() == Game.STATE_BOTTOM && game.getCurrentDealer() == getPlayer()) {
			LevelUp.println("[View] You have been given the bottom:" + Card.toString(cards));
		}
	}
	
	public void eventSelectBottom(List<Card> cards) {
		LevelUp.println("[View] You have successfully selected the bottom");
	}
	
	public void eventUpdateRoundOverCounter(int newCounter) {
		LevelUp.println("[View] Next round starts in " + newCounter);
	}
	
	public void run() {
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
			
			String line;
			List<Card> selectedCards = new ArrayList<Card>();
			List<Integer> selectedAmounts = new ArrayList<Integer>();
			
			while((line = in.readLine()) != null) {
				String[] parts = line.split(" ");
				
				try {
					if(hasJoined) {
						if(parts[0].equals("declare")) {
							int suit = Card.getSuitInt(parts[1]);
							int amount = Integer.parseInt(parts[2]);
							client.sendDeclare(suit, amount);
						} else if(parts[0].equals("withdraw")) {
							client.sendWithdrawDeclaration();
						} else if(parts[0].equals("defend")) {
							int amount = Integer.parseInt(parts[1]);
							client.sendDefendDeclaration(amount);
						} else if(parts[0].equals("select")) {
							int suit = Card.getSuitInt(parts[1]);
							int value = Card.getValueInt(parts[2]);
							int amount = Integer.parseInt(parts[3]);
							selectedCards.add(new Card(suit, value));
							selectedAmounts.add(amount);
						} else if(parts[0].equals("qselect")) {
							System.out.println("you have selected:");
							for(int i = 0; i < selectedCards.size(); i++) {
								System.out.println("\t" + selectedAmounts.get(i) + " " + selectedCards.get(i));
							}
						} else if(parts[0].equals("clear")) {
							selectedCards.clear();
							selectedAmounts.clear();
						} else if(parts[0].equals("play")) {
							client.sendPlayCards(selectedCards, selectedAmounts);
							selectedCards.clear();
							selectedAmounts.clear();
						} else if(parts[0].equals("bottom")) {
							List<Card> bottom = new ArrayList<Card>();
							
							for(int i = 0; i < selectedCards.size(); i++) {
								for(int j = 0; j < selectedAmounts.get(i); j++) {
									bottom.add(selectedCards.get(i));
								}
							}
							
							client.sendSelectBottom(bottom);
							selectedCards.clear();
							selectedAmounts.clear();
						}
					} else {
						if(parts[0].equals("join")) {
							String username = parts[1];
							
							String hostname = "";
							if(parts.length >= 3) hostname = parts[2];
							
							client.connect(hostname);
							client.sendJoin(username);
						}
					}
				} catch(Exception e) {
					e.printStackTrace();
				}
			}
		} catch(IOException ioe) {
			ioe.printStackTrace();
		}
	}
}
