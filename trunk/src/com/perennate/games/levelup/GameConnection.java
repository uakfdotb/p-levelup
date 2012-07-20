package com.perennate.games.levelup;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

import com.perennate.games.levelup.engine.Card;
import com.perennate.games.levelup.engine.CardTuple;
import com.perennate.games.levelup.engine.Game;
import com.perennate.games.levelup.engine.GamePlayerListener;

public 

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
	public static int PACKET_UPDATEROUNDOVERCOUNTER = 12;
	public static int PACKET_BOTTOM = 13;
	public static int PACKET_SELECTBOTTOM = 14;
	public static int PACKET_CHAT = 15;
	public static int PACKET_SYNC = 16;
	public static int PACKET_SYNCPART = 17;
	public static int PACKET_SWAP = 18;
	public static int PACKET_NEWPID = 19;
	public static int PACKET_RESIZED = 20;
	public static int PACKET_NOOP = 21;
	
	GameHost host;
	
	Socket socket;
	DataInputStream in;
	DataOutputStream out;
	
	int pid;
	String name;
	Game game;
	boolean terminated;
	
	//allows clients to use !password to login as an administrator
	boolean administrator;
	
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
		try {
			socket.setSoTimeout(40000); //client should send keep-alive every 30 seconds
		} catch(IOException ioe) {
			println("Warning: failed to set socket timeout");
		}
		
		while(true) {
			try {
				int header = in.readUnsignedByte();
				
				if(header == -1) {
					println("Remote disconnected");
					break;
				} else if(header != PACKET_HEADER) {
					println("Invalid header " + header + " received from client; terminating connection");
					break;
				}
				
				int identifier = in.readUnsignedByte();
				
				if(identifier == PACKET_NOOP) {
					//respond with our own NOOP packet
					//client won't respond to this one so it's good
					sendNoop();
				} else if(pid == -1) {
					if(identifier == PACKET_JOIN) { //JOIN
						name = in.readUTF();
						pid = host.eventPlayerJoin(this, name);
						
						//respond with the PID
						sendJoin(pid);
						
						if(pid == -1) break;
					} else {
						println("Unknown packet received (init), id=" + identifier);
						break;
					}
				} else if(identifier == PACKET_CHAT) {
					String message = in.readUTF();
					
					String name = game.getPlayer(pid).getName();
					host.eventPlayerChat(this, name, message);
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
							playSuccess = game.playTrick(pid, CardTuple.createTrick(cards, amounts));
							
							if(!playSuccess)
								sendPlayError("Play failed");
							else
								game.notifyAll();
						}
					} else if(identifier == PACKET_SELECTBOTTOM) {
						int numCards = in.readInt();
						List<Card> cards = new ArrayList<Card>(numCards);
						
						for(int i = 0; i < numCards; i++) {
							int suit = in.readInt();
							int value = in.readInt();
							cards.add(game.constructCard(suit, value));
						}
						
						boolean bottomSuccess;
						
						synchronized(game) {
							bottomSuccess = game.selectBottom(pid, cards);
							
							if(!bottomSuccess)
								sendPlayError("Bottom selection failed");
							else
								game.notifyAll();
						}
					} else {
						println("Unknown packet received (joined), id=" + identifier);
						break;
					}
				} else {
					println("Unknown packet received (init), id=" + identifier);
					break;
				}
			} catch(SocketTimeoutException e) {
				println("Timed out");
				break;
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
	
	public void close() {
		if(socket != null && !socket.isClosed()) {
			println("Closing connection");
			
			try {
				socket.close();
			} catch(IOException ioe) {}
		}
	}
	
	public synchronized void terminate() {
		if(!terminated) {
			terminated = true;
			
			close();
			
			if(pid != -1) {
				host.eventPlayerLeave(this, pid);
			}
			
			host.eventPlayerTerminate(this);
		}
	}
	
	public int getPlayer() {
		return pid;
	}
	
	//in the functions below, we only send if the socket is connected
	//we also synchronize output and close on failure
	//terminate is not called because that could lead to a deadlock
	// since we call eventPlayerLeave and eventPlayerTerminate from
	// the function, if disconnection occurs in eventPlayerLeft
	
	public void sendJoin(int pid) {
		if(!socket.isConnected()) return;
		
		synchronized(out) {
			try {
				out.write(PACKET_HEADER);
				out.write(PACKET_JOIN);
				out.writeInt(pid);
			} catch(IOException ioe) {
				close();
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
				close();
			}
		}
	}
	
	public void eventPlayerLeft(int pid) {
		if(!socket.isConnected()) return;
		
		if(pid == this.pid) {
			//looks like we were kicked somehow, maybe while resizing game
			//anyways, terminate the connection
			close();
			return;
		}
		
		synchronized(out) {
			try {
				out.write(PACKET_HEADER);
				out.write(PACKET_LEAVEOTHER);
				out.writeInt(pid);
			} catch(IOException ioe) {
				close();
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
				close();
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
				close();
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
				close();
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
				close();
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
				close();
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
				close();
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
				close();
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
				close();
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
				close();
			}
		}
	}
	
	public void eventBottom(List<Card> cards) {
		if(!socket.isConnected()) return;
		
		synchronized(out) {
			try {
				out.write(PACKET_HEADER);
				out.write(PACKET_BOTTOM);
				out.writeInt(cards.size());
				
				for(Card card : cards) {
					out.writeInt(card.getSuit());
					out.writeInt(card.getValue());
				}
			} catch(IOException ioe) {
				close();
			}
		}
	}
	
	public void eventSelectBottom(List<Card> cards) {
		if(!socket.isConnected()) return;
		
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
				close();
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
				close();
			}
		}
	}
	
	public void sendChat(String name, String message) {
		if(!socket.isConnected()) return;
		
		synchronized(out) {
			try {
				out.write(PACKET_HEADER);
				out.write(PACKET_CHAT);
				out.writeUTF(name);
				out.writeUTF(message);
			} catch(IOException ioe) {
				close();
			}
		}
	}
	
	public void sendSync(int len) {
		if(!socket.isConnected()) return;
		
		synchronized(out) {
			try {
				out.write(PACKET_HEADER);
				out.write(PACKET_SYNC);
				out.writeInt(len);
			} catch(IOException ioe) {
				close();
			}
		}
	}
	
	public void sendSyncPart(byte[] bytes, int offset, int len) {
		if(!socket.isConnected()) return;
		
		synchronized(out) {
			try {
				out.write(PACKET_HEADER);
				out.write(PACKET_SYNCPART);
				out.writeShort((short) len);
				out.write(bytes, offset, len);
			} catch(IOException ioe) {
				close();
			}
		}
	}
	
	public void eventPlayerSwapped(int id1, int id2) {
		if(!socket.isConnected()) return;
		
		synchronized(out) {
			try {
				out.write(PACKET_HEADER);
				out.write(PACKET_SWAP);
				out.writeInt(id1);
				out.writeInt(id2);
			} catch(IOException ioe) {
				close();
			}
		}
	}
	
	public void eventNewPID(int newPID) {
		if(!socket.isConnected()) return;
		
		synchronized(out) {
			try {
				out.write(PACKET_HEADER);
				out.write(PACKET_NEWPID);
				out.writeInt(newPID);
			} catch(IOException ioe) {
				close();
			}
		}
	}
	
	public void eventResized(int newSize) {
		if(!socket.isConnected()) return;
		
		synchronized(out) {
			try {
				out.write(PACKET_HEADER);
				out.write(PACKET_RESIZED);
				out.writeInt(newSize);
			} catch(IOException ioe) {
				close();
			}
		}
	}
	
	public void sendNoop() {
		if(!socket.isConnected()) return;
		
		synchronized(out) {
			try {
				out.write(PACKET_HEADER);
				out.write(PACKET_NOOP);
			} catch(IOException ioe) {
				close();
			}
		}
	}
}