package com.perennate.games.levelup;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import com.perennate.games.levelup.engine.Card;
import com.perennate.games.levelup.engine.Game;

public class GameClient extends Thread {
	public static int DEFAULT_PORT = 7553;
	
	public static int PACKET_HEADER = 146;
	public static int PACKET_JOIN = 0;
	public static int PACKET_JOINOTHER = 1;
	public static int PACKET_LEAVEOTHER = 2;
	public static int PACKET_GAMELOADED = 3;
	public static int PACKET_GAMESTATECHANGE = 4;
	public static int PACKET_DECLARE = 5;
	public static int PACKET_WITHDRAWDECLARATION = 6;
	public static int PACKET_DEFENDDECLARATION = 7;
	public static int PACKET_PLAYCARDS = 8;
	public static int PACKET_PLAYERROR = 9;
	public static int PACKET_DEALTCARD = 10;
	public static int PACKET_UPDATEBETCOUNTER = 11;
	
	Socket socket;
	DataInputStream in;
	DataOutputStream out;
	
	Game game;
	int pid;
	
	public GameClient() {
		game = new Game(4, false);
		pid = -1;
		
		connect();
	}
	
	public void connect() {
		InetAddress host = null;
		
		try {
			host = InetAddress.getLocalHost();
		} catch(UnknownHostException e) {
			LevelUp.println("[GameClient] Unable to get host address: " + e.getLocalizedMessage());
			return;
		}
		
		try {
			socket = new Socket(host, DEFAULT_PORT);
			
			in = new DataInputStream(socket.getInputStream());
			out = new DataOutputStream(socket.getOutputStream());
		} catch(IOException ioe) {
			LevelUp.println("[GameClient] Unable to connect to " + host.getHostAddress() + ": " + ioe.getLocalizedMessage());
		}
	}
	
	public void terminate(String reason) {
		LevelUp.println("[GameClient] Terminating connection: " + reason);
		
		try {
			socket.close();
		} catch(IOException ioe) {}
	}
	
	public void run() {
		//reason for termination
		String reason = "unknown";
		
		while(true) {
			try {
				int header = in.read();
				
				if(header == -1) {
					reason = "remote disconnected";
					break;
				} else if(header != PACKET_HEADER) {
					reason = "invalid header=" + header + " received from server";
					break;
				}
				
				int identifier = in.read();
				
				if(identifier == PACKET_JOIN) { //JOIN
					pid = in.readInt();
					
					if(pid == -1) {
						reason = "server rejected connection";
						break;
					}
				} else if(identifier == PACKET_JOINOTHER) {
					int otherPlayer = in.readInt();
					String name = in.readUTF();
					game.playerJoined(otherPlayer, name);
					
					LevelUp.println("[GameClient] Player [" + name + "] has joined the game");
				} else if(identifier == PACKET_LEAVEOTHER) {
					int otherPlayer = in.readInt();
					String name = game.getPlayer(otherPlayer).getName();
					game.playerLeft(otherPlayer);
					
					LevelUp.println("[GameClient] Player [" + name + "] has left the game");
				} else if(identifier == PACKET_GAMELOADED) {
					LevelUp.println("[GameClient] The game has begun.");
				} else if(identifier == PACKET_GAMESTATECHANGE) {
					int newState = in.readInt();
					game.setState(newState);
					
					LevelUp.println("[GameClient] Game state updated to: " + newState);
				} else if(identifier == PACKET_DECLARE) {
					int otherPlayer = in.readInt();
					int suit = in.readInt();
					int amount = in.readInt();
					
					game.declare(otherPlayer, suit, amount);
					
					String name = game.getPlayer(otherPlayer).getName();
					LevelUp.println("[GameClient] Player [" + name + "] has declared with " + amount + " of " + Card.getSuitString(suit));
				} else if(identifier == PACKET_WITHDRAWDECLARATION) {
					int otherPlayer = in.readInt();
					game.withdrawDeclaration(otherPlayer);
					
					String name = game.getPlayer(otherPlayer).getName();
					LevelUp.println("[GameClient] Player [" + name + "] has withdrawn");
				} else if(identifier == PACKET_DEFENDDECLARATION) {
					int otherPlayer = in.readInt();
					int amount = in.readInt();
					
					game.defendDeclaration(otherPlayer, amount);
					
					String name = game.getPlayer(otherPlayer).getName();
					LevelUp.println("[GameClient] Player [" + name + "] has defended with " + amount);
				} else if(identifier == PACKET_PLAYCARDS) {
					int otherPlayer = in.readInt();
					int numCards = in.readInt();
					
					List<Card> cards = new ArrayList<Card>(numCards);
					
					for(int i = 0; i < numCards; i++) {
						int suit = in.readInt();
						int value = in.readInt();
						cards.add(new Card(value, suit));
					}
					
					int numAmounts = in.readInt();
					List<Integer> amounts = new ArrayList<Integer>(numAmounts);
					
					for(int i = 0; i < numCards; i++) {
						amounts.add(in.readInt());
					}
					
					game.playTrick(otherPlayer, cards, amounts);
					
					String name = game.getPlayer(otherPlayer).getName();
					String print = "[GameClient] Player [" + name + "] has played a trick:";
					
					for(int i = 0; i < cards.size() && i < amounts.size(); i++) {
						print += " " + amounts.get(i) + " of " + cards.get(i);
					}
					
					LevelUp.println(print);
				} else if(identifier == PACKET_PLAYERROR) {
					String message = in.readUTF();
					LevelUp.println("[GameClient] Server says you made an invalid play: " + message);
				} else if(identifier == PACKET_DEALTCARD) {
					int suit = in.readInt();
					int value = in.readInt();
					Card card = new Card(value, suit);
					game.getPlayer(pid).addCard(card);
					
					LevelUp.println("[GameClient] You were dealt: " + card);
				} else if(identifier == PACKET_UPDATEBETCOUNTER) {
					int newCounter = in.readInt();
					game.setBetCounter(newCounter);
					
					LevelUp.println("[GameClient] Betting ends in " + newCounter);
				} else {
					reason = "unknown packet received from server, id=" + identifier;
					break;
				}
			} catch(IOException ioe) {
				reason = "error while reading: " + ioe.getLocalizedMessage();
				
				if(LevelUp.DEBUG) {
					ioe.printStackTrace();
				}
				
				break;
			}
		}
	
		//make sure connection is terminated
		terminate(reason);
	}
	
	public void sendJoin(String name) {
		synchronized(out) {
			try {
				out.write(PACKET_HEADER);
				out.write(PACKET_JOIN);
				out.writeUTF(name);
			} catch(IOException ioe) {
				terminate("failed to send packet");
			}
		}
	}
	
	public void sendDeclare(int suit, int amount) {
		synchronized(out) {
			try {
				out.write(PACKET_HEADER);
				out.write(PACKET_DECLARE);
				out.writeInt(suit);
				out.writeInt(amount);
			} catch(IOException ioe) {
				terminate("failed to send packet");
			}
		}
	}
	
	public void sendWithdrawDeclaration() {
		synchronized(out) {
			try {
				out.write(PACKET_HEADER);
				out.write(PACKET_WITHDRAWDECLARATION);
			} catch(IOException ioe) {
				terminate("failed to send packet");
			}
		}
	}
	
	public void sendDefendDeclaration(int amount) {
		synchronized(out) {
			try {
				out.write(PACKET_HEADER);
				out.write(PACKET_DEFENDDECLARATION);
				out.writeInt(amount);
			} catch(IOException ioe) {
				terminate("failed to send packet");
			}
		}
	}
	
	public void sendPlayCards(List<Card> cards, List<Integer> amounts) {
		synchronized(out) {
			try {
				out.write(PACKET_HEADER);
				out.write(PACKET_PLAYCARDS);
				out.writeInt(cards.size());
				
				for(Card card : cards) {
					out.writeInt(card.getSuit());
					out.writeInt(card.getValue());
				}
				
				out.writeInt(amounts.size());
				
				for(Integer x : amounts) {
					out.writeInt(x);
				}
			} catch(IOException ioe) {
				terminate("failed to send packet");
			}
		}
	}
}
