package caa.component;

import burp.api.montoya.MontoyaApi;
import caa.Config;
import caa.component.utils.UITools;
import caa.component.member.DatatablePanel;
import caa.component.member.taskboard.MessageTableModel;
import caa.instances.Database;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class Databoard extends JPanel {
    private JTextField hostTextField;
    private JButton searchButton;
    private JComboBox<String> tableComboBox;
    private JComboBox<String> limitComboBox;
    private JPanel dataPanel;
    private final MontoyaApi api;
    private final Database db;
    private final String defaultText = "Please enter the host.";
    private final MessageTableModel messageTableModel;

    public Databoard(MontoyaApi api, Database db, MessageTableModel messageTableModel) {
        this.api = api;
        this.db = db;
        this.messageTableModel = messageTableModel;
        initComponents();
    }

    private void initComponents() {
        setLayout(new GridBagLayout());
        ((GridBagLayout)getLayout()).columnWidths = new int[] {10, 0, 0, 0, 20, 0};
        ((GridBagLayout)getLayout()).rowHeights = new int[] {0, 65, 20, 0};
        ((GridBagLayout)getLayout()).columnWeights = new double[] {0.0, 0.0, 1.0, 0.0, 0.0, 1.0E-4};
        ((GridBagLayout)getLayout()).rowWeights = new double[] {0.0, 1.0, 0.0, 1.0E-4};

        tableComboBox = new JComboBox<>();
        tableComboBox.setModel(new DefaultComboBoxModel<>(Config.CaATableName));

        // 添加选项监听器
        tableComboBox.addActionListener(e -> {
            String selected = (String) tableComboBox.getSelectedItem();
            if (selected.contains("All")) {
                hostTextField.setEnabled(false);
                hostTextField.setText("");
            } else {
                if (hostTextField.getText().isBlank()) {
                    setHostTextField();
                }
                hostTextField.setEnabled(true);
            }
        });

        limitComboBox = new JComboBox<>();
        limitComboBox.setModel(new DefaultComboBoxModel<>(new String[] {
                "100",
                "1000",
                "10000"
        }));

        hostTextField = new JTextField();
        setHostTextField();
        UITools.addPlaceholder(hostTextField, defaultText);

        searchButton = new JButton();
        searchButton.setText("Search");
        searchButton.addActionListener(e -> {
            dataPanel.removeAll();
            String tableName = (String) tableComboBox.getSelectedItem();
            String limitSize = (String) limitComboBox.getSelectedItem();
            String host = hostTextField.getText();
            Object dataObj = db.selectData(host, tableName, limitSize);
            List<String> columnNameA = new ArrayList<>();
            columnNameA.add("Name");
            List<String> columnNameB = new ArrayList<>();
            columnNameB.add("Name");
            columnNameB.add("Value");
            if (tableName.equals("Value")) {
                dataPanel.add(new DatatablePanel(api, db, messageTableModel, columnNameB, dataObj, null, tableName), BorderLayout.CENTER);
            } else {
                dataPanel.add(new DatatablePanel(api, db, messageTableModel, columnNameA, dataObj, null, tableName), BorderLayout.CENTER);
            }
        });

        dataPanel = new JPanel();
        dataPanel.setLayout(new BorderLayout());

        add(tableComboBox, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(8, 0, 5, 5), 0, 0));
        add(hostTextField, new GridBagConstraints(2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(8, 0, 5, 5), 0, 0));
        add(limitComboBox, new GridBagConstraints(3, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(8, 0, 5, 5), 0, 0));
        add(searchButton, new GridBagConstraints(4, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(8, 0, 5, 5), 0, 0));
        add(dataPanel, new GridBagConstraints(1, 1, 4, 3, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(8, 0, 5, 5), 0, 0));
    }

    private void setHostTextField() {
        hostTextField.setText(defaultText);
        hostTextField.setForeground(Color.GRAY);
    }

}
