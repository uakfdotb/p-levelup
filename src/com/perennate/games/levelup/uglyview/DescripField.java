package com.perennate.games.levelup.uglyview;

import javax.swing.*;

public class DescripField extends JPanel {
    public JLabel descrip;
    public JTextField field;

    public DescripField(String desc, String f) {
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        descrip = new JLabel(desc);
        descrip.setAlignmentX(JLabel.TOP_ALIGNMENT);
        field = new JTextField(f);
        field.setAlignmentX(JLabel.TOP_ALIGNMENT);

        add(descrip);
        add(field);
    }

    public DescripField(String desc, String f, int fsize) {
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        descrip = new JLabel(desc);
        descrip.setAlignmentX(JLabel.TOP_ALIGNMENT);
        field = new JTextField(f, fsize);
        field.setAlignmentX(JLabel.TOP_ALIGNMENT);

        add(descrip);
        add(field);
    }

    public DescripField(JLabel descrip, JTextField field) {
        this.descrip = descrip;
        this.field = field;
        add(descrip);
        add(field);
    }
    
    public String getField() {
        return field.getText();
    }
    
    public void setField(String s) {
        field.setText(s);
    }
}