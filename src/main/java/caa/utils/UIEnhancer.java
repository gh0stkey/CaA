package caa.utils;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class UIEnhancer {
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

    public static void setTextFieldPlaceholder(JTextField textField, String placeholderText) {
        // 存储占位符文本
        textField.putClientProperty("placeholderText", placeholderText);
        textField.putClientProperty("isPlaceholder", true);

        updatePlaceholderText(textField);

        textField.addPropertyChangeListener("background", evt -> {
            updateForeground(textField);
        });

        textField.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                if (Boolean.TRUE.equals(textField.getClientProperty("isPlaceholder"))) {
                    textField.putClientProperty("isPlaceholder", false);
                    updateForeground(textField);

                    textField.setText("");
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (textField.getText().isEmpty()) {
                    updatePlaceholderText(textField);
                }
            }
        });

        textField.addPropertyChangeListener("text", evt -> {
            if (Boolean.TRUE.equals(textField.getClientProperty("isPlaceholder"))) {
                if (!textField.getText().isEmpty()) {
                    textField.putClientProperty("isPlaceholder", false);
                    updateForeground(textField);
                }
            } else {
                if (textField.getText().isEmpty()) {
                    updatePlaceholderText(textField);
                }
            }
        });
    }

    private static void updatePlaceholderText(JTextField textField) {
        String placeholderText = (String) textField.getClientProperty("placeholderText");
        textField.putClientProperty("isPlaceholder", true);
        textField.setText(placeholderText);
        textField.setForeground(Color.GRAY);
    }

    private static void updateForeground(JTextField textField) {
        Color bg = textField.getBackground();
        Color fg = isDarkColor(bg) ? Color.WHITE : Color.BLACK;

        if (!Boolean.TRUE.equals(textField.getClientProperty("isPlaceholder"))) {
            textField.setForeground(fg);
            textField.putClientProperty("isPlaceholder", false);
        }
    }

    public static boolean isDarkColor(Color color) {
        double brightness = 0.299 * color.getRed()
                + 0.587 * color.getGreen()
                + 0.114 * color.getBlue();
        return brightness < 128;
    }

    public static boolean hasUserInput(JTextField field) {
        Object prop = field.getClientProperty("isPlaceholder");
        return prop instanceof Boolean && !((Boolean) prop);
    }

    public static void addSimpleDocumentListener(JTextField field, Runnable action) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                action.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                action.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                action.run();
            }
        });
    }

    public static void addDataToTable(String data, DefaultTableModel model, boolean parseKeyValue, Function<String, String> valueDecoder) {
        if (data.isBlank()) {
            return;
        }

        String[] rows = data.split("\\r?\\n");
        for (String row : rows) {
            String[] cellData;
            if (parseKeyValue && row.contains("=")) {
                cellData = new String[]{row.split("=")[0], valueDecoder.apply(row.split("=")[1])};
            } else {
                cellData = new String[]{row};
            }
            model.addRow(cellData);
        }

        deduplicateTableData(model);
    }
}
