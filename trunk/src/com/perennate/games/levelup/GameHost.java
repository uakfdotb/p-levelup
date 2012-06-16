package com.perennate.games.levelup;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import com.perennate.games.levelup.engine.Card;
import com.perennate.games.levelup.engine.Game;
import com.perennate.games.levelup.engine.GamePlayerListener;

public class GameHost extends Thread {
	public static int DEFAULT_PORT = 7553;
	
	ServerSocket server;
	ArrayList<GameConnection> connections;
	
	boolean gameLoaded;
	GameSlot[] slots;
	
	Game game;
	
	public GameHost() {
		game = new Game(4, true);
		connections = new ArrayList<GameConnection>();
		slots = new GameSlot[4];
		gameLoaded = false;
		
		for(int i = 0; i < slots.length; i++) {
			slots[i] = new GameSlot();
		}
		
		try {
			server = new ServerSocket(DEFAULT_PORT);
		} catch(IOException ioe) {
			LevelUp.println("[GameHost] Error while binding to " + DEFAULT_PORT + ": " + ioe.getLocalizedMessage());
			
			if(LevelUp.DEBUG) {
				ioe.printStackTrace();
			}
		}
	}
	
	public int eventPlayerJoin(GameConnection connection, String name) {
		if(gameLoaded) return -1;
		
		int pid = -1;
		
		synchronized(game) {
			for(int i = 0; i < slots.length; i++) {
				if(slots[i].connection == null) {
					slots[i].connection = connection;
					slots[i].name = name;
					pid = i;
					
					game.playerJoined(i, name);
					break;
				}
			}
			
			if(pid == -1) return -1;
			
			LevelUp.println("[GameHost] Player [" + name + "|" + connection.socket.getInetAddress().getHostName() + "] has joined the game");
			game.addListener(connection);
			
			//check if we're done loading; also use this to let the joining player know about the slots
			boolean done = true;
			for(int i = 0; i < slots.length; i++) {
				if(slots[i].connection == null) {
					done = false;
				} else {
					connection.eventPlayerJoined(i, slots[i].name);
				}
			}
			
			if(done) {
				gameLoaded = true;
				
				try {
					server.close();
				} catch(IOException ioe) {
					LevelUp.println("[GameHost] Error while closing server socket; game may not update");
				}
				
				for(GameConnection i : connections) {
					i.sendGameLoaded();
				}
			}
		}
		
		return pid;
	}
	
	public void eventPlayerLeave(GameConnection connection, int pid) {
		if(pid >= 0 && pid < slots.length) {
			synchronized(game) {
				LevelUp.println("[GameHost] Player [" + slots[pid].name + "] has left the game");
				
				slots[pid].connection = null;
				slots[pid].name = null;
				
				game.playerLeft(pid);
				game.removeListener(connection);
			}
		}
	}
	
	public void eventPlayerTerminate(GameConnection connection) {
		LevelUp.println("[GameHost] Connection from " + connection.socket.getInetAddress().getHostAddress() + " is terminated");
		connections.remove(connection);
	}
	
	public void run() {
		while(!gameLoaded) {
			try {
				Socket socket = server.accept();
				LevelUp.println("[GameHost] New connection from " + socket.getInetAddress().getHostAddress());
				
				GameConnection connection = new GameConnection(this, socket, game);
				
				synchronized(connections) {
					connections.add(connection);
				}
				
				connection.start();
			} catch(IOException ioe) {
				if(server.isClosed()) break;
				
				LevelUp.println("[GameHost] Error while accepting socket: " + ioe.getLocalizedMessage());
				
				if(LevelUp.DEBUG){
					ioe.printStackTrace();
				}
			}
		}
		
		while(!game.gameOver()) {
			synchronized(game) {
				int ticks = game.update();
				
				try {
					game.wait(ticks);
				} catch(InterruptedException e) {
					
				}
			}
		}
	}
}

class GameConnection extends Thread implements GamePlayerListener {
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
	public static int PACKET_UPDATEROUNDOVERCOUNTER= 14;
	
	GameHost host;
	
	Socket socket;
	DataInputStream in;
	DataOutputStream out;
	
	int pid;
	Game game;
	boolean terminated;
	
	public GameConnection(GameHost host, Socket socket, Game game) {
		this.host = host;
		this.socket = socket;
		this.game = game;
		pid = -1;
		terminated = false;
		
		try {
			in = new DataInputStream(socket.getInputStream());
			out = new DataOutputStream(socket.getOutputStream());
		} catch(IOException ioe) {
			LevelUp.println("[GameConnection] Error while initializing streams: " + ioe.getLocalizedMessage());
			
			if(LevelUp.DEBUG) {
				ioe.printStackTrace();
			}
		}
	}
	
	public void println(String message) {
		LevelUp.println("[GameConnection " + socket.getInetAddress().getHostAddress() + "] " + message);
	}
	
	public void run() {
		while(true) {
			try {
				int header = in.readUnsignedByte();
				
				if(header == -1) {
					println("Remote disconnected");
					break;
				} else if(header != PACKET_HEADER) {
					println("Invalid header received from client; terminating connection");
					break;
				}
				
				int identifier = in.readUnsignedByte();
				
				if(pid == -1) {
					if(identifier == PACKET_JOIN) { //JOIN
						String name = in.readUTF();
						pid = host.eventPlayerJoin(this, name);
						
						//respond with the PID
						sendJoin(pid);
						
						if(pid == -1) break;
					} else {
						println("Unkwown packet received (init), id=" + identifier);
						break;
					}
				} else if(host.gameLoaded) {
					if(identifier == PACKET_DECLARE) {
						int suit = in.readInt();
						int amount = in.readInt();
						
						boolean declareSuccess;
						
						synchronized(game) {
							declareSuccess = game.declare(pid, suit, amount);
							
							if(!declareSuccess)
								sendPlayError("Declaration failed");
							else
								game.notifyAll();
						}
					} else if(identifier == PACKET_WITHDRAWDECLARATION) {
						boolean withdrawSuccess;
						
						synchronized(game) {
							withdrawSuccess = game.withdrawDeclaration(pid);
							
							if(!withdrawSuccess)
								sendPlayError("Withdraw failed");
							else
								game.notifyAll();
						}
					} else if(identifier == PACKET_DEFENDDECLARATION) {
						int amount = in.readInt();
						
						boolean defendSuccess;
						
						synchronized(game) {
							defendSuccess = game.defendDeclaration(pid, amount);
							
							if(!defendSuccess)
								sendPlayError("Defend failed");
							else
								game.notifyAll();
						}
					} else if(identifier == PACKET_PLAYCARDS) {
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
						
						boolean playSuccess;
						
						synchronized(game) {
							playSuccess = game.playTrick(pid, cards, amounts);
							
							if(!playSuccess)
								sendPlayError("Play failed");
							else
								game.notifyAll();
						}
					} else {
						println("Unkwown packet received (joined), id=" + identifier);
						break;
					}
				}
			} catch(IOException ioe) {
				println("Error while reading: " + ioe.getLocalizedMessage());
				
				if(LevelUp.DEBUG) {
					ioe.printStackTrace();
				}
				
				break;
			}
		}
		
		//make sure connection is terminated
		terminate();
	}
	
	public synchronized void terminate() {
		if(!terminated) {
			terminated = true;
			
			try {
				socket.close();
			} catch(IOException ioe) {}
			
			if(pid != -1) {
				host.eventPlayerLeave(this, pid);
			}
			
			host.eventPlayerTerminate(this);
		}
	}
	
	public int getPlayer() {
		return pid;
	}
	
	public void sendJoin(int pid) {
		if(!socket.isConnected()) return;
		
		synchronized(out) {
			try {
				out.write(PACKET_HEADER);
				out.write(PACKET_JOIN);
				out.writeInt(pid);
			} catch(IOException ioe) {
				terminate();
			}
		}
	}
	
	public void eventPlayerJoined(int pid, String name) {
		if(!socket.isConnected()) return;
		
		synchronized(out) {
			try {
				out.write(PACKET_HEADER);
				out.write(PACKET_JOINOTHER);
				out.writeInt(pid);
				out.writeUTF(name);
			} catch(IOException ioe) {
				terminate();
			}
		}
	}
	
	public void eventPlayerLeft(int pid) {
		if(!socket.isConnected()) return;
		
		synchronized(out) {
			try {
				out.write(PACKET_HEADER);
				out.write(PACKET_LEAVEOTHER);
				out.writeInt(pid);
			} catch(IOException ioe) {
				terminate();
			}
		}
	}
	
	public void sendGameLoaded() {
		if(!socket.isConnected()) return;
		
		synchronized(out) {
			try {
				out.write(PACKET_HEADER);
				out.write(PACKET_GAMELOADED);
			} catch(IOException ioe) {
				terminate();
			}
		}
	}
	
	public void eventGameStateChange(int newState) {
		if(!socket.isConnected()) return;
		
		synchronized(out) {
			try {
				out.write(PACKET_HEADER);
				out.write(PACKET_GAMESTATECHANGE);
				out.writeInt(newState);
			} catch(IOException ioe) {
				terminate();
			}
		}
	}
	
	public void eventDeclare(int pid, int suit, int amount) {
		if(!socket.isConnected()) return;
		
		synchronized(out) {
			try {
				out.write(PACKET_HEADER);
				out.write(PACKET_DECLARE);
				out.writeInt(pid);
				out.writeInt(suit);
				out.writeInt(amount);
			} catch(IOException ioe) {
				terminate();
			}
		}
	}
	
	public void eventWithdrawDeclaration(int pid) {
		if(!socket.isConnected()) return;
		
		synchronized(out) {
			try {
				out.write(PACKET_HEADER);
				out.write(PACKET_WITHDRAWDECLARATION);
				out.writeInt(pid);
			} catch(IOException ioe) {
				terminate();
			}
		}
	}
	
	public void eventDefendDeclaration(int pid, int amount) {
		if(!socket.isConnected()) return;
		
		synchronized(out) {
			try {
				out.write(PACKET_HEADER);
				out.write(PACKET_DEFENDDECLARATION);
				out.writeInt(pid);
				out.writeInt(amount);
			} catch(IOException ioe) {
				terminate();
			}
		}
	}
	
	public void eventPlayCards(int pid, List<Card> cards, List<Integer> amounts) {
		if(!socket.isConnected()) return;
		
		synchronized(out) {
			try {
				out.write(PACKET_HEADER);
				out.write(PACKET_PLAYCARDS);
				out.writeInt(pid);
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
				terminate();
			}
		}
	}
	
	public void sendPlayError(String message) {
		if(!socket.isConnected()) return;
		
		synchronized(out) {
			try {
				out.write(PACKET_HEADER);
				out.write(PACKET_PLAYERROR);
				out.writeUTF(message);
			} catch(IOException ioe) {
				terminate();
			}
		}
	}
	
	public void eventDealtCard(Card card) {
		if(!socket.isConnected()) return;
		
		synchronized(out) {
			try {
				out.write(PACKET_HEADER);
				out.write(PACKET_DEALTCARD);
				out.writeInt(card.getSuit());
				out.writeInt(card.getValue());
			} catch(IOException ioe) {
				terminate();
			}
		}
	}
	
	public void eventUpdateBetCounter(int newCounter) {
		if(!socket.isConnected()) return;
		
		synchronized(out) {
			try {
				out.write(PACKET_HEADER);
				out.write(PACKET_UPDATEBETCOUNTER);
				out.writeInt(newCounter);
			} catch(IOException ioe) {
				terminate();
			}
		}
	}
	
	public void eventUpdateRoundOverCounter(int newCounter) {
		if(!socket.isConnected()) return;
		
		synchronized(out) {
			try {
				out.write(PACKET_HEADER);
				out.write(PACKET_UPDATEROUNDOVERCOUNTER);
				out.writeInt(newCounter);
			} catch(IOException ioe) {
				terminate();
			}
		}
	}
}

class GameSlot {
	GameConnection connection;
	String name;
}