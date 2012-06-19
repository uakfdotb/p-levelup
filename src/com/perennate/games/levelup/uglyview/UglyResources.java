package com.perennate.games.levelup.uglyview;

import java.awt.Color;
import java.awt.Font;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import javax.imageio.ImageIO;

import com.perennate.games.levelup.Config;
import com.perennate.games.levelup.LevelUp;

public class UglyResources {
	UglyView view;
	HashMap<String, Image> images;
	HashMap<String, Font> fonts;
	HashMap<String, Color> colors;
	
	String resourcesPath;
	File resourcesDirectory;

	public UglyResources(UglyView view) {
		this.view = view;
		images = new HashMap<String, Image>();
		fonts = new HashMap<String, Font>();
		colors = new HashMap<String, Color>();
	}

	public void loadImages() {
		resourcesPath = Config.getString("resources_path", "res");
		
		if(LevelUp.APPLET == null) {
			resourcesDirectory = new File(resourcesPath);
			if(!resourcesDirectory.exists()) {
				view.eventUglyError("Resources directory does not exist");
				return;
			}
		}

		for(int i = 0; i < 54; i++) {
			loadImage(i + ".png", "card_" + i);
		}
	}
	
	public void loadFonts() {
		Font playerCircleNameFont = new Font("Arial", Font.BOLD, 20);
		fonts.put("playerCircleName", playerCircleNameFont);
		
		Font playerCircleAttributeFont = new Font("Arial", Font.PLAIN, 16);
		fonts.put("playerCircleAttribute", playerCircleAttributeFont);
		
		Font timerFont = new Font("Times New Roman", Font.BOLD, 26);
		fonts.put("timer", timerFont);
	}
	
	public void loadColors() {
		Color gold = new Color(207, 181, 59);
		colors.put("gold", gold);
	}

	public void loadImage(String file_name, String image_name) {
		if(resourcesDirectory == null) {
			images.put(image_name, LevelUp.APPLET.getImage(LevelUp.APPLET.getDocumentBase(), resourcesPath + "/" + file_name));
		} else {
			File image_file = new File(resourcesDirectory, file_name);
			loadImage(image_file, image_name);
		}
	}

	public void loadImage(File file, String name) {
		try {
			BufferedImage read = ImageIO.read(file);
			images.put(name, read);
		} catch(IOException ioe) {
			ioe.printStackTrace();
			view.eventUglyError("Failed to load the image: " + name);
			LevelUp.println("Error while loading " + file.getAbsolutePath());
		}
	}

	public Image getImage(String name) {
		return images.get(name);
	}
	
	public Font getFont(String name) {
		return fonts.get(name);
	}
	
	public Color getColor(String name) {
		return colors.get(name);
	}
}
