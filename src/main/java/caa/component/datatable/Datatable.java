package caa.component.datatable;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import caa.component.generator.Generator;
import caa.instances.Database;
import caa.instances.payload.PayloadGenerator;
import caa.utils.UIEnhancer;
import com.google.common.collect.SetMultimap;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

public class Datatable extends JPanel {
    private final JTable dataTable;
    private final DefaultTableModel dataTableModel;
    private final JTextField searchField;
    private final JTextField secondSearchField;
    private final TableRowSorter<DefaultTableModel> sorter;
    private final MontoyaApi api;
    private final Generator generator;
    private final JCheckBox searchMode = new JCheckBox("Reverse search");
    private final JCheckBox regexMode = new JCheckBox("Regex mode");
    private final String tabName;
    private final Object dataObj;
    private final int columnSize;
    private final HttpRequest httpRequest;
    private final Database db;
    private final PayloadGenerator payloadGenerator;
    private final Mode mode;
    private String currentHost = "";

    public Datatable(DatatableContext ctx, List<String> columnNameList, Object dataObj, String tabName, Mode mode) {
        this.api = ctx.api();
        this.db = ctx.db();
        this.generator = ctx.generator();
        this.httpRequest = ctx.httpRequest();
        this.tabName = tabName;
        this.dataObj = dataObj;
        this.columnSize = columnNameList.size();
        this.payloadGenerator = new PayloadGenerator(ctx.api(), ctx.configLoader());
        this.mode = mode;

        String[] columnNames = new String[columnSize + 1];
        columnNames[0] = "#";
        for (int i = 1; i <= columnSize; i++) {
            columnNames[i] = columnNameList.get(i - 1);
        }

        dataTableModel = new DefaultTableModel(columnNames, 0);
        dataTable = new JTable(dataTableModel);
        sorter = new TableRowSorter<>(dataTableModel);

        searchField = new JTextField(10);
        secondSearchField = new JTextField(10);

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

        TableColumn idColumn = dataTable.getColumnModel().getColumn(0);
        idColumn.setPreferredWidth(50);
        idColumn.setMaxWidth(100);

        if (mode == Mode.COUNT) {
            TableColumn countColumn = dataTable.getColumnModel().getColumn(columnSize);
            countColumn.setPreferredWidth(100);
            countColumn.setMaxWidth(300);

            sorter.setComparator(columnSize, new Comparator<Integer>() {
                @Override
                public int compare(Integer s1, Integer s2) {
                    return s1.compareTo(s2);
                }
            });
        }

        dataTable.setRowSorter(sorter);
        populateTableData();

        UIEnhancer.setTextFieldPlaceholder(searchField, "Search");

        UIEnhancer.addSimpleDocumentListener(searchField, this::performSearch);

        UIEnhancer.setTextFieldPlaceholder(secondSearchField, "Second search");

        UIEnhancer.addSimpleDocumentListener(secondSearchField, this::performSearch);

        // 设置布局
        JScrollPane scrollPane = new JScrollPane(dataTable);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

        searchMode.addItemListener(e -> performSearch());

        setLayout(new BorderLayout(0, 5));

        JPanel optionsPanel = new JPanel();
        optionsPanel.setBorder(BorderFactory.createEmptyBorder(2, 3, 5, 5));
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.X_AXIS));

        // 新增复选框要在这修改rows
        JPanel settingMenuPanel = new JPanel(new GridLayout(2, 1));
        settingMenuPanel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
        JPopupMenu menu = new JPopupMenu();
        settingMenuPanel.add(searchMode);
        settingMenuPanel.add(regexMode);
        regexMode.setSelected(true);
        searchMode.addItemListener(e -> performSearch());
        menu.add(settingMenuPanel);

        JButton settingsButton = new JButton("Settings");
        settingsButton.addActionListener(e -> {
            Point buttonLocation = settingsButton.getLocationOnScreen();
            Dimension menuSize = menu.getPreferredSize();
            int x = buttonLocation.x + (settingsButton.getWidth() - menuSize.width) / 2;
            int y = buttonLocation.y - menuSize.height;
            menu.show(settingsButton, x - buttonLocation.x, y - buttonLocation.y);
        });

        optionsPanel.add(settingsButton);
        optionsPanel.add(Box.createHorizontalStrut(5));
        optionsPanel.add(searchField);
        optionsPanel.add(Box.createHorizontalStrut(5));
        optionsPanel.add(secondSearchField);

        // 右键快捷方式
        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem sendToGenerator = new JMenuItem("Send to Generator");
        JMenu copyMenu = new JMenu("Copy Payload");
        JMenu rawMenu = new JMenu("Raw");
        JMenuItem rawWithParamCopy = new JMenuItem("Raw with Param");
        JMenuItem rawWithCookieCopy = new JMenuItem("Raw with Cookie");
        JMenuItem rawWithHeaderCopy = new JMenuItem("Raw with Header");
        JMenuItem jsonCopy = new JMenuItem("Json");
        JMenuItem xmlCopy = new JMenuItem("Xml");
        copyMenu.add(rawMenu);
        rawMenu.add(rawWithParamCopy);
        rawMenu.add(rawWithCookieCopy);
        rawMenu.add(rawWithHeaderCopy);
        copyMenu.add(jsonCopy);
        copyMenu.add(xmlCopy);

        rawWithParamCopy.addActionListener(e -> {
            String payload = getSelectedDataAtTable(dataTable);
            setClipboardContents(payloadGenerator.generateRawParam(payload, "=", "&"));
        });

        rawWithCookieCopy.addActionListener(e -> {
            String payload = getSelectedDataAtTable(dataTable);
            setClipboardContents(payloadGenerator.generateRawParam(payload, "=", "; "));
        });

        rawWithHeaderCopy.addActionListener(e -> {
            String payload = getSelectedDataAtTable(dataTable);
            setClipboardContents(payloadGenerator.generateRawParam(payload, ": ", "\r\n"));
        });

        jsonCopy.addActionListener(e -> {
            String payload = getSelectedDataAtTable(dataTable);
            setClipboardContents(payloadGenerator.generateJsonParam(payload));
        });

        xmlCopy.addActionListener(e -> {
            String payload = getSelectedDataAtTable(dataTable);
            setClipboardContents(payloadGenerator.generateXmlParam(payload));
        });

        sendToGenerator.addActionListener(e -> {
            String payloads = getSelectedDataAtTable(dataTable);
            generator.insertNewTab(httpRequest, tabName, payloads);
        });

        // 添加删除菜单项（仅在COUNT模式下，即Databoard中显示）
        JMenuItem deleteItem = new JMenuItem("Delete Selected");
        deleteItem.addActionListener(e -> {
            int[] selectedRows = dataTable.getSelectedRows();
            if (selectedRows.length == 0) {
                JOptionPane.showMessageDialog(this, "Please select data to delete", "Warning", JOptionPane.WARNING_MESSAGE);
                return;
            }

            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "Are you sure you want to delete " + selectedRows.length + " selected item(s)?",
                    "Confirm Delete",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );

            if (confirm == JOptionPane.YES_OPTION) {
                deleteSelectedRows(selectedRows);
            }
        });

        dataTable.addMouseListener(new MouseAdapter() {
            public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    popupMenu.removeAll();

                    if (tabName.contains("Param") || tabName.equals("Value")) {
                        popupMenu.add(copyMenu);
                    }

                    if (httpRequest != null) {
                        popupMenu.add(sendToGenerator);
                    }

                    // 只在COUNT模式下（Databoard）添加删除菜单
                    if (mode == Mode.COUNT) {
                        if (popupMenu.getComponentCount() > 0) {
                            popupMenu.addSeparator();
                        }
                        popupMenu.add(deleteItem);
                    }

                    popupMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });

        dataTable.setTransferHandler(new TransferHandler() {
            @Override
            public void exportToClipboard(JComponent comp, Clipboard clip, int action) throws IllegalStateException {
                if (comp instanceof JTable) {
                    StringSelection stringSelection = new StringSelection(getSelectedDataAtTable((JTable) comp).replace("\0", "").replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", ""));
                    clip.setContents(stringSelection, null);
                } else {
                    super.exportToClipboard(comp, clip, action);
                }
            }
        });

        add(scrollPane, BorderLayout.CENTER);
        add(optionsPanel, BorderLayout.SOUTH);
    }

    private void setClipboardContents(String contents) {
        StringSelection stringSelection = new StringSelection(contents);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, null);
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
        List<RowFilter<Object, Object>> filters = new ArrayList<>();

        if (UIEnhancer.hasUserInput(searchField)) {
            filters.add(getObjectObjectRowFilter(searchField, true));
        }

        if (UIEnhancer.hasUserInput(secondSearchField)) {
            filters.add(getObjectObjectRowFilter(secondSearchField, false));
        }

        sorter.setRowFilter(filters.isEmpty() ? null : RowFilter.andFilter(filters));
    }

    private RowFilter<Object, Object> getObjectObjectRowFilter(JTextField searchField, boolean firstFlag) {
        return new RowFilter<>() {
            public boolean include(Entry<?, ?> entry) {
                String searchFieldTextText = searchField.getText();
                searchFieldTextText = searchFieldTextText.toLowerCase();
                String entryValue = ((String) entry.getValue(1)).toLowerCase();
                boolean filterReturn = searchFieldTextText.isEmpty();
                boolean firstFlagReturn = searchMode.isSelected() && firstFlag;
                if (regexMode.isSelected()) {
                    Pattern pattern = null;
                    try {
                        pattern = Pattern.compile(searchFieldTextText, Pattern.CASE_INSENSITIVE);
                    } catch (Exception ignored) {
                    }

                    if (pattern != null) {
                        filterReturn = filterReturn || pattern.matcher(entryValue).find() != firstFlagReturn;
                    }
                } else {
                    filterReturn = filterReturn || entryValue.contains(searchFieldTextText) != firstFlagReturn;
                }

                return filterReturn;
            }
        };
    }

    private void populateTableData() {
        if (mode == Mode.COUNT) {
            if (columnSize > 2) {
                ((SetMultimap<String, String>) dataObj).forEach((k, v) -> {
                    // 解析组合的值
                    String[] parts = v.split("\\|", 2);
                    String value = parts[1];
                    int count = Integer.parseInt(parts[0]);

                    addRowToTable(new Object[]{k, value, count});
                });
            } else {
                Map<String, Integer> resultMap = (Map<String, Integer>) dataObj;
                resultMap.forEach((key, count) -> {
                    addRowToTable(new Object[]{key, count});
                });
            }
        } else {
            if (columnSize > 1) {
                ((SetMultimap<String, String>) dataObj).forEach((k, v) -> {
                    addRowToTable(new Object[]{k, v});
                });
            } else {
                for (String d : (HashSet<String>) dataObj) {
                    addRowToTable(new Object[]{d});
                }
            }
        }
    }

    private void deleteSelectedRows(int[] selectedRows) {
        SwingWorker<Integer, Void> worker = new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() {
                List<Map<String, String>> dataToDelete = new ArrayList<>();

                // 收集要删除的数据
                for (int row : selectedRows) {
                    int modelRow = dataTable.convertRowIndexToModel(row);
                    Map<String, String> data = new HashMap<>();

                    // 获取name值（第1列）
                    Object nameObj = dataTableModel.getValueAt(modelRow, 1);
                    if (nameObj != null) {
                        data.put("name", nameObj.toString());
                    }

                    // 如果是Value表，获取value值（第2列）
                    if (tabName.equals("Value") && columnSize > 2) {
                        Object valueObj = dataTableModel.getValueAt(modelRow, 2);
                        if (valueObj != null) {
                            data.put("value", valueObj.toString());
                        }
                    }

                    dataToDelete.add(data);
                }
                // 批量删除
                return db.batchDeleteData(currentHost, tabName, dataToDelete);
            }

            @Override
            protected void done() {
                try {
                    int deletedCount = get();

                    if (deletedCount > 0) {
                        // 从表格中移除已删除的行
                        Arrays.sort(selectedRows);
                        for (int i = selectedRows.length - 1; i >= 0; i--) {
                            int modelRow = dataTable.convertRowIndexToModel(selectedRows[i]);
                            dataTableModel.removeRow(modelRow);
                        }

                        // 更新行号
                        updateRowNumbers();

                        JOptionPane.showMessageDialog(
                                Datatable.this,
                                "Successfully deleted " + deletedCount + " item(s)",
                                "Success",
                                JOptionPane.INFORMATION_MESSAGE
                        );
                    } else {
                        JOptionPane.showMessageDialog(
                                Datatable.this,
                                "Failed to delete data",
                                "Error",
                                JOptionPane.ERROR_MESSAGE
                        );
                    }
                } catch (Exception ex) {
                    api.logging().logToError("Failed to delete data: " + ex.getMessage());
                    JOptionPane.showMessageDialog(
                            Datatable.this,
                            "Error: " + ex.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        };

        worker.execute();
    }

    private void updateRowNumbers() {
        for (int i = 0; i < dataTableModel.getRowCount(); i++) {
            dataTableModel.setValueAt(i + 1, i, 0);
        }
    }

    public void setCurrentHost(String host) {
        this.currentHost = host;
    }

    public String getSelectedDataAtTable(JTable table) {
        int[] selectRows = table.getSelectedRows();
        StringBuilder selectData = new StringBuilder();

        for (int row : selectRows) {
            int columnCount = table.getColumnCount();
            if (mode == Mode.COUNT) {
                switch (columnCount) {
                    case 3 -> selectData.append(table.getValueAt(row, 1).toString()).append("\r\n");
                    case 4 ->
                            selectData.append(String.format("%s=%s", table.getValueAt(row, 1).toString(), encodeParameter(table.getValueAt(row, 2).toString()))).append("\r\n");
                }
            } else {
                switch (columnCount) {
                    case 2 -> selectData.append(table.getValueAt(row, 1).toString()).append("\r\n");
                    case 3 ->
                            selectData.append(String.format("%s=%s", table.getValueAt(row, 1).toString(), encodeParameter(table.getValueAt(row, 2).toString()))).append("\r\n");
                }
            }
        }

        if (!selectData.isEmpty()) {
            selectData.delete(selectData.length() - 2, selectData.length());
        } else {
            return "";
        }

        return selectData.toString();
    }

    public String encodeParameter(String input) {
        try {
            input = api.utilities().urlUtils().encode(input);
        } catch (Exception ignored) {
        }
        return input;
    }

    public JTable getDataTable() {
        return this.dataTable;
    }
}
