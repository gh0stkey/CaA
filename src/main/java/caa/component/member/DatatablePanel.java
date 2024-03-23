package caa.component.member;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;

import caa.component.utils.UITools;
import caa.component.member.taskboard.MessageTableModel;
import caa.instances.Database;
import com.google.common.collect.SetMultimap;
import jregex.Pattern;
import jregex.REFlags;

import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;

public class DatatablePanel extends JPanel {
    private final JTable dataTable;
    private final DefaultTableModel dataTableModel;
    private final JTextField searchField;
    private final TableRowSorter<DefaultTableModel> sorter;
    private final MontoyaApi api;
    private final JCheckBox searchMode = new JCheckBox("Reverse search");
    private final String tabName;
    private final Object dataObj;
    private final int columnSize;
    private final HttpRequest httpRequest;
    private final MessageTableModel messageTableModel;
    private final Database db;

    public DatatablePanel(MontoyaApi api, Database db, MessageTableModel messageTableModel, List<String> columnNameList, Object dataObj, HttpRequest httpRequest, String tabName) {
        this.api = api;
        this.db = db;
        this.messageTableModel = messageTableModel;
        this.tabName = tabName.replace("All ", "");
        this.dataObj = dataObj;
        this.httpRequest = httpRequest;
        this.columnSize = columnNameList.size();

        String[] columnNames = new String[columnSize + 1];
        columnNames[0] = "#";
        for (int i = 1; i <= columnSize; i++) {
            columnNames[i] = columnNameList.get(i - 1);
        }

        dataTableModel = new DefaultTableModel(columnNames, 0);
        dataTable = new JTable(dataTableModel);
        sorter = new TableRowSorter<>(dataTableModel);

        searchField = new JTextField();

        initComponents();
    }

    private void initComponents() {
        // 设置ID排序
        sorter.setComparator(0, new Comparator<Integer>() {
            @Override
            public int compare(Integer s1, Integer s2) {
                return s1.compareTo(s2);
            }
        });

        dataTable.setRowSorter(sorter);
        TableColumn idColumn = dataTable.getColumnModel().getColumn(0);
        idColumn.setMaxWidth(50);
        if (columnSize > 1) {
            ((SetMultimap<String, String>) dataObj).forEach((k, v) -> {
                addRowToTable(new Object[]{k, v});
            });
        } else {
            for (String d : (HashSet<String>) dataObj) {
                addRowToTable(new Object[]{d});
            }
        }

        // 设置灰色默认文本
        String searchText = "Search";
        UITools.addPlaceholder(searchField, searchText);

        // 监听输入框内容输入、更新、删除
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                performSearch();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                performSearch();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                performSearch();
            }

        });

        // 设置布局
        JScrollPane scrollPane = new JScrollPane(dataTable);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

        searchMode.addItemListener(e -> performSearch());

        setLayout(new BorderLayout(0, 5));

        JPanel optionsPanel = new JPanel();
        optionsPanel.setBorder(BorderFactory.createEmptyBorder(2, 3, 5, 5));
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.X_AXIS));

        // 新增复选框要在这修改rows
        JPanel menuPanel = new JPanel(new GridLayout(1, 1));
        menuPanel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
        JPopupMenu menu = new JPopupMenu();
        menuPanel.add(searchMode);
        menu.add(menuPanel);

        JButton settingsButton = new JButton("Settings");
        settingsButton.addActionListener(e -> {
            int x = settingsButton.getX();
            int y = settingsButton.getY() - menu.getPreferredSize().height;
            menu.show(settingsButton, x, y);
        });

        optionsPanel.add(settingsButton);
        optionsPanel.add(Box.createHorizontalStrut(5));
        optionsPanel.add(searchField);

        // Fuzzer 快捷方式
        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem sendToFuzzerMenuItem = new JMenuItem("Send to Fuzzer");
        popupMenu.add(sendToFuzzerMenuItem);
        sendToFuzzerMenuItem.addActionListener(e -> {
            String payload = getSelectedData(dataTable);
            if (httpRequest != null) {
                new FuzzerDialog(api, db, messageTableModel, httpRequest.toString().replaceAll("\r\n", "\n"), httpRequest.httpService().secure(), tabName, payload);
            } else {
                new FuzzerDialog(api, db, messageTableModel, "", false, tabName, payload);
            }
        });

        dataTable.addMouseListener(new MouseAdapter() {
            public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    popupMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });

        dataTable.setTransferHandler(new TransferHandler() {
            @Override
            public void exportToClipboard(JComponent comp, Clipboard clip, int action) throws IllegalStateException {
                if (comp instanceof JTable) {
                    StringSelection stringSelection = new StringSelection(getSelectedData(
                            (JTable) comp));
                    clip.setContents(stringSelection, null);
                } else {
                    super.exportToClipboard(comp, clip, action);
                }
            }
        });

        add(scrollPane, BorderLayout.CENTER);
        add(optionsPanel, BorderLayout.SOUTH);
    }

    private void addRowToTable(Object[] data) {
        int rowCount = dataTableModel.getRowCount();
        int id = rowCount > 0 ? (Integer) dataTableModel.getValueAt(rowCount - 1, 0) + 1 : 1;
        Object[] rowData = new Object[columnSize + 1];
        rowData[0] = id;
        System.arraycopy(data, 0, rowData, 1, columnSize);
        dataTableModel.addRow(rowData);
    }

    private void performSearch() {
        if (searchField.getForeground().equals(Color.BLACK)) {
            RowFilter<Object, Object> rowFilter = new RowFilter<Object, Object>() {
                public boolean include(Entry<?, ?> entry) {
                    String searchFieldTextText = searchField.getText();
                    Pattern pattern = null;
                    try {
                        pattern = new Pattern(searchFieldTextText, REFlags.IGNORE_CASE);
                    } catch (Exception ignored) {
                    }

                    String entryValue = ((String) entry.getValue(1)).toLowerCase();
                    searchFieldTextText = searchFieldTextText.toLowerCase();
                    if (pattern != null) {
                        return searchFieldTextText.isEmpty() || pattern.matcher(entryValue).find() != searchMode.isSelected();
                    } else {
                        return searchFieldTextText.isEmpty() || entryValue.contains(searchFieldTextText) != searchMode.isSelected();
                    }
                }
            };
            sorter.setRowFilter(rowFilter);
        }
    }

    public static String getSelectedData(JTable table) {
        int[] selectRows = table.getSelectedRows();
        StringBuilder selectData = new StringBuilder();
        for (int row : selectRows) {
            int columnCount = table.getColumnCount();
            switch (columnCount) {
                case 2 -> selectData.append(table.getValueAt(row, 1).toString()).append("\n");
                case 3 ->
                        selectData.append(String.format("%s\t%s", table.getValueAt(row, 1).toString(), table.getValueAt(row, 2).toString())).append("\n");
            }
        }

        // 便于单行复制，去除最后一个换行符
        if (selectData.length() > 0){
            selectData.deleteCharAt(selectData.length() - 1);
        }

        return selectData.toString();
    }

    public JTable getDataTable() {
        return this.dataTable;
    }
}
