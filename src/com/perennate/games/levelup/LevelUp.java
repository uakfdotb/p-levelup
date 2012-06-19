package com.perennate.games.levelup;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import com.perennate.games.levelup.engine.Game;
import com.perennate.games.levelup.uglyview.UglyView;

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
			Game game = new Game(Config.getInt("numplayers", 4), false);
			
			String viewSelection = Config.getString("view", "terminal");
			View view = null;
			
			if(viewSelection.equals("terminal")) {
				view = new TerminalView(game);
			} else if(viewSelection.equals("ugly")) {
				view = new UglyView(game);
			} else {
				println("[Main] Error: view selection [" + viewSelection + "] is invalid");
				System.exit(-1);
			}
			
			GameClient client = new GameClient(game, view);
			view.run();
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
