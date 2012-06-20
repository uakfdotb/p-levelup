package com.perennate.games.levelup.uglyview;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

public class ChatPanel extends JPanel implements ActionListener {
	UglyView view;
	
    JTextPane chatLog;
    JTextField submitText;
    JScrollPane scrollPane;
    
    SimpleAttributeSet nameAttribute;
    SimpleAttributeSet messageAttribute;
    
    public ChatPanel(UglyView view) {
    	super();
    	this.view = view;

    	chatLog = new JTextPane();
        chatLog.setPreferredSize(new Dimension(chatLog.getPreferredSize().width, 150));
        chatLog.setEditable(false);
        
        submitText = new JTextField();
        submitText.addActionListener(this);
        
        nameAttribute = new SimpleAttributeSet();
        StyleConstants.setBold(nameAttribute, true);
        
        messageAttribute = new SimpleAttributeSet();
    	
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		
		scrollPane = new JScrollPane(chatLog);
		scrollPane.setPreferredSize(new Dimension(chatLog.getPreferredSize().width, 150));
		add(scrollPane);
		add(submitText);
    }
    
    public void append(String name, String message) {
    	StyledDocument document = chatLog.getStyledDocument();
    	
    	try {
    		document.insertString(document.getLength(), name, nameAttribute);
    		document.insertString(document.getLength(), ": " + message + "\n", messageAttribute);
    	} catch(BadLocationException e) {
    		chatLog.setText(chatLog.getText() + name + ": " + message + "\n");
    	}
    	
    	chatLog.setCaretPosition(document.getLength());
    }
    
    public void actionPerformed(ActionEvent e) {
    	if(e.getSource() == submitText) {
    		String str = submitText.getText().trim();
    		
    		if(!str.isEmpty()) {
    			view.getClient().sendChat(str);
    		}
    		
    		submitText.setText("");
    	}
    }
}
