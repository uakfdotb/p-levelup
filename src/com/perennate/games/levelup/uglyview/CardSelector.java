package com.perennate.games.levelup.uglyview;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.perennate.games.levelup.engine.Card;


public class CardSelector implements MouseListener {
	UglyView view;
	GamePanel panel;
	
	Set<Integer> selectedCards;
	List<Card> selection;
	
	public CardSelector(UglyView view, GamePanel panel) {
		this.view = view;
		this.panel = panel;
		
		selectedCards = new HashSet<Integer>();
		selection = new ArrayList<Card>();
	}
	
	public void clearSelection() {
		synchronized(selectedCards) {
			selectedCards.clear();
			selection.clear();
		}
	}
	
	public boolean isSelected(Card card) {
		synchronized(selectedCards) {
			return selectedCards.contains(card.getUID());
		}
	}
	
	public List<Card> getSelection() {
		synchronized(selectedCards) {
			return selection;
		}
	}
	
	public void mousePressed(MouseEvent e) {
		//check if user clicked a card, and select it if so
		//need synchronized in case panel updates current cards at the same time
		synchronized(panel.currentCards) {
			//loop through placements in reverse
			for(int i = panel.currentCards.size() - 1; i >= 0; i--) {
				CardPlacement placement = panel.currentCards.get(i);
				
				if(e.getX() > placement.x && e.getX() < placement.x + placement.width &&
						e.getY() > placement.y && e.getY() < placement.y + placement.height) {
					int uid = placement.card.getUID();
					
					synchronized(selectedCards) {
						if(selectedCards.contains(uid)) {
							selectedCards.remove(uid);
							
							for(int j = 0; j < selection.size(); j++) {
								if(selection.get(j).getUID() == uid) {
									selection.remove(j);
								}
							}
						} else {
							selectedCards.add(uid);
							selection.add(placement.card);
						}
					}
					
					//don't update any more cards, only select one at a time
					break;
				}
			}
		}
		
		panel.repaint();
	}
	
	//functions from listener that we don't care about
	
	public void mouseClicked(MouseEvent e) {
		
	}
	
	public void mouseReleased(MouseEvent e) {
		
	}
	
	public void mouseEntered(MouseEvent e) {
		
	}
	
	public void mouseExited(MouseEvent e) {
		
	}
}