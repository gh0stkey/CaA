package caa.component.utils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.net.URL;

public class UITools {
    public static ImageIcon getImageIcon(boolean isDark, String filename, int width, int height, int imageStyle) {
        URL imageURL = null;
        ClassLoader classLoader = UITools.class.getClassLoader();
        String iconFileName = String.format("%s%s.png", filename, isDark ? "" : "_black");
        imageURL = classLoader.getResource(iconFileName);
        ImageIcon originalIcon = new ImageIcon(imageURL);
        Image originalImage = originalIcon.getImage();
        Image scaledImage = originalImage.getScaledInstance(width, height, imageStyle);
        ImageIcon scaledIcon = new ImageIcon(scaledImage);
        return scaledIcon;
    }

    public static void addPlaceholder(JTextField textField, String placeholderText) {
        textField.setForeground(Color.GRAY);
        textField.setText(placeholderText);
        textField.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                if (textField.getText().equals(placeholderText)) {
                    textField.setText("");
                    textField.setForeground(Color.BLACK);
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (textField.getText().isEmpty()) {
                    textField.setForeground(Color.GRAY);
                    textField.setText(placeholderText);
                }
            }
        });
    }
}
