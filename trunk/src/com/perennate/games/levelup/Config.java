package com.perennate.games.levelup;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

public class Config {
	static Properties properties;
	
	public static boolean init(String propertiesFile) {
		if(LevelUp.APPLET == null) {
			properties = new Properties();
			LevelUp.println("[Config] Loading configuration file " + propertiesFile);
			
			try {
				properties.load(new FileInputStream(propertiesFile));
				return true;
			} catch(FileNotFoundException e) {
				LevelUp.println("[Config] Fatal error: could not find configuration file " + propertiesFile);
				return false;
			} catch(IOException e) {
				LevelUp.println("[Config] Fatal error: error while reading from configuration file " + propertiesFile);
				return false;
			}
		} else {
			LevelUp.println("[Config] Loading from applet");
			return true;
		}
	}
	
	public static String getString(String key, String defaultValue) {
		String str;
		
		if(LevelUp.APPLET == null)
			str = properties.getProperty(key, defaultValue);
		else
			str = LevelUp.APPLET.getParameter(key);
			
		if(str == null || str.trim().equals("")) {
			return defaultValue;
		} else {
			return str;
		}
	}
	
	public static int getInt(String key, int defaultValue) {
		try {
			String result = null;
			
			if(LevelUp.APPLET == null)
				result = properties.getProperty(key, null);
			else
				result = LevelUp.APPLET.getParameter(key);
			
			if(result != null) {
				return Integer.parseInt(result);
			} else {
				return defaultValue;
			}
		} catch(NumberFormatException nfe) {
			System.out.println("[Config] Warning: invalid integer for key " + key);
			return defaultValue;
		}
	}
	
	public static boolean getBoolean(String key, boolean defaultValue) {
		String result = null;
		
		if(LevelUp.APPLET == null)
			result = properties.getProperty(key, null);
		else
			result = LevelUp.APPLET.getParameter(key);
		
		if(result != null) {
			if(result.equals("true") || result.equals("1")) return true;
			else if(result.equals("false") || result.equals("0")) return false;
			else {
				System.out.println("[Config] Warning: invalid boolean for key " + key);
				return defaultValue;
			}
		} else {
			return defaultValue;
		}
	}
	
	public static boolean containsKey(String key) {
		if(LevelUp.APPLET == null)
			return properties.containsKey(key);
		else
			return LevelUp.APPLET.getParameter(key) == null;
	}
}