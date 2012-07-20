package com.perennate.games.levelup;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

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
	public static int PACKET_CHAT = 15;
	public static int PACKET_SYNC = 16;
	public static int PACKET_SYNCPART = 17;
	public static int PACKET_SWAP = 18;
	public static int PACKET_NEWPID = 19;
	public static int PACKET_RESIZED = 20;
	public static int PACKET_NOOP = 21;
	
	Timer timer;
	Socket socket;
	DataInputStream in;
	DataOutputStream out;
	boolean isConnected;
	
	Game game;
	View view;
	int pid;
	
	//byte[] buffer for syncing game with server
	byte[] syncBuf;
	int syncPos;
	
	public GameClient(Game game, View view) {
		this.view = view;
		this.game = game;
		
		view.setClient(this);
		
		pid = -1;
		isConnected = false;
		
		timer = new Timer();
	}
	
	public synchronized boolean connect(String hostname, int port) {
		if(isConnected) return false;
		
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
			return false;
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
		
		//add new noop task, and cancel if there's an existing one
		if(timer != null) {
			timer.cancel();
		}
		
		timer = new Timer();
		timer.schedule(new NoopTask(), 0, 30000); //send NOOP to server every 30 seconds
		
		return true;
	}
	
	public void terminate(String reason) {
		if(isConnected) {
			isConnected = false;
			
			LevelUp.println("[GameClient] Terminating connection: " + reason);
			view.eventTerminateError(reason);
			
			if(socket != null && !socket.isClosed()) {
				try {
					socket.close();
				} catch(IOException ioe) {}
			}
		}
	}
	
	public void run() {
		//reason for termination
		String reason = "unknown";
			
		while(isConnected) {
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
				} else if(identifier == PACKET_CHAT) {
					String name = in.readUTF();
					String message = in.readUTF();
					view.eventPlayerChat(name, message);
				} else if(identifier == PACKET_SYNC) {
					int len = in.readInt();
					LevelUp.println("[GameClient] Syncing game with server, length=" + len + " bytes");
					
					if(len > 1024 * 1024) {
						LevelUp.println("[GameClient] Sync failed: game data rejected for large size");
						reason = "sync failed";
						break;
					} else if(len < 0) {
						LevelUp.println("[GameClient] Sync failed: game data rejected for negative length");
						reason = "sync failed";
						break;
					}
					
					syncBuf = new byte[len];
					syncPos = 0;
				} else if(identifier == PACKET_SYNCPART) {
					int len = in.readUnsignedShort();
					byte[] bytes = new byte[len];
					in.readFully(bytes);
					
					if(syncPos + len > bytes.length) {
						LevelUp.println("[GameClient] Sync failed: sync position at " + (syncPos + len) + ", greater than length");
						reason = "sync failed";
						break;
					}
					
					System.arraycopy(bytes, 0, syncBuf, syncPos, len);
					syncPos += len;
					
					//check if we're done
					if(syncPos >= bytes.length) {
						LevelUp.println("[GameClient] Sync transfer completed; updating game instance...");
						
						synchronized(game) {
							ByteArrayInputStream in = new ByteArrayInputStream(syncBuf);
							Game syncGame = Game.readGame(in);
							
							if(syncGame == null) {
								LevelUp.println("[GameClient] Sync failed: Game.readGame returned null (see above for errors)");
								reason = "sync failed";
								break;
							}
							
							game.synchronize(syncGame, pid);
						}
					}
				} else if(identifier == PACKET_SWAP) {
					int id1 = in.readInt();
					int id2 = in.readInt();
					
					synchronized(game) {
						game.playerSwapped(id1, id2);
					}
				} else if(identifier == PACKET_NEWPID) {
					//view should have already been notified by the swap
					// event above, but now we update our own pid
					pid = in.readInt();
				} else if(identifier == PACKET_RESIZED) {
					int newSize = in.readInt();
					
					synchronized(game) {
						game.resized(newSize);
					}
				} else if(identifier == PACKET_NOOP) {
					//cool
					//server will respond if we send another NOOP, so we don't
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
	
	public void sendChat(String message) {
		if(!isConnected) return;
		
		synchronized(out) {
			try {
				out.write(PACKET_HEADER);
				out.write(PACKET_CHAT);
				out.writeUTF(message);
			} catch(IOException ioe) {
				terminate("failed to send packet");
			}
		}
	}
	
	public void sendNoop() {
		if(!isConnected) return;
		
		synchronized(out) {
			try {
				out.write(PACKET_HEADER);
				out.write(PACKET_NOOP);
			} catch(IOException ioe) {
				terminate("failed to send packet");
			}
		}
	}

	class NoopTask extends TimerTask {
		public void run() {
			sendNoop();
		}
	}
}