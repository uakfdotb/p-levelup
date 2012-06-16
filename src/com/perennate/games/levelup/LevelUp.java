package com.perennate.games.levelup;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import com.perennate.games.levelup.engine.Card;
import com.perennate.games.levelup.engine.CardHandComparator;

public class LevelUp {
	public static int LEVELUP_VERSION = 0;
	public static String LEVELUP_VERSION_STRING = "p-levelup 0 (http://levelup.perennate.com/)";
	public static File logTarget = null;
	public static boolean DEBUG = true;
	
	public static void main(String args[]) {
		println(LEVELUP_VERSION_STRING);
		
		String propertiesFile = "levelup.cfg";
		
		if(args.length >= 1) {
			propertiesFile = args[0];
		}
		
		boolean result = Config.init(propertiesFile);
		if(!result) return; //fatal error
		
		String logFile = Config.getString("lu_log", null);
		
		if(logFile != null) {
			logTarget = new File(logFile);
		}
		
		println("[Main] Starting up");
		
		if(Config.getBoolean("host", false)) {
			GameHost host = new GameHost();
			host.start();
		} else {
			GameClient client = new GameClient();
			client.start();
			
			client.sendJoin("yourface");
			
			try {
				BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
				
				String line;
				List<Card> selectedCards = new ArrayList<Card>();
				List<Integer> selectedAmounts = new ArrayList<Integer>();
				
				while((line = in.readLine()) != null) {
					String[] parts = line.split(" ");
					
					try {
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
	
	public static void println(String message) {
		System.out.println(message);
		
		//output to file
		if(logTarget != null) {
			try {
				PrintWriter out = new PrintWriter(new FileWriter(logTarget));
				out.println(message);
				out.close();
			} catch(IOException ioe) {
				System.out.println("[Main] Output to " + logTarget + " failed; disabling");
				logTarget = null;
			}
		}
	}
	
	public static void debug(String message) {
		if(DEBUG) {
			System.out.println(message);
			
			//output to file
			if(logTarget != null) {
				try {
					PrintWriter out = new PrintWriter(new FileWriter(logTarget));
					out.println(message);
					out.close();
				} catch(IOException ioe) {
					System.out.println("[Main] Output to " + logTarget + " failed; disabling");
					logTarget = null;
				}
			}
		}
	}
}
