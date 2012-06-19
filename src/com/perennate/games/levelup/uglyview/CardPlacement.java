package com.perennate.games.levelup.uglyview;

import com.perennate.games.levelup.engine.Card;

public class CardPlacement {
	Card card;
	int x;
	int y;
	int width;
	int height;
	
	public CardPlacement(Card card, int x, int y, int width, int height) {
		this.card = card;
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}
}
