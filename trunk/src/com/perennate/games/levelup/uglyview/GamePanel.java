package com.perennate.games.levelup.uglyview;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.AffineTransform;
import java.awt.image.ImageObserver;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;

import com.perennate.games.levelup.LevelUp;
import com.perennate.games.levelup.engine.Bet;
import com.perennate.games.levelup.engine.Card;
import com.perennate.games.levelup.engine.CardTuple;
import com.perennate.games.levelup.engine.Game;
import com.perennate.games.levelup.engine.Player;

public class GamePanel extends JPanel implements ImageObserver {
	public static int WIDTH = 800;
	public static int HEIGHT = 600;
	public static int CARDSHEIGHT = 100;
	
	public static int CARD_WIDTH = 73;
	public static int CARD_HEIGHT = 97;
	
	public static int CARD_SMALL_WIDTH = 15;
	public static int CARD_MEDIUM_WIDTH = 30;
	
	//resource collection with images, fonts, and colors
	UglyResources resources;
	
	UglyView view;
	Game game;
	
	//list of cards last placed on screen
	// so that we can track mouse clicks
	List<CardPlacement> currentCards;
	
	//manages current card selection
	CardSelector cardSelector;
	
	public GamePanel(UglyView view, Game game) {
		super();
		
		this.view = view;
		this.game = game;
		
		//card selection related initialization
		currentCards = new ArrayList<CardPlacement>();
		cardSelector = new CardSelector(view, this);
		
		addMouseListener(cardSelector);
		
		//load resources
		resources = new UglyResources(view);
		resources.loadImages();
		resources.loadFonts();
		resources.loadColors();
		
		//set some things to make it work properly
        setBackground(Color.WHITE);
		setPreferredSize(new Dimension(WIDTH, HEIGHT));
	}
	
	public void paintComponent(Graphics g_old) {
		super.paintComponent(g_old);
		
		long millisStart = System.currentTimeMillis();
		
		Graphics2D g = (Graphics2D) g_old;
		
		//scale from game to current window size
		AffineTransform savedTransform = g.getTransform();
		AffineTransform scaleTransform = AffineTransform.getScaleInstance((double) getWidth() / WIDTH, (double) getHeight() / HEIGHT);
		g.transform(scaleTransform);
		
		synchronized(game) {
			//get our pid
			int pid = view.getPlayer();
			
			//draw our cards
			// CARD_SMALL_WIDTH is the width between cards in our deck
			//  to put the cards one on top of the previous one
			// CARD_WIDTH is the total size of the card (the last one
			//  needs to be drawn in full
			//we also add our cards to the currentCards list.
			// We don't bother with width and height in the list because
			// will be processed in reverse order from how the cards are
			// drawn on the screen.
			currentCards.clear();
			List<Card> hand = game.getPlayer(pid).getHand();
			
			int cardsXStart = WIDTH / 2 - (hand.size() - 1) * CARD_SMALL_WIDTH / 2 - CARD_WIDTH / 2;
			int cardsY = HEIGHT - CARDSHEIGHT + 1;
			
			for(int i = 0; i < hand.size(); i++) {
				Card card = hand.get(i);
				Image image = resources.getImage("card_" + card.getId());
				
				int cardX = cardsXStart + i * CARD_SMALL_WIDTH;
				int cardY = cardsY;
				
				//if card is selected, draw it a bit higher
				if(cardSelector.isSelected(card)) {
					cardY -= 6;
				}
				
				g.drawImage(image, cardX, cardY, this);
				
				synchronized(currentCards) {
					//take transform into account when we're creating
					// card placement objects
					double[] dst = new double[2];
					scaleTransform.transform(new double[] {cardX, cardY}, 0, dst, 0, 1);
					currentCards.add(new CardPlacement(card, (int) dst[0], (int) dst[1], CARD_WIDTH, CARD_HEIGHT));
				}
			}
			
			//draw the players in a circle, along with currently played cards (if any)
			int playerCenterX = WIDTH / 2;
			int playerCenterY = (HEIGHT - CARDSHEIGHT) / 2;
			int playerRadiusX = playerCenterX - 65;
			int playerRadiusY = playerCenterY - 50;
			
			int playerTrickRadiusX = playerCenterX - 230;
			int playerTrickRadiusY = playerCenterY - 150;
			
			int numPlayers = game.getNumPlayers();
			int numPlays = game.getNumStoredPlays();
			
			for(int i = 0; i < numPlayers; i++) {
				Player player = game.getPlayer(i);
				
				//see where we should place this player
				//To do this, we first get their relative position from our current player
				// by subtracting and modding. Then, we calculate the radian placement such
				// that we will be placed on the bottom part of the screen. Then, we just
				// go around the circle according to the values we have already calculaed
				int relativeId = (i - pid) % numPlayers;
				double radians = ((double) relativeId / numPlayers + 0.25) * 2 * Math.PI;
	
				//draw the player information
				//this includes name, number of points, and team status
				
				//circleX and circleY identify the position of player information
				int circleX = (int) (playerRadiusX * Math.cos(radians)) + playerCenterX;
				int circleY = (int) (playerRadiusY * Math.sin(radians)) + playerCenterY;
				
				g.setFont(resources.getFont("playerCircleName"));
				g.setColor(Color.DARK_GRAY);
				
				//assign colors depending on player status (DARK_GRAY remains default)
				if(game.getState() == Game.STATE_PLAYING && i == game.getNextPlayer()) {
					g.setColor(Color.RED);
				} else if(relativeId == 0) {
					g.setColor(resources.getColor("gold"));
				}
				
				String playerName = player.getName();
				if(playerName == null) playerName = "Empty";
				g.drawString(playerName, circleX - 55, circleY - 15);
				
				//set font and reset color
				g.setFont(resources.getFont("playerCircleAttribute"));
				g.setColor(Color.DARK_GRAY);
				
				//allocate 15 pixels per attribute
				g.drawString("Points: " + player.getPoints(), circleX - 55, circleY + 3);
				g.drawString("Status: " + player.getDefendingString(), circleX - 55, circleY + 18);
				g.drawString("Level: " + player.getLevel(), circleX - 55, circleY + 33);
	
				//now draw cards, if any
				circleX = (int) (playerTrickRadiusX * Math.cos(radians)) + playerCenterX;
				circleY = (int) (playerTrickRadiusY * Math.sin(radians)) + playerCenterY;
				List<Card> drawCards = new ArrayList<Card>();
				Bet bet;
				
				//if state is playing, then check if player has played this round
				// we add numPlayers to ensure that the modulo will be non-negative
				if(game.getState() == Game.STATE_PLAYING && (i - game.getStoredStartingPlayer() + numPlayers) % numPlayers < numPlays) {
					List<CardTuple> trick = game.getStoredPlay((i - game.getStoredStartingPlayer() + numPlayers) % numPlayers);
					
					for(CardTuple tuple : trick) {
						for(int j = 0; j < tuple.getAmount(); j++) {
							drawCards.add(tuple.getCard());
						}
					}
				}
				
				//otherwise we might still have cards to show if betting
				else if((game.getState() == Game.STATE_BETTING || game.getState() == Game.STATE_DEALING) &&
						(bet = game.getPlayerBet(i)) != null) {
					Card card = new Card(bet.getSuit(), game.getCurrentLevel());
					
					for(int j = 0; j < bet.getAmount(); j++) {
						drawCards.add(card);
					}
				}
				
				//NOW draw cards, if any
				int leftShift = 20 + CARD_MEDIUM_WIDTH / 2 * drawCards.size();
				
				for(int j = 0; j < drawCards.size(); j++) {
					Card card = drawCards.get(j);
					Image image = resources.getImage("card_" + card.getId());
					g.drawImage(image, circleX - leftShift + CARD_MEDIUM_WIDTH * j, circleY - CARD_HEIGHT / 2, this);
				}
			}
		
			int timer = -1;
			
			//if there's a timer in progress, draw it
			if(game.getState() == Game.STATE_BETTING) {
				timer = game.getBetCounter();
			} else if(game.getState() == Game.STATE_ROUNDOVER) {
				timer = game.getRoundOverCounter();
			}
			
			if(timer != -1) {
				int timerX = playerCenterX - 30;
				int timerY = playerCenterY - 20;
				
				g.setFont(resources.getFont("timer"));
				g.setColor(Color.BLUE);
				
				g.drawString(timer + "", timerX, timerY);
			}
		}
		
		//restore saved transform
		g.setTransform(savedTransform);
		
		long millisEnd = System.currentTimeMillis();
		LevelUp.debug("[GamePanel] Update took " + (millisEnd - millisStart) + " ms");
	}
	
	public boolean updateImage(Image img, int infoflags, int x, int y, int width, int height) {
		if((infoflags & ImageObserver.ALLBITS) == ImageObserver.ALLBITS) {
			//repaint the screen so that the image can be shown
			view.frame.gameUpdated();
			
			return false;
		}
		
		return true;
	}
}