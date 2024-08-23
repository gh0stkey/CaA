package caa.component.member;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import caa.component.utils.UITools;
import caa.instances.Database;
import caa.instances.Generator;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.stream.IntStream;

public class GeneratorDialog extends JDialog {
    private HttpRequest httpRequest;
    private String tabName;
    private final Generator generator;
    private final MontoyaApi api;
    private final Database db;
    private final JPanel contentPanel;
    private final String payload;
    private final Dimension dialogDimension = new Dimension(600, 700);
    private final JPopupMenu popupMenu;
    private JTable payloadTable;
    private final JComboBox<String> payloadModeComboBox;
    private int randomStringFlag;
    private JTextField typeLengthField = new JTextField("6");

    private final MouseAdapter mouseAdapter = new MouseAdapter() {
        public void mouseReleased(MouseEvent e) {
            if (SwingUtilities.isRightMouseButton(e)) {
                popupMenu.show(e.getComponent(), e.getX(), e.getY());
            }
        }
    };

    public GeneratorDialog(DatatablePanel datatablePanel, MontoyaApi api, Database db, HttpRequest httpRequest, String tabName, String payload) {
        this.api = api;
        this.db = db;
        this.httpRequest = httpRequest;
        this.tabName = tabName;
        this.payload = payload;
        this.generator = new Generator(api);

        this.payloadModeComboBox = new JComboBox<>();
        this.contentPanel = new JPanel(new BorderLayout());
        this.popupMenu = new JPopupMenu();

        initComponents();

        setTitle("CaA - Payload Generator");
        setSize(dialogDimension);

        add(contentPanel);
        setLocationRelativeTo(datatablePanel);
        setAlwaysOnTop(true);

        setVisible(true);
    }

    private void initComponents() {
        // 设置主面板的边距
        contentPanel.setBorder(new EmptyBorder(10, 15, 10, 15));

        JPanel settingPanel = new JPanel(new BorderLayout());
        JPanel valueTypePanel = new JPanel(new BorderLayout());
        JScrollPane typeScrollPane = new JScrollPane(valueTypePanel);
        typeScrollPane.setBorder(new TitledBorder("Value Type"));
        valueTypePanel.setLayout(new BoxLayout(valueTypePanel, BoxLayout.X_AXIS));
        ButtonGroup group = new ButtonGroup();
        for (String typeName : new String[]{"A-Za-z", "0-9"}) {
            JRadioButton radioButton = getjRadioButton(typeName);
            valueTypePanel.add(radioButton);
            group.add(radioButton);
        }
        valueTypePanel.add(new JLabel(" | "));
        valueTypePanel.add(new JLabel("Length:"));
        valueTypePanel.add(typeLengthField);

        settingPanel.add(typeScrollPane, BorderLayout.CENTER);
        JScrollPane payloadPanel = getPayloadPanel();

        contentPanel.add(settingPanel, BorderLayout.NORTH);
        contentPanel.add(payloadPanel, BorderLayout.CENTER);

        JPanel dialogButtonPanel = getDialogButtonPanel();

        contentPanel.add(dialogButtonPanel, BorderLayout.SOUTH);
    }

    private JPanel getDialogButtonPanel() {
        JButton confirmButton = new JButton("Confirm");
        confirmButton.addActionListener(e -> {
            boolean res = false;
            String tablePayload = getTableData(payloadTable);
            if (!tablePayload.isEmpty()) {
                res = generator.generateRequest(httpRequest, tablePayload, payloadModeComboBox.getSelectedItem().toString(), randomStringFlag, Integer.parseInt(typeLengthField.getText()));
            }

            dispose();

            if (res) {
                JOptionPane.showMessageDialog(this, "Successfully generated payload.", "CaA", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Generated payload return error, please check!", "CaA", JOptionPane.ERROR_MESSAGE);
            }
        });

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());

        JPanel dialogButtonPanel = new JPanel();
        dialogButtonPanel.add(confirmButton);
        dialogButtonPanel.add(cancelButton);
        return dialogButtonPanel;
    }

    private JRadioButton getjRadioButton(String typeName) {
        JRadioButton radioButton = new JRadioButton(typeName);
        radioButton.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (radioButton.getText().toString().equals("A-Za-z")) {
                    randomStringFlag = 1;
                } else {
                    randomStringFlag = 2;
                }
            }
        });
        if (typeName.equals("A-Za-z")) {
            radioButton.setSelected(true);
        }
        return radioButton;
    }

    private JScrollPane getPayloadPanel() {
        JPanel payloadTablePanel = new JPanel(new BorderLayout());

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1.0;

        DefaultTableModel model = new DefaultTableModel();

        payloadTable = new JTable(model);
        model.addColumn("Name");
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
        String[] mode = new String[]{"Param", "File", "Path", "Value"};
        payloadModeComboBox.setModel(new DefaultComboBoxModel<>(mode));

        // Value 修改Name快捷方式
        JMenuItem renameMenuItem = new JMenuItem("Rename");
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

        payloadModeComboBox.addActionListener(e -> {
            String selected = payloadModeComboBox.getSelectedItem().toString();
            if (selected.equals("Value")) {
                model.addColumn("Value");
                model.setColumnCount(2);

                payloadTable.addMouseListener(mouseAdapter);
            } else {
                model.setColumnCount(1);
                payloadTable.removeMouseListener(mouseAdapter);
            }

            addDataToTable(payload, model);
        });
        payloadModeComboBox.setSelectedItem(tabName);

        constraints.insets = new Insets(0, 0, 3, 0);
        constraints.gridy = 0;
        buttonPanel.add(payloadModeComboBox, constraints);
        constraints.gridy = 1;
        buttonPanel.add(addButton, constraints);
        constraints.gridy = 2;
        buttonPanel.add(removeButton, constraints);
        constraints.gridy = 3;
        buttonPanel.add(pasteButton, constraints);
        constraints.gridy = 4;
        buttonPanel.add(clearButton, constraints);

        JTextField addTextField = new JTextField();
        String defaultText = "Enter a new item";
        UITools.addPlaceholder(addTextField, defaultText);

        inputPanelB.add(addTextField, BorderLayout.CENTER);
        inputPanel.add(scrollPane, BorderLayout.CENTER);
        inputPanel.add(inputPanelB, BorderLayout.NORTH);

        addButton.addActionListener(e -> {
            String addTextFieldText = addTextField.getText();
            if (!addTextFieldText.equals(defaultText)) {
                addDataToTable(addTextFieldText, model);
            }
        });

        UITools.addButtonListener(pasteButton, removeButton, clearButton, payloadTable, model, this::addDataToTable);

        payloadTablePanel.add(buttonPanel, BorderLayout.EAST);
        payloadTablePanel.add(inputPanel, BorderLayout.CENTER);
        JScrollPane payloadScrollPane = new JScrollPane(payloadTablePanel);
        payloadScrollPane.setBorder(new TitledBorder("Payload"));

        return payloadScrollPane;
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
                case 2 ->
                        allData.append(String.format("%s\t%s", table.getValueAt(row, 0).toString(), table.getValueAt(row, 1).toString())).append("\n");
            }
        }

        if (!allData.isEmpty()) {
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
            UITools.deduplicateTableData(model);
        }
    }
}