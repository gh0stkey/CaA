package caa.component;

import burp.api.montoya.MontoyaApi;
import caa.utils.ConfigLoader;
import caa.utils.UITools;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Config extends JPanel {
    private final MontoyaApi api;
    private final ConfigLoader configLoader;
    private final String defaultText = "Enter a new item";
    private boolean isLoadingData = false;

    public Config(MontoyaApi api, ConfigLoader configLoader) {
        this.api = api;
        this.configLoader = configLoader;

        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;

        JPanel configInfoPanel = new JPanel(new GridBagLayout());
        configInfoPanel.setBorder(new EmptyBorder(10, 15, 5, 15));

        constraints.gridx = 1;
        JLabel dbPathLabel = new JLabel("Path:");
        JTextField dbPathTextField = new JTextField();
        dbPathTextField.setEditable(false);
        dbPathTextField.setText(configLoader.getDbFilePath());
        configInfoPanel.add(dbPathLabel);
        configInfoPanel.add(dbPathTextField, constraints);

        JTabbedPane configTabbedPanel = new JTabbedPane();

        String[] settingMode = new String[]{"Exclude suffix", "Block host", "Exclude status"};
        JPanel settingPanel = createConfigTablePanel(settingMode);
        JPanel scopePanel = getScopePanel();
        JScrollPane scopeScrollPane = new JScrollPane(scopePanel);
        scopeScrollPane.setBorder(new TitledBorder("Scope"));
        settingPanel.add(scopeScrollPane, BorderLayout.NORTH);
        configTabbedPanel.add("Setting", settingPanel);

        add(configInfoPanel, BorderLayout.NORTH);
        add(configTabbedPanel, BorderLayout.CENTER);
    }

    private JPanel getScopePanel() {
        JPanel scopePanel = new JPanel();
        scopePanel.setLayout(new BoxLayout(scopePanel, BoxLayout.X_AXIS));

        String[] scopeInit = caa.Config.scopeOptions.split("\\|");
        String[] scopeMode = configLoader.getScope().split("\\|");
        for (String scope : scopeInit) {
            JCheckBox checkBox = new JCheckBox(scope);
            scopePanel.add(checkBox);
            for (String mode : scopeMode) {
                if (scope.equals(mode)) {
                    checkBox.setSelected(true);
                }
            }

            checkBox.addActionListener(e -> updateScope(checkBox));
        }
        return scopePanel;
    }

    private TableModelListener createSettingTableModelListener(JComboBox<String> setTypeComboBox, DefaultTableModel model) {
        return e -> {
            // 如果是程序正在加载数据，不处理事件
            if (isLoadingData) {
                return;
            }

            String selected = (String) setTypeComboBox.getSelectedItem();
            String values = getFirstColumnDataAsString(model);

            if (selected.equals("Exclude suffix")) {
                if (!values.equals(configLoader.getExcludeSuffix())) {
                    configLoader.setExcludeSuffix(values);
                }
            }

            if (selected.equals("Block host")) {
                if (!values.equals(configLoader.getBlockHost())) {
                    configLoader.setBlockHost(values);
                }
            }

            if (selected.equals("Exclude status")) {
                if (!values.equals(configLoader.getExcludeStatus())) {
                    configLoader.setExcludeStatus(values);
                }
            }
        };
    }

    private ActionListener createSettingActionListener(JComboBox<String> setTypeComboBox, DefaultTableModel model) {
        return e -> {
            String selected = (String) setTypeComboBox.getSelectedItem();

            // 设置标志，表示正在加载数据
            isLoadingData = true;
            model.setRowCount(0);

            if (selected.equals("Exclude suffix")) {
                addDataToTable(configLoader.getExcludeSuffix().replaceAll("\\|", "\r\n"), model);
            }

            if (selected.equals("Block host")) {
                addDataToTable(configLoader.getBlockHost().replaceAll("\\|", "\r\n"), model);
            }

            if (selected.equals("Exclude status")) {
                addDataToTable(configLoader.getExcludeStatus().replaceAll("\\|", "\r\n"), model);
            }

            // 重置标志
            isLoadingData = false;
        };
    }

    private JPanel createConfigTablePanel(String[] mode) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;

        JPanel settingPanel = new JPanel(new BorderLayout());
        DefaultTableModel model = new DefaultTableModel();

        JTable table = new JTable(model);
        model.addColumn("Value");
        JScrollPane scrollPane = new JScrollPane(table);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setBorder(new EmptyBorder(0, 3, 0, 0));
        GridBagLayout layout = new GridBagLayout();
        layout.rowHeights = new int[]{0, 0, 0, 0, 0, 0, 0};
        layout.rowWeights = new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE};
        buttonPanel.setLayout(layout);

        JPanel inputPanel = new JPanel(new BorderLayout());
        JPanel inputPanelB = new JPanel(new BorderLayout());
        inputPanelB.setBorder(new EmptyBorder(0, 0, 3, 0));

        JButton addButton = new JButton("Add");
        JButton removeButton = new JButton("Remove");
        JButton pasteButton = new JButton("Paste");
        JButton clearButton = new JButton("Clear");

        JComboBox<String> setTypeComboBox = new JComboBox<>();
        setTypeComboBox.setModel(new DefaultComboBoxModel<>(mode));

        setTypeComboBox.addActionListener(createSettingActionListener(setTypeComboBox, model));

        setTypeComboBox.setSelectedItem(mode[0]);

        model.addTableModelListener(createSettingTableModelListener(setTypeComboBox, model));

        constraints.insets = new Insets(0, 0, 3, 0);
        constraints.gridy = 0;
        buttonPanel.add(setTypeComboBox, constraints);
        constraints.gridy = 1;
        buttonPanel.add(addButton, constraints);
        constraints.gridy = 2;
        buttonPanel.add(removeButton, constraints);
        constraints.gridy = 3;
        buttonPanel.add(pasteButton, constraints);
        constraints.gridy = 4;
        buttonPanel.add(clearButton, constraints);

        JTextField addTextField = new JTextField();
        UITools.setTextFieldPlaceholder(addTextField, defaultText);

        inputPanelB.add(addTextField, BorderLayout.CENTER);
        inputPanel.add(scrollPane, BorderLayout.CENTER);
        inputPanel.add(inputPanelB, BorderLayout.NORTH);

        settingPanel.add(buttonPanel, BorderLayout.EAST);
        settingPanel.add(inputPanel, BorderLayout.CENTER);


        addButton.addActionListener(e -> addActionPerformed(e, model, addTextField));

        addTextField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    addActionPerformed(null, model, addTextField);
                }
            }
        });

        UITools.addButtonListener(pasteButton, removeButton, clearButton, table, model, this::addDataToTable);

        JPanel settingMainPanel = new JPanel(new BorderLayout());
        settingMainPanel.setBorder(new EmptyBorder(5, 15, 10, 15));
        JScrollPane settingScroller = new JScrollPane(settingPanel);
        settingScroller.setBorder(new TitledBorder("Setting"));
        settingMainPanel.add(settingScroller, BorderLayout.CENTER);

        return settingMainPanel;
    }


    private String getFirstColumnDataAsString(DefaultTableModel model) {
        StringBuilder firstColumnData = new StringBuilder();
        int numRows = model.getRowCount();

        for (int row = 0; row < numRows; row++) {
            firstColumnData.append(model.getValueAt(row, 0));
            if (row < numRows - 1) {
                firstColumnData.append("|");
            }
        }

        return firstColumnData.toString();
    }

    private void addDataToTable(String data, DefaultTableModel model) {
        if (!data.isBlank()) {
            String[] rows = data.split("\\r?\\n");
            for (String row : rows) {
                model.addRow(new String[]{row});
            }
            UITools.deduplicateTableData(model);
        }
    }


    public void updateScope(JCheckBox checkBox) {
        String boxText = checkBox.getText();
        boolean selected = checkBox.isSelected();

        Set<String> scope = new HashSet<>(Arrays.asList(configLoader.getScope().split("\\|")));

        if (selected) {
            scope.add(boxText);
        } else {
            scope.remove(boxText);
        }

        configLoader.setScope(String.join("|", scope));
    }

    private void addActionPerformed(ActionEvent e, DefaultTableModel model, JTextField addTextField) {
        String addTextFieldText = addTextField.getText();
        if (addTextField.getForeground().equals(Color.BLACK)) {
            addDataToTable(addTextFieldText, model);
            addTextField.setText("");
            addTextField.requestFocusInWindow();
        }
    }
}
