package com.perennate.games.levelup.uglyview;

import javax.swing.BoxLayout;
import javax.swing.JPanel;

public class UglyPanel extends JPanel {
	UglyView view;
	
	GamePanel gamePanel;
	GameButtonsPanel buttonsPanel;

	public UglyPanel(UglyView view) {
		super();
		this.view = view;

		gamePanel = new GamePanel(view, view.getGame());
		buttonsPanel = new GameButtonsPanel(view, view.getGame(), gamePanel.cardSelector);

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		add(gamePanel);
		add(buttonsPanel);
	}
}