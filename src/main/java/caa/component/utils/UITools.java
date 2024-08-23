package caa.component.utils;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

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

    public static void addButtonListener(JButton pasteButton, JButton removeButton, JButton clearButton, JTable table, DefaultTableModel model, BiConsumer<String, DefaultTableModel> addDataToTable) {
        pasteButton.addActionListener(e -> {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            try {
                String data = (String) clipboard.getData(DataFlavor.stringFlavor);

                if (data != null && !data.isEmpty()) {
                    addDataToTable.accept(data, model);
                }
            } catch (Exception ignored) {
            }
        });

        removeButton.addActionListener(e -> {
            int selectedRow = table.getSelectedRow();
            if (selectedRow != -1) {
                model.removeRow(selectedRow);
            }
        });

        clearButton.addActionListener(e -> model.setRowCount(0));
    }

    public static void deduplicateTableData(DefaultTableModel model) {
        // 使用 Map 存储每一行的数据，用于去重
        Set<java.util.List<Object>> rowData = new LinkedHashSet<>();

        int columnCount = model.getColumnCount();

        // 将每一行数据作为一个列表，添加到 Set 中
        for (int i = 0; i < model.getRowCount(); i++) {
            java.util.List<Object> row = new ArrayList<>();
            for (int j = 0; j < columnCount; j++) {
                row.add(model.getValueAt(i, j));
            }
            rowData.add(row);
        }

        // 清除原始数据
        model.setRowCount(0);

        // 将去重后的数据添加回去
        for (List<Object> uniqueRow : rowData) {
            model.addRow(uniqueRow.toArray());
        }
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
