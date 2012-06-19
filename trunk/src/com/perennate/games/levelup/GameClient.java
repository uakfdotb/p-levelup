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
import com.perennate.games.levelup.engine.CardTuple;
import com.perennate.games.levelup.engine.Game;

public class GameClient implements Runnable {
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
	public static int PACKET_UPDATEROUNDOVERCOUNTER = 12;
	public static int PACKET_BOTTOM = 13;
	public static int PACKET_SELECTBOTTOM = 14;
	
	Socket socket;
	DataInputStream in;
	DataOutputStream out;
	boolean isConnected;
	boolean quit;
	
	Game game;
	View view;
	int pid;
	
	public GameClient(Game game, View view) {
		this.view = view;
		this.game = game;
		
		view.setClient(this);
		
		pid = -1;
		isConnected = false;
		quit = false;
	}
	
	public void connect(String hostname, int port) {
		InetAddress host = null;
		
		try {
			if(hostname.isEmpty()) {
				hostname = Config.getString("hostname", null);
				
				if(hostname == null) {
					host = InetAddress.getLocalHost();
				} else {
					host = InetAddress.getByName(hostname);
				}
			} else {
				host = InetAddress.getByName(hostname);
			}
		} catch(UnknownHostException e) {
			LevelUp.println("[GameClient] Unable to resolve host address: " + e.getLocalizedMessage());
			view.eventConnectError("Unable to resolve host address");
			return;
		}
		
		try {
			socket = new Socket(host, port);
			
			in = new DataInputStream(socket.getInputStream());
			out = new DataOutputStream(socket.getOutputStream());
			
			isConnected = true;
		} catch(IOException ioe) {
			LevelUp.println("[GameClient] Unable to connect to " + host.getHostAddress() + ": " + ioe.getLocalizedMessage());
			view.eventConnectError("Unable to connect to " + host.getHostAddress() + ": " + ioe.getLocalizedMessage());
		}
		
		new Thread(this).start();
	}
	
	public void terminate(String reason) {
		if(isConnected) {
			isConnected = false;
			
			LevelUp.println("[GameClient] Terminating connection: " + reason);
			view.eventTerminateError(reason);
			
			try {
				socket.close();
			} catch(IOException ioe) {}
		}
	}
	
	public void quit() {
		quit = true;
		terminate("quit called");
	}
	
	public void run() {
		//reason for termination
		String reason = "unknown";
		
		while(!quit) {
			//wait until we are connected to start
			while(!isConnected && !quit) {
				try {
					Thread.sleep(500);
				} catch(InterruptedException e) {}
			}
			
			while(!quit) {
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
						view.setPID(pid);
						
						if(pid == -1) {
							reason = "server rejected connection";
							view.eventJoined(false);
							break;
						}
						
						view.eventJoined(true);
					} else if(identifier == PACKET_JOINOTHER) {
						int otherPlayer = in.readInt();
						String name = in.readUTF();
	
						synchronized(game) {
							game.playerJoined(otherPlayer, name);
						}
					} else if(identifier == PACKET_LEAVEOTHER) {
						int otherPlayer = in.readInt();
						
						synchronized(game) {
							game.playerLeft(otherPlayer);
						}
					} else if(identifier == PACKET_GAMELOADED) {
						view.eventGameLoaded();
					} else if(identifier == PACKET_GAMESTATECHANGE) {
						int newState = in.readInt();
	
						synchronized(game) {
							game.setState(newState);
						}
					} else if(identifier == PACKET_DECLARE) {
						int otherPlayer = in.readInt();
						int suit = in.readInt();
						int amount = in.readInt();
						
						synchronized(game) {
							game.declare(otherPlayer, suit, amount);
						}
					} else if(identifier == PACKET_WITHDRAWDECLARATION) {
						int otherPlayer = in.readInt();
	
						synchronized(game) {
							game.withdrawDeclaration(otherPlayer);
						}
					} else if(identifier == PACKET_DEFENDDECLARATION) {
						int otherPlayer = in.readInt();
						int amount = in.readInt();
	
						synchronized(game) {
							game.defendDeclaration(otherPlayer, amount);
						}
					} else if(identifier == PACKET_PLAYCARDS) {
						int otherPlayer = in.readInt();
						int numCards = in.readInt();
						
						List<Card> cards = new ArrayList<Card>(numCards);
						
						for(int i = 0; i < numCards; i++) {
							int suit = in.readInt();
							int value = in.readInt();
							cards.add(game.constructCard(suit, value));
						}
						
						int numAmounts = in.readInt();
						List<Integer> amounts = new ArrayList<Integer>(numAmounts);
						
						for(int i = 0; i < numCards; i++) {
							amounts.add(in.readInt());
						}
	
						synchronized(game) {
							game.playTrick(otherPlayer, CardTuple.createTrick(cards, amounts));
						}
					} else if(identifier == PACKET_PLAYERROR) {
						String message = in.readUTF();
						view.eventPlayError(message);
					} else if(identifier == PACKET_DEALTCARD) {
						int suit = in.readInt();
						int value = in.readInt();
						Card card = game.constructCard(suit, value);
	
						synchronized(game) {
							game.getPlayer(pid).addCard(card);
						}
						
						view.eventDealtCard(card);
					} else if(identifier == PACKET_UPDATEBETCOUNTER) {
						int newCounter = in.readInt();
						
						synchronized(game) {
							game.setBetCounter(newCounter);
						}
						
						//game won't tell listeners unless it's controller, so tell view from here
						view.eventUpdateBetCounter(newCounter);
					} else if(identifier == PACKET_UPDATEROUNDOVERCOUNTER) {
						int newCounter = in.readInt();
						
						synchronized(game) {
							game.setRoundOverCounter(newCounter);
						}
						
						//game won't tell listeners unless it's controller, so tell view from here
						view.eventUpdateRoundOverCounter(newCounter);
					} else if(identifier == PACKET_BOTTOM) {
						int numCards = in.readInt();
						
						List<Card> cards = new ArrayList<Card>(numCards);
						
						for(int i = 0; i < numCards; i++) {
							int suit = in.readInt();
							int value = in.readInt();
							cards.add(game.constructCard(suit, value));
						}
						
						synchronized(game) {
							game.setBottom(cards);
						}
					} else if(identifier == PACKET_SELECTBOTTOM) {
						int numCards = in.readInt();
						
						List<Card> cards = new ArrayList<Card>(numCards);
						
						for(int i = 0; i < numCards; i++) {
							int suit = in.readInt();
							int value = in.readInt();
							cards.add(game.constructCard(suit, value));
						}
	
						synchronized(game) {
							game.selectBottom(pid, cards);
						}
					} else {
						reason = "unknown packet received from server, id=" + identifier;
						break;
					}
					
					//notify view that the game updated
					//this is outside of our synchronization statements
					view.eventGameUpdated();
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
	
	}
	
	public void sendJoin(String name) {
		if(!isConnected) return;
		
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
		if(!isConnected) return;
		
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
		if(!isConnected) return;
		
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
		if(!isConnected) return;
		
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
		if(!isConnected) return;
		
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
	
	public void sendSelectBottom(List<Card> cards) {
		if(!isConnected) return;
		
		synchronized(out) {
			try {
				out.write(PACKET_HEADER);
				out.write(PACKET_SELECTBOTTOM);
				out.writeInt(cards.size());
				
				for(Card card : cards) {
					out.writeInt(card.getSuit());
					out.writeInt(card.getValue());
				}
			} catch(IOException ioe) {
				terminate("failed to send packet");
			}
		}
	}
}
