package com.perennate.games.levelup.uglyview;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;

import com.perennate.games.levelup.LevelUp;
import com.perennate.games.levelup.engine.Bet;
import com.perennate.games.levelup.engine.Card;
import com.perennate.games.levelup.engine.Game;

public class GameButtonsPanel extends JPanel implements ActionListener {
	UglyView view;
	Game game;
	CardSelector cardSelector;
	
	JButton declareButton;
	JButton defendButton;
	JButton withdrawButton;
	
	JButton bottomButton;
	JButton playButton;
	
	JButton clearButton;
	
	List<String> previousButtons;

	public GameButtonsPanel(UglyView view, Game game, CardSelector cardSelector) {
		super();
		this.view = view;
		this.game = game;
		this.cardSelector = cardSelector;
		
		previousButtons = new ArrayList<String>();

		declareButton = new JButton("Declare");
		declareButton.addActionListener(this);
		defendButton = new JButton("Defend");
		defendButton.addActionListener(this);
		withdrawButton = new JButton("Withdraw");
		withdrawButton.addActionListener(this);
		
		bottomButton = new JButton("Select bottom");
		bottomButton.addActionListener(this);
		playButton = new JButton("Play");
		playButton.addActionListener(this);
		
		clearButton = new JButton("Clear selection");
		clearButton.addActionListener(this);

		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
	}
	
	//reselect which buttons to show
	public synchronized void updateButtons() {
		List<String> newButtons = new ArrayList<String>();
		
		if(game.getState() == Game.STATE_DEALING || game.getState() == Game.STATE_BETTING) {
			Bet bet = game.getPlayerBet(view.getPlayer());
			
			if(bet == null) {
				newButtons.add("declare");
			} else {
				newButtons.add("defend");
				newButtons.add("withdraw");
			}
		} else if(game.getState() == Game.STATE_BOTTOM) {
			if(view.getPlayer() == game.getCurrentDealer()) {
				newButtons.add("bottom");
			}
		} else if(game.getState() == Game.STATE_PLAYING) {
			if(view.getPlayer() == game.getNextPlayer()) {
				newButtons.add("play");
			}
		}
		
		newButtons.add("clear");
		
		if(!previousButtons.equals(newButtons)) {
			removeAll();
			
			for(String str : newButtons) {
				if(str.equals("declare")) add(declareButton);
				else if(str.equals("defend")) add(defendButton);
				else if(str.equals("withdraw")) add(withdrawButton);
				else if(str.equals("bottom")) add(bottomButton);
				else if(str.equals("play")) add(playButton);
				else if(str.equals("clear")) add(clearButton);
				else LevelUp.println("[GameButtonsPanel] Warning: invalid button name: " + str);
			}
			
			previousButtons = newButtons;
		}
		
		LevelUp.debug("[GameButtonsPanel] Updated buttons: " + previousButtons);
		revalidate();
	}

	public void actionPerformed(ActionEvent e) {
		String action = "";
		
		if(e.getSource() == declareButton) {
			action = "declare";
		} else if(e.getSource() == defendButton) {
			action = "defend";
		} else if(e.getSource() == withdrawButton) {
			action = "withdraw";
		} else if(e.getSource() == bottomButton) {
			action = "bottom";
		} else if(e.getSource() == playButton) {
			action = "play";
		} else if(e.getSource() == clearButton) {
			action = "clear";
		}
		
		if(!action.isEmpty()) {
			ButtonThreadedAction threadedAction = new ButtonThreadedAction(action, view, cardSelector);
			threadedAction.start();
		}
	}
}

class ButtonThreadedAction extends Thread {
	String action;
	UglyView view;
	CardSelector cardSelector;
	
	public ButtonThreadedAction(String action, UglyView view, CardSelector cardSelector) {
		this.action = action;
		this.view = view;
		this.cardSelector = cardSelector;
	}
	
	public void run() {
		List<Card> cards = cardSelector.getSelection();
		
		if(cards.isEmpty()) {
			view.eventGameError("You have not selected any cards!");
			return;
		}
		
		if(action.equals("declare")) {
			//make sure all the same card
			Card first = cards.get(0);
			
			for(int i = 1; i < cards.size(); i++) {
				if(!cards.get(i).equals(first)) {
					view.eventGameError("Declared cards must all be the same.");
					return;
				}
			}
			
			int suit = first.getSuit();
			int amount = cards.size();
			
			view.getClient().sendDeclare(suit,  amount);
		} else if(action.equals("defend")) {
			//make sure all the same card
			Card first = cards.get(0);
			
			for(int i = 1; i < cards.size(); i++) {
				if(!cards.get(i).equals(first)) {
					view.eventGameError("Defend cards must all be the same.");
					return;
				}
			}
			
			int amount = cards.size();
			
			view.getClient().sendDefendDeclaration(amount);
		} else if(action.equals("withdraw")) {
			view.getClient().sendWithdrawDeclaration();
		} else if(action.equals("bottom")) {
			view.getClient().sendSelectBottom(cards);
		} else if(action.equals("play")) {
			//each card is selected once, so create amounts accordingly
			List<Integer> amounts = new ArrayList<Integer>();
			
			for(int i = 0; i < cards.size(); i++) {
				amounts.add(1);
			}
			view.getClient().sendPlayCards(cards, amounts);
		}
		
		cardSelector.clearSelection();
	}
}