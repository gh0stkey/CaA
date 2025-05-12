package caa.component.member;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import caa.component.generator.Generator;
import caa.instances.Database;
import caa.instances.payload.PayloadGenerator;
import caa.utils.ConfigLoader;
import caa.utils.UITools;
import com.google.common.collect.SetMultimap;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class DatatablePanel extends JPanel {
    private final JTable dataTable;
    private final DefaultTableModel dataTableModel;
    private final JTextField searchField;
    private final TableRowSorter<DefaultTableModel> sorter;
    private final MontoyaApi api;
    private final ConfigLoader configLoader;
    private final Generator generator;
    private final JCheckBox searchMode = new JCheckBox("Reverse search");
    private final String tabName;
    private final Object dataObj;
    private final int columnSize;
    private final HttpRequest httpRequest;
    private final Database db;
    private final PayloadGenerator payloadGenerator;
    private final DisplayMode displayMode;

    public DatatablePanel(MontoyaApi api, Database db, ConfigLoader configLoader, Generator generator, List<String> columnNameList, Object dataObj, HttpRequest httpRequest, String tabName, boolean flag) {
        this(api, db, configLoader, generator, columnNameList, dataObj, httpRequest, tabName, flag ? DisplayMode.COUNT : DisplayMode.STANDARD);
    }

    public DatatablePanel(MontoyaApi api, Database db, ConfigLoader configLoader, Generator generator, List<String> columnNameList, Object dataObj, HttpRequest httpRequest, String tabName, DisplayMode displayMode) {
        this.api = api;
        this.db = db;
        this.configLoader = configLoader;
        this.generator = generator;
        this.tabName = tabName.replace("All ", "");
        this.dataObj = dataObj;
        this.httpRequest = httpRequest;
        this.columnSize = columnNameList.size();
        this.payloadGenerator = new PayloadGenerator(api, configLoader);
        this.displayMode = displayMode;

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

        if (displayMode == DisplayMode.COUNT) {
            TableColumn countColumn = dataTable.getColumnModel().getColumn(columnSize);
            countColumn.setMaxWidth(50);
        }

        populateTableData();


        // 设置灰色默认文本
        String searchText = "Search";
        UITools.setTextFieldPlaceholder(searchField, searchText);

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
            Point buttonLocation = settingsButton.getLocationOnScreen();
            Dimension menuSize = menu.getPreferredSize();
            int x = buttonLocation.x + (settingsButton.getWidth() - menuSize.width) / 2;
            int y = buttonLocation.y - menuSize.height;
            menu.show(settingsButton, x - buttonLocation.x, y - buttonLocation.y);
        });

        optionsPanel.add(settingsButton);
        optionsPanel.add(Box.createHorizontalStrut(5));
        optionsPanel.add(searchField);

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

        dataTable.addMouseListener(new MouseAdapter() {
            public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    if (tabName.equals("Param") || tabName.equals("Value")) {
                        popupMenu.add(copyMenu);
                    }
                    if (httpRequest != null) {
                        popupMenu.add(sendToGenerator);
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
        if (searchField.getForeground().equals(Color.BLACK)) {
            RowFilter<Object, Object> rowFilter = new RowFilter<Object, Object>() {
                public boolean include(Entry<?, ?> entry) {
                    String searchFieldTextText = searchField.getText();
                    Pattern pattern = null;
                    try {
                        pattern = Pattern.compile(searchFieldTextText, Pattern.CASE_INSENSITIVE);
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

    /**
     * 填充表格数据
     */
    private void populateTableData() {
        if (displayMode == DisplayMode.COUNT) {
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

    public String getSelectedDataAtTable(JTable table) {
        int[] selectRows = table.getSelectedRows();
        StringBuilder selectData = new StringBuilder();

        for (int row : selectRows) {
            int columnCount = table.getColumnCount();
            if (displayMode == DisplayMode.COUNT) {
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
