package caa.component.member;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.requests.HttpRequest;
import caa.component.utils.UITools;
import caa.component.member.taskboard.MessageTableModel;
import caa.instances.Database;
import caa.instances.Fuzzer;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import jregex.Matcher;
import jregex.Pattern;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.stream.IntStream;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;

public class FuzzerDialog extends JDialog {
    private String httpRequest;
    private String tabName;
    private final MontoyaApi api;
    private final Database db;
    private final MessageTableModel messageTableModel;
    private final JPanel contentPanel;
    private final String payload;
    private final boolean secure;
    private final Dimension dialogDimension = new Dimension(888, 688);
    private final JPopupMenu popupMenu;

    private final MouseAdapter mouseAdapter = new MouseAdapter() {
        public void mouseReleased(MouseEvent e) {
            if (SwingUtilities.isRightMouseButton(e)) {
                popupMenu.show(e.getComponent(), e.getX(), e.getY());
            }
        }
    };

    public FuzzerDialog(MontoyaApi api, Database db, MessageTableModel messageTableModel, String httpRequest, boolean secure, String tabName, String payload) {
        this.api = api;
        this.db = db;
        this.messageTableModel = messageTableModel;
        this.httpRequest = httpRequest;
        this.tabName = tabName;
        this.payload = payload;
        this.secure = secure;

        contentPanel = new JPanel(new BorderLayout());
        popupMenu = new JPopupMenu();

        initComponents();

        setTitle("Fuzzer - Task Configuration");
        setSize(dialogDimension);

        // 把包含整个布局的面板添加到对话框
        add(contentPanel);
        setLocationRelativeTo(null);
        setAlwaysOnTop(true);

        // 增大对话框大小以容纳边距
        setVisible(true);
    }

    private void initComponents() {
        // 设置主面板的边距
        contentPanel.setBorder(new EmptyBorder(10, 15, 10, 15));

        JPanel settingPanel = new JPanel(new GridBagLayout());
        settingPanel.setBorder(new EmptyBorder(0, 0, 10, 0));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1.0;
        JLabel hostLabel = new JLabel("Host:");
        JTextField hostTextField = new JTextField();
        JCheckBox useHttpsCheckbox = new JCheckBox("Use HTTPS");
        useHttpsCheckbox.setSelected(secure);
        settingPanel.add(hostLabel);
        settingPanel.add(hostTextField, constraints);
        settingPanel.add(useHttpsCheckbox);

        contentPanel.add(settingPanel, BorderLayout.NORTH);

        // Request
        JPanel requestPanel = new JPanel(new BorderLayout());
        JTextArea requestTextArea = new JTextArea();
        requestTextArea.setLineWrap(true);
        requestTextArea.setWrapStyleWord(true);
        requestTextArea.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                updateHost();
            }
            public void removeUpdate(DocumentEvent e) {
                updateHost();
            }
            public void changedUpdate(DocumentEvent e) {
                updateHost();
            }

            private void updateHost() {
                jregex.Pattern pattern = new jregex.Pattern("Host:\\s*([0-9a-z\\.-]+)", Pattern.IGNORE_CASE);
                Matcher matcher = pattern.matcher(requestTextArea.getText());
                if (matcher.find()) {
                    hostTextField.setText(matcher.group(1));
                } else {
                    hostTextField.setText("");
                }
            }
        });
        requestTextArea.setText(httpRequest);
        JLabel requestLabel = new JLabel("Request:");
        JPanel requestSetPanel = new JPanel(new BorderLayout());
        requestSetPanel.add(requestLabel, BorderLayout.WEST);
        requestPanel.add(requestSetPanel, BorderLayout.NORTH);
        JScrollPane requestScrollPane = new JScrollPane(requestTextArea);
        requestPanel.add(requestScrollPane, BorderLayout.CENTER);


        // Payload
        JPanel payloadTablePanel = new JPanel(new BorderLayout());

        DefaultTableModel model = new DefaultTableModel();

        JTable table = new JTable(model);
        model.addColumn("Name");
        JScrollPane scrollPane = new JScrollPane(table);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setBorder(new EmptyBorder(0, 3, 0, 0));
        GridBagLayout layout = new GridBagLayout();
        layout.rowHeights = new int[] {0, 0, 0, 0, 0, 0, 0};
        layout.rowWeights = new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE};
        buttonPanel.setLayout(layout);

        JPanel inputPanel = new JPanel(new BorderLayout());
        JPanel inputPanelB = new JPanel(new BorderLayout());
        inputPanelB.setBorder(new EmptyBorder(0, 0, 3, 0));
        constraints.gridx = 1;

        JButton addButton = new JButton("Add");
        JButton removeButton = new JButton("Remove");
        JButton pasteButton = new JButton("Paste");
        JButton clearButton = new JButton("Clear");
        JButton deduplicateButton = new JButton("Deduplicate");
        JComboBox<String> fuzzModeComboBox = new JComboBox<>();
        String[] mode = new String[] {"Param", "File", "Path", "Value"};
        fuzzModeComboBox.setModel(new DefaultComboBoxModel<>(mode));

        // Value 修改Name快捷方式

        JMenuItem renameMenuItem = new JMenuItem("Rename");
        popupMenu.add(renameMenuItem);
        renameMenuItem.addActionListener(e -> {
            int[] selectedRows = table.getSelectedRows();
            String newName = JOptionPane.showInputDialog("Please enter the new name.");
            if (!newName.isBlank()) {
                for (int row : selectedRows) {
                    int columnIndex = 0;
                    table.getModel().setValueAt(newName, row, columnIndex);
                }
            }
        });

        fuzzModeComboBox.addActionListener(e -> {
            String selected = (String) fuzzModeComboBox.getSelectedItem();
            if (selected.equals("Value")) {
                model.addColumn("Value");
                model.setColumnCount(2);

                table.addMouseListener(mouseAdapter);
            } else {
                model.setColumnCount(1);
                table.removeMouseListener(mouseAdapter);
            }
            addDataToTable(payload, model);
        });
        fuzzModeComboBox.setSelectedItem(tabName);

        constraints.insets = new Insets(0, 0, 3, 0);
        constraints.gridy = 0;
        buttonPanel.add(fuzzModeComboBox, constraints);
        constraints.gridy = 1;
        buttonPanel.add(addButton, constraints);
        constraints.gridy = 2;
        buttonPanel.add(removeButton, constraints);
        constraints.gridy = 3;
        buttonPanel.add(pasteButton, constraints);
        constraints.gridy = 4;
        buttonPanel.add(clearButton, constraints);
        constraints.gridy = 5;
        buttonPanel.add(deduplicateButton, constraints);

        JTextField addTextField = new JTextField();
        String defaultText = "Enter a new item";
        UITools.addPlaceholder(addTextField, defaultText);

        inputPanelB.add(addTextField, BorderLayout.CENTER);
        inputPanel.add(scrollPane, BorderLayout.CENTER);
        inputPanel.add(inputPanelB, BorderLayout.NORTH);

        payloadTablePanel.add(buttonPanel, BorderLayout.EAST);
        payloadTablePanel.add(inputPanel, BorderLayout.CENTER);

        addButton.addActionListener(e -> {
            String addTextFieldText = addTextField.getText();
            if (!addTextFieldText.equals(defaultText)) {
                String[] cellData = addTextFieldText.split("\\t");
                model.addRow(cellData);
            }
        });

        pasteButton.addActionListener(e -> {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            try {
                String data = (String) clipboard.getData(DataFlavor.stringFlavor);

                if (data != null && !data.isEmpty()) {
                    addDataToTable(data, model);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        removeButton.addActionListener(e -> {
            int selectedRow = table.getSelectedRow();
            if (selectedRow != -1) {
                model.removeRow(selectedRow);
            }
        });

        clearButton.addActionListener(e -> model.setRowCount(0));

        deduplicateButton.addActionListener(e -> {
            deduplicateTableData(model);
        });

        JPanel payloadPanel = new JPanel(new BorderLayout());
        JLabel payloadLabel = new JLabel("Payload:");
        JPanel payloadSetPanel = new JPanel(new BorderLayout());
        payloadSetPanel.add(payloadLabel, BorderLayout.WEST);
        payloadPanel.add(payloadSetPanel, BorderLayout.NORTH);
        payloadPanel.add(payloadTablePanel, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setLeftComponent(requestPanel);
        splitPane.setRightComponent(payloadPanel);
        splitPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                splitPane.setDividerLocation(0.5);
            }
        });

        contentPanel.add(splitPane, BorderLayout.CENTER);

        JButton confirmButton = new JButton("Confirm");
        confirmButton.addActionListener(e -> {
            HttpService httpService = HttpService.httpService(hostTextField.getText(), useHttpsCheckbox.isSelected());
            HttpRequest httpRequest = HttpRequest.httpRequest(httpService, requestTextArea.getText().replaceAll("\r", "").replaceAll("\n", "\r\n"));
            dispose();
            sendToFuzzer(httpRequest, hostTextField.getText(), getTableData(table), (String) fuzzModeComboBox.getSelectedItem());
        });

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());

        JPanel dialogButtonPanel = new JPanel();
        dialogButtonPanel.add(confirmButton);
        dialogButtonPanel.add(cancelButton);

        contentPanel.add(dialogButtonPanel, BorderLayout.SOUTH);
    }

    private void deduplicateTableData(DefaultTableModel model) {
        // 使用 Map 存储每一行的数据，用于去重
        Set<List<Object>> rowData = new LinkedHashSet<>();

        int columnCount = model.getColumnCount();

        // 将每一行数据作为一个列表，添加到 Set 中
        for (int i = 0; i < model.getRowCount(); i++) {
            List<Object> row = new ArrayList<>();
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

    private String getTableData(JTable table) {
        int rowCount = table.getRowCount();
        // 用一个数组代替之前的 getSelectedRows() 方法返回的数组
        int[] allRows = IntStream.range(0, rowCount).toArray();
        StringBuilder allData = new StringBuilder();
        for (int row : allRows) {
            int columnCount = table.getColumnCount();
            switch (columnCount) {
                case 1 -> allData.append(table.getValueAt(row, 0).toString()).append("\n");
                case 2 -> allData.append(String.format("%s\t%s", table.getValueAt(row, 0).toString(), table.getValueAt(row, 1).toString())).append("\n");
            }
        }

        if (allData.length() > 0){
            allData.deleteCharAt(allData.length() - 1);
        }

        return allData.toString();
    }

    private void addDataToTable(String data, DefaultTableModel model) {
        if (!data.isBlank()) {
            String[] rows = data.split("\\r?\\n");
            for (String row : rows) {
                String[] cellData = row.split("\\t");
                model.addRow(cellData);
            }
            deduplicateTableData(model);
        }
    }

    private void sendToFuzzer(HttpRequest request, String host, String payload, String fuzzMode) {
        if (request != null) {
            Object payloadObj = null;
            if (payload.contains("\t")) {
                java.util.List<String> dataList = Arrays.stream(payload.split("\n")).toList();
                SetMultimap<String, String> keyValues = HashMultimap.create();
                for (String data : dataList) {
                    List<String> tmpData = Arrays.stream(data.split("\t")).toList();
                    keyValues.put(tmpData.get(0), tmpData.get(1));
                }
                payloadObj = keyValues;
            } else {
                payloadObj = Arrays.stream(payload.split("\n")).toList();
            }

            do {
                String taskName = JOptionPane.showInputDialog("Please enter the task name.");
                if (!taskName.isBlank()) {
                    String fullTaskName = String.format("%s - %s", host, taskName);

                    if (messageTableModel.getTaskNameList().contains(fullTaskName)) {
                        JOptionPane.showMessageDialog(null, "The task name already exists, please enter again!", "Error", JOptionPane.WARNING_MESSAGE);
                    } else {
                        Object finalPayloadObj = payloadObj;
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                Fuzzer fuzzer = new Fuzzer(api, db, messageTableModel, fullTaskName, fuzzMode, finalPayloadObj);
                                fuzzer.fuzzRequest(request);
                            }
                        }).start();

                        JOptionPane.showMessageDialog(null, "Successfully started the fuzzing task, please check the task details in the Taskboard panel.", "Info", JOptionPane.INFORMATION_MESSAGE);
                        break;
                    }
                }
            } while (true);
        }
    }

}