package caa.component.generator;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.UserInterface;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import caa.Config;
import caa.instances.payload.PayloadGenerator;
import caa.utils.ConfigLoader;
import caa.utils.HttpUtils;
import caa.utils.UIEnhancer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.util.stream.IntStream;

public class Tab extends JPanel {
    private final MontoyaApi api;
    private final ConfigLoader configLoader;
    private final HttpUtils httpUtils;
    private final PayloadGenerator payloadGenerator;
    private final String payloadType;
    private final String payloads;
    private final HttpRequest httpRequest;

    private JComboBox<String> payloadTypeComboBox;
    private DefaultTableModel payloadTableModel;
    private JPopupMenu popupMenu;
    private final MouseAdapter mouseAdapter = new MouseAdapter() {
        public void mouseReleased(MouseEvent e) {
            if (SwingUtilities.isRightMouseButton(e)) {
                popupMenu.show(e.getComponent(), e.getX(), e.getY());
            }
        }
    };
    private JTextField urlField;
    private HttpRequestEditor requestEditor;
    private JTable payloadTable;
    private JComboBox<String> valueTypeComboBox;
    private JTextField valueInputField;
    private JTextField valueLengthField;

    public Tab(MontoyaApi api, ConfigLoader configLoader, HttpRequest httpRequest, String payloadType, String payloads) {
        this.api = api;
        this.configLoader = configLoader;
        this.httpUtils = new HttpUtils(api, configLoader);
        this.payloadGenerator = new PayloadGenerator(api, configLoader);

        this.httpRequest = httpRequest;
        this.payloadType = payloadType;
        this.payloads = payloads;

        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                splitPane.setDividerLocation(0.6);
            }
        });

        JPanel leftPanel = createLeftPanel();
        JSplitPane rightPanel = createRightPanel();
        rightPanel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                rightPanel.setDividerLocation(0.8);
            }
        });

        splitPane.setLeftComponent(leftPanel);
        splitPane.setRightComponent(rightPanel);

        add(splitPane, BorderLayout.CENTER);
    }

    private JPanel createLeftPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(new EmptyBorder(5, 5, 5, 5));

        // URL部分
        JPanel urlPanel = new JPanel(new BorderLayout(10, 0));
        JLabel urlLabel = new JLabel("URL:");
        urlField = new JTextField(httpRequest.url());
        urlField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                changeAction();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                changeAction();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                changeAction();
            }

            private void changeAction() {
                // 改动就重组
                try {
                    HttpService httpService = HttpService.httpService(urlField.getText());
                    requestEditor.setRequest(HttpRequest.httpRequest(httpService, requestEditor.getRequest().toByteArray()));
                } catch (Exception ignored) {

                }
            }
        });

        JButton generateButton = new JButton("Generate");

        urlPanel.add(urlLabel, BorderLayout.WEST);
        urlPanel.add(urlField, BorderLayout.CENTER);
        urlPanel.add(generateButton, BorderLayout.EAST);

        generateButton.addActionListener(e -> {
            boolean res = false;
            String tablePayloads = getTableData(payloadTable);
            if (!tablePayloads.isEmpty()) {
                HttpService httpService = HttpService.httpService(urlField.getText());
                HttpRequest targetRequest = HttpRequest.httpRequest(httpService, requestEditor.getRequest().toByteArray());

                res = payloadGenerator.generateRequest(targetRequest, tablePayloads, payloadTypeComboBox.getSelectedItem().toString(), valueTypeComboBox.getSelectedItem().toString(), valueInputField.getText(), Integer.parseInt(valueLengthField.getText()));
            }

            if (res) {
                JOptionPane.showMessageDialog(this, "Successfully generated payload.", "CaA", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Generated payload return error, please check!", "CaA", JOptionPane.ERROR_MESSAGE);
            }
        });

        // 请求部分
        JPanel requestPanel = new JPanel(new BorderLayout(0, 5));
        JLabel requestLabel = new JLabel("Request");
        requestLabel.setFont(new Font(requestLabel.getFont().getName(), Font.BOLD, 14));

        UserInterface userInterface = api.userInterface();
        requestEditor = userInterface.createHttpRequestEditor();
        requestEditor.setRequest(httpRequest);
        requestPanel.add(requestLabel, BorderLayout.NORTH);
        requestPanel.add(requestEditor.uiComponent(), BorderLayout.CENTER);

        panel.add(urlPanel, BorderLayout.NORTH);
        panel.add(requestPanel, BorderLayout.CENTER);

        return panel;
    }

    private JSplitPane createRightPanel() {
        JSplitPane panel = new JSplitPane(JSplitPane.VERTICAL_SPLIT);

        // 有效载荷标题
        JLabel payloadsLabel = new JLabel("Payload");
        payloadsLabel.setFont(new Font(payloadsLabel.getFont().getName(), Font.BOLD, 14));

        // 值部分
        JLabel valuesLabel = new JLabel("Value");
        valuesLabel.setFont(new Font(valuesLabel.getFont().getName(), Font.BOLD, 14));

        // 组合所有右侧组件
        JPanel rightTopPanel = new JPanel(new BorderLayout(0, 5));
        rightTopPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        rightTopPanel.add(payloadsLabel, BorderLayout.NORTH);
        rightTopPanel.add(getPayloadPanel(), BorderLayout.CENTER);

        JPanel rightCenterPanel = new JPanel(new BorderLayout());
        rightCenterPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        rightCenterPanel.add(valuesLabel, BorderLayout.NORTH);
        JScrollPane valuesScrollPane = new JScrollPane(getValuePanel());
        valuesScrollPane.setBorder(null);
        rightCenterPanel.add(valuesScrollPane, BorderLayout.CENTER);

        panel.setLeftComponent(rightTopPanel);
        panel.setRightComponent(rightCenterPanel);

        payloadTypeComboBox.setSelectedItem(payloadType);
        addDataToTable(payloads, payloadTableModel);
        return panel;
    }

    private JPanel getPayloadPanel() {
        JPanel payloadTablePanel = new JPanel(new BorderLayout());

        payloadTypeComboBox = new JComboBox<>();

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1.0;
        payloadTableModel = new DefaultTableModel();
        payloadTable = new JTable(payloadTableModel);
        payloadTableModel.addColumn("Name");
        JScrollPane scrollPane = new JScrollPane(payloadTable);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setBorder(new EmptyBorder(0, 3, 0, 0));
        GridBagLayout layout = new GridBagLayout();
        layout.rowHeights = new int[]{0, 0, 0, 0, 0, 0, 0};
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
        String[] mode = new String[]{"Param", "File", "FullPath", "Path", "Value"};
        payloadTypeComboBox.setModel(new DefaultComboBoxModel<>(mode));

        // Value 修改Name快捷方式
        JMenuItem renameMenuItem = new JMenuItem("Rename");
        popupMenu = new JPopupMenu();
        popupMenu.add(renameMenuItem);

        renameMenuItem.addActionListener(e -> {
            int[] selectedRows = payloadTable.getSelectedRows();
            String newName = JOptionPane.showInputDialog(this, "Please enter the new name.");
            if (!newName.isBlank()) {
                for (int row : selectedRows) {
                    int columnIndex = 0;
                    payloadTable.getModel().setValueAt(newName, row, columnIndex);
                }
            }
        });

        constraints.insets = new Insets(0, 0, 0, 0);
        constraints.gridy = 0;
        buttonPanel.add(addButton, constraints);
        constraints.gridy = 1;
        buttonPanel.add(removeButton, constraints);
        constraints.gridy = 2;
        buttonPanel.add(pasteButton, constraints);
        constraints.gridy = 3;
        buttonPanel.add(clearButton, constraints);

        JTextField addTextField = new JTextField();
        String defaultText = "Enter a new item";
        String defaultTextForValue = "Enter a new item (e.g. key=value)";
        UIEnhancer.setTextFieldPlaceholder(addTextField, defaultText);

        addTextField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    addActionPerformed(null, payloadTableModel, addTextField);
                }
            }
        });

        payloadTypeComboBox.addActionListener(e -> {
            String selected = payloadTypeComboBox.getSelectedItem().toString();
            if (selected.equals("Value")) {
                payloadTableModel.addColumn("Value");
                payloadTableModel.setColumnCount(2);

                payloadTable.addMouseListener(mouseAdapter);
                // 切换到Value模式时，更新输入框提示文本
                UIEnhancer.setTextFieldPlaceholder(addTextField, defaultTextForValue);
            } else {
                payloadTableModel.setColumnCount(1);
                payloadTable.removeMouseListener(mouseAdapter);
                // 切换到其他模式时，恢复原始提示文本
                UIEnhancer.setTextFieldPlaceholder(addTextField, defaultText);
            }

            setValueEnabled(selected.equals("Param"));
        });

        JPanel payloadTypePanel = new JPanel(new BorderLayout(10, 0));
        JLabel payloadTypeLabel = new JLabel("Type:");
        payloadTypePanel.add(payloadTypeLabel, BorderLayout.WEST);
        payloadTypePanel.add(payloadTypeComboBox, BorderLayout.CENTER);
        payloadTypePanel.setBorder(new EmptyBorder(0, 0, 3, 0));

        JPanel payloadInputPanel = new JPanel(new BorderLayout(10, 0));
        JLabel payloadInputLabel = new JLabel("Input:");
        payloadInputPanel.add(payloadInputLabel, BorderLayout.WEST);
        payloadInputPanel.add(addTextField, BorderLayout.CENTER);

        inputPanelB.add(payloadTypePanel, BorderLayout.NORTH);
        inputPanelB.add(payloadInputPanel, BorderLayout.CENTER);
        inputPanel.add(inputPanelB, BorderLayout.NORTH);
        inputPanel.add(scrollPane, BorderLayout.CENTER);

        addButton.addActionListener(e -> {
            String addTextFieldText = addTextField.getText();
            if (!addTextFieldText.equals(defaultText)) {
                addDataToTable(addTextFieldText, payloadTableModel);
            }
        });

        UIEnhancer.addButtonListener(pasteButton, removeButton, clearButton, payloadTable, payloadTableModel, this::addDataToTable);

        payloadTablePanel.add(buttonPanel, BorderLayout.EAST);
        payloadTablePanel.add(inputPanel, BorderLayout.CENTER);

        return payloadTablePanel;
    }

    private void addActionPerformed(ActionEvent e, DefaultTableModel model, JTextField addTextField) {
        String addTextFieldText = addTextField.getText();
        if (addTextField.getForeground().equals(Color.BLACK)) {
            addDataToTable(addTextFieldText, model);
            addTextField.setText("");
            addTextField.requestFocusInWindow();
        }
    }

    private JPanel getValuePanel() {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1.0;

        JPanel valueTypePanel = new JPanel(new BorderLayout(10, 0));
        JLabel valueTypeLabel = new JLabel("Type:");
        valueTypeComboBox = new JComboBox<>(new String[]{"Random", "Custom"});
        valueTypeComboBox.addActionListener(e -> {
            String selected = valueTypeComboBox.getSelectedItem().toString();
            valueLengthField.setEnabled(selected.equals("Random"));
        });
        valueTypePanel.add(valueTypeLabel, BorderLayout.WEST);
        valueTypePanel.add(valueTypeComboBox, BorderLayout.CENTER);
        valueTypePanel.setBorder(new EmptyBorder(3, 0, 0, 0));

        JPanel valueInputPanel = new JPanel(new BorderLayout(10, 0));
        JLabel valueInputLabel = new JLabel("Input:");
        valueInputField = new JTextField(Config.alphanumericChars);
        valueInputPanel.add(valueInputLabel, BorderLayout.WEST);
        valueInputPanel.add(valueInputField, BorderLayout.CENTER);
        valueInputPanel.setBorder(new EmptyBorder(3, 0, 0, 0));

        JPanel lengthPanel = new JPanel(new BorderLayout(10, 0));
        JLabel lengthLabel = new JLabel("Length:");
        valueLengthField = new JTextField(String.valueOf(Config.defaultLength));
        lengthPanel.add(lengthLabel, BorderLayout.WEST);
        lengthPanel.add(valueLengthField, BorderLayout.CENTER);
        lengthPanel.setBorder(new EmptyBorder(3, 0, 0, 0));

        JPanel buttonPanel = new JPanel();
        GridBagLayout layout = new GridBagLayout();
        layout.rowHeights = new int[]{0, 0, 0, 0, 0, 0, 0};
        layout.rowWeights = new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE};
        buttonPanel.setLayout(layout);

        constraints.insets = new Insets(0, 0, 0, 0);
        constraints.gridy = 0;
        buttonPanel.add(valueTypePanel, constraints);
        constraints.gridy = 1;
        buttonPanel.add(valueInputPanel, constraints);
        constraints.gridy = 2;
        buttonPanel.add(lengthPanel, constraints);

        return buttonPanel;
    }

    private void setValueEnabled(boolean enabled) {
        valueTypeComboBox.setEnabled(enabled);
        valueInputField.setEnabled(enabled);
        valueLengthField.setEnabled(enabled);
    }

    private String getTableData(JTable table) {
        int rowCount = table.getRowCount();
        int[] allRows = IntStream.range(0, rowCount).toArray();
        StringBuilder selectData = new StringBuilder();

        for (int row : allRows) {
            int columnCount = table.getColumnCount();
            switch (columnCount) {
                case 1 -> selectData.append(table.getValueAt(row, 0).toString()).append("\r\n");
                case 2 ->
                        selectData.append(String.format("%s=%s", table.getValueAt(row, 0).toString(), table.getValueAt(row, 1).toString())).append("\r\n");
            }
        }

        if (!selectData.isEmpty()) {
            selectData.delete(selectData.length() - 2, selectData.length());
        } else {
            return "";
        }

        return selectData.toString();
    }

    private void addDataToTable(String data, DefaultTableModel model) {
        if (data.isBlank()) {
            return;
        }

        String[] rows = data.split("\\r?\\n");
        for (String row : rows) {
            String[] cellData;

            if (row.contains("=")) {
                cellData = new String[]{row.split("=")[0], httpUtils.decodeParameter(row.split("=")[1])};
            } else {
                cellData = new String[]{row};
            }

            model.addRow(cellData);
        }

        UIEnhancer.deduplicateTableData(model);
    }
}