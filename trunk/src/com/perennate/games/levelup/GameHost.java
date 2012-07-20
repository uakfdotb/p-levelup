package com.perennate.games.levelup;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

import com.perennate.games.levelup.engine.Game;
import com.perennate.games.levelup.util.Util;

public class GameHost extends Thread {
	public static int DEFAULT_PORT = 7553;
	
	ServerSocket server;
	ArrayList<GameConnection> connections;
	
	boolean gameLoaded;
	GameSlot[] slots;
	
	Game game;
	
	//configuration
	String commandTrigger; //if this prefixes a string, it's possibly a command
	
	public GameHost() {
		game = new Game(Config.getInt("numplayers", 4), true);
		connections = new ArrayList<GameConnection>();
		slots = new GameSlot[Config.getInt("numplayers", 4)];
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
		
		commandTrigger = Config.getString("trigger", "!");
	}
	
	public void loadGame() {
		if(gameLoaded) return;
		
		synchronized(game) {
			if(!gameLoaded) {
				gameLoaded = true;
				
				try {
					server.close();
				} catch(IOException ioe) {
					LevelUp.println("[GameHost] Error while closing server socket; game may not update");
				}
				
				synchronized(connections) {
					for(GameConnection i : connections) {
						i.sendGameLoaded();
					}
				}
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

			LevelUp.println("[GameHost] Player [" + name + "|" + connection.socket.getInetAddress().getHostAddress() + "] has joined the game");
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
			
			//make sure player has the correct size of the game
			connection.eventResized(slots.length);
			
			//if we're done, then start the game
			if(done) {
				loadGame();
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
				
				game.removeListener(connection);
				game.playerLeft(pid);
			}
		}
	}
	
	public void eventPlayerTerminate(GameConnection connection) {
		LevelUp.println("[GameHost] Connection from " + connection.socket.getInetAddress().getHostAddress() + " is terminated");
		
		synchronized(connections) {
			connections.remove(connection);
		}
	}
	
	public void eventPlayerChat(GameConnection source, String name, String message) {
		boolean hideChat = false;
		
		//check commands
		if(source != null && message.startsWith(commandTrigger)) {
			String[] parts = message.substring(commandTrigger.length()).split(" ", 2);
			
			if(parts.length >= 1) { 
				parts[0] = parts[0].toLowerCase();
				
				if(source.administrator) {
					if(parts[0].equals("savegame") && parts.length >= 2) {
						LevelUp.println("[GameHost] Attempting to save game to " + parts[1]);
						File saveGamesPath = new File(Config.getString("savegames_path", "savegames/"));
						File targetFile = new File(saveGamesPath, Util.cleanFileName(parts[1]));
						
						if(targetFile.exists()) {
							chatTo(source, "The target file already exists.");
						} else {
							try {
								FileOutputStream out = new FileOutputStream(targetFile);
								
								boolean success = false;
								
								synchronized(game) {
									success = Game.writeGame(game, out);
								}
								
								if(success) {
									chatTo(source, "Game saved successfully as: [" + targetFile.getName() + "]");
								} else {
									chatTo(source, "Failed to save game!");
								}
							} catch(IOException ioe) {
								chatTo(source, "Error while writing to file: " + ioe.getLocalizedMessage());
							}
						}
					} else if(parts[0].equals("loadgame") && parts.length >= 2) {
						LevelUp.println("[GameHost] Attempting to load game from " + parts[1]);
						File saveGamesPath = new File(Config.getString("savegames_path", "savegames/"));
						File sourceFile = new File(saveGamesPath, Util.cleanFileName(parts[1]));
						
						if(!sourceFile.exists()) {
							chatTo(source, "The source file does not exist.");
						} else {
							String error = loadSavedGame(sourceFile);
							
							if(error == null) {
								eventPlayerChat(null, "Server", "The saved game has been loaded successfully.");
							} else {
								chatTo(source, error);
							}
						}
					} else if(parts[0].equals("kick") && parts.length >= 2) {
						GameConnection connection = getConnectionByPartial(parts[1]);
						
						if(connection != null) {
							eventPlayerChat(null, "Server", "Player [" + connection.name + "] was kicked by admin [" + source.name + "].");
							connection.terminate();
						} else {
							chatTo(source, "Failed to kick: player not found.");
						}
					} else if(parts[0].equals("swap") && parts.length >= 2) {
						String[] subParts = parts[1].split(" ");
						
						if(subParts.length >= 2) {
							try {
								int id1 = Integer.parseInt(subParts[0]);
								int id2 = Integer.parseInt(subParts[1]);
								
								synchronized(game) {
									game.playerSwapped(id1,  id2);
								}
							} catch(NumberFormatException e) {}
						}
					} else if(parts[0].equals("resize") && parts.length >= 2) {
						try {
							synchronized(game) {
								int numPlayers = Integer.parseInt(parts[1]);
								
								//update slots to the new size
								//note that old connections will be kicked automatically
								// by the Game calling eventPlayerLeft
								GameSlot[] newSlots = new GameSlot[numPlayers];
								
								for(int i = 0; i < newSlots.length; i++) {
									if(i < slots.length) {
										newSlots[i] = slots[i];
									} else {
										newSlots[i] = new GameSlot();
									}
								}
								
								slots = newSlots;
								
								game.resized(numPlayers);
							}
						} catch(NumberFormatException e) {}
					}
				} else {
					if(parts[0].equals("password")) {
						hideChat = true;
						String password = Config.getString("password_" + source.name.toLowerCase(), null);
						
						if(password != null && password.equals(parts[1])) {
							source.administrator = true;
							chatTo(source, "You have logged in successfully");
						} else {
							source.terminate();
						}
					}
				}
			}
		}
		
		if(!hideChat) {
			LevelUp.println("[GameHost] [" + name + "]: " + message);
			
			synchronized(connections) {
				for(GameConnection connection : connections) {
					connection.sendChat(name, message);
				}
			}
		}
	}
	
	//send a chat to a specific connection
	public void chatTo(GameConnection target, String message) {
		synchronized(game) {
			LevelUp.println("[GameHost] [-> " + game.getPlayer(target.pid).getName() + "] " + message);
		}
		
		target.sendChat("Server", message);
	}
	
	//searches for a GameConnection instance by a partial
	// of the player's name
	public GameConnection getConnectionByPartial(String name) {
		name = name.toLowerCase();
		
		int foundPID = -1;
		
		synchronized(game) {
			for(int i = 0; i < game.getNumPlayers(); i++) {
				if(game.getPlayer(i).getName().toLowerCase().equals(name)) {
					foundPID = i;
					break;
				} else if(game.getPlayer(i).getName().contains(name)) {
					foundPID = i;
				}
			}
		}
		
		if(foundPID == -1) return null;
		
		synchronized(connections) {
			for(GameConnection connection : connections) {
				if(connection.pid == foundPID) {
					return connection;
				}
			}
		}
		
		return null;
	}
	
	public void terminate() {
		synchronized(connections) {
			for(GameConnection connection : connections) {
				connection.terminate();
			}
		}
		
		if(server != null && !server.isClosed()) {
			try {
				server.close();
			} catch(IOException ioe) {}
		}
	}
	
	//attempts to load a saved game from the specified file
	//returns error message on failure, or null on success
	public String loadSavedGame(File file) {
		if(!file.exists()) {
			String reason = "Loading saved game: error: file does not exist";
			LevelUp.println("[GameHost] " + reason);
			return reason;
		}
		
		LevelUp.println("[GameHost] Attempting to load saved game from " + file.getAbsolutePath());
		
		synchronized(game) {
			//make sure all slots are full
			//otherwise, when the saved game is loaded we'll be missing people
			for(int i = 0; i < slots.length; i++) {
				if(slots[i].connection == null) {
					String reason = "Loading saved game: error: slot " + i + " is unoccupied";
					LevelUp.println("[GameHost] " + reason);
					return reason;
				}
			}
		
			Game loadedGame = null;
			
			try {
				FileInputStream in = new FileInputStream(file);
				loadedGame = Game.readGame(in);
			} catch(IOException ioe) {
				String reason = "Loading saved game: error: " + ioe.getLocalizedMessage();
				LevelUp.println("[GameHost] " + reason);
				return reason;
			}
			
			if(loadedGame == null) {
				String reason = "Loading saved game: error: game is null";
				LevelUp.println("[GameHost] " + reason);
				return reason;
			}
			
			if(loadedGame.getNumPlayers() != slots.length) {
				String reason = "Loading saved game: error: number of players in saved game " +
						"(" + loadedGame.getNumPlayers() + ") doesn't match current slots (" + slots.length + ")";
				LevelUp.println("[GameHost] " + reason);
				return reason;
			}
			
			//synchronize current game with the loaded one
			game.synchronize(loadedGame, -1);
			
			//tell all the clients to synchronize
			//use byte array to buffer the game data
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			boolean success = Game.writeGame(game, out);
			
			if(!success) {
				String reason = "Loading saved game: error: failed to buffer game data; disconnecting clients";
				LevelUp.println("[GameHost] " + reason);
				terminate();
				return reason;
			}
			
			byte[] bytes = out.toByteArray();
			
			synchronized(connections) {
				for(GameConnection connection : connections) {
					connection.sendSync(bytes.length);
				}
			}
			
			for(int i = 0; i < bytes.length; i += 1400) {
				synchronized(connections) {
					for(GameConnection connection : connections) {
						connection.sendSyncPart(bytes, i, Math.min(1400, bytes.length - i));
					}
				}
			}
		}
		
		LevelUp.println("[GameHost] Saved game loaded successfully, and synchronization completed");
		
		//make sure to load the game, because the saved game
		// is probably in the middle of something
		loadGame();
		
		return null;
	}
	
	public void run() {
		if(server == null || !server.isBound()) {
			//something probably failed in the server initialization code
			//we sleep for a while
			//then, in case we're hosting multiple games, notify to host another
			
			try {
				Thread.sleep(5000);
			} catch(InterruptedException e) {}
			
			gameLoaded = true;
			
			synchronized(this) {
				this.notifyAll();
			}
			
			return;
		}
		
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
		
		//if we're hosting multiple games, notify that this one started
		synchronized(this) {
			this.notifyAll();
		}
		
		while(!game.gameOver() && !connections.isEmpty()) {
			synchronized(game) {
				int ticks = game.update();
				
				try {
					game.wait(ticks);
				} catch(InterruptedException e) {
					
				}
			}
		}
		
		LevelUp.println("[GameHost] Game is over");
	}
}

class GameSlot {
	GameConnection connection;
	String name;
}