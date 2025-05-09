package caa.utils;

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
        // 使用客户端属性来存储占位符文本和占位符状态
        textField.putClientProperty("placeholderText", placeholderText);
        textField.putClientProperty("isPlaceholder", true);

        // 设置占位符文本和颜色
        setPlaceholderText(textField);

        textField.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                // 当获得焦点且文本是占位符时，清除文本并更改颜色
                if ((boolean) textField.getClientProperty("isPlaceholder")) {
                    textField.setText("");
                    textField.setForeground(Color.BLACK);
                    textField.putClientProperty("isPlaceholder", false);
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                // 当失去焦点且文本为空时，设置占位符文本和颜色
                if (textField.getText().isEmpty()) {
                    setPlaceholderText(textField);
                }
            }
        });
    }

    private static void setPlaceholderText(JTextField textField) {
        String placeholderText = (String) textField.getClientProperty("placeholderText");
        textField.setForeground(Color.GRAY);
        textField.setText(placeholderText);
        textField.putClientProperty("isPlaceholder", true);
    }
}
