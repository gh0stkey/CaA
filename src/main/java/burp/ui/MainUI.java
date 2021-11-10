/*
 * Created by JFormDesigner on Sun Oct 31 02:40:06 CST 2021
 */

package burp.ui;

import burp.Config;
import burp.db.ProcessDB;
import burp.json.ProcessJson;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;

/**
 * @author 0chencc && EvilChen
 */

public class MainUI extends JPanel {

    public MainUI() {
        initComponents();
    }

    /**
     * 按钮/输入框开关函数
     */
    private void controlSwitch(boolean state) {
        connectButton.setEnabled(state);
        hostInput.setEditable(state);
        portInput.setEditable(state);
        userInput.setEditable(state);
        pwdInput.setEditable(state);
        dbInput.setEditable(state);
        queryButton.setEnabled(!state);
        disConnectButton.setEnabled(!state);
    }

    /**
     * 连接按钮
     */
    private void connectButtonActionPerformed(ActionEvent e) {

        String host = hostInput.getText();
        String port = portInput.getText();
        String user = userInput.getText();
        String pwd = pwdInput.getText();
        String db = dbInput.getText();

        Config.setConn(host, port, user, pwd, db);

        File configFile = new File(Config.CaAConfig);
        Map<String, String> map = new HashMap<>();

        map.put("host", host);
        map.put("port", port);
        map.put("username", user);
        map.put("password", pwd);
        map.put("database", db);

        if (Config.isConnect) {
            // 当连接成功时判断配置文件是否存在，不存在则创建
            if (configFile.exists()) {
                map = ProcessJson.parseJson();
            } else {
                try {
                    configFile.createNewFile();
                } catch (Exception es) {
                    es.printStackTrace();
                }
            }
            ProcessDB.initDB(db);
            // 就写入配置文件
            ProcessJson.writeJson(map);
            controlSwitch(false);
        } else {
            // 连接失败则弹框提示
            JOptionPane.showMessageDialog(null, "Database connection failed!", "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * 断开连接按钮
     */
    private void disconnectButtonActionPerformed(ActionEvent e) {
        // 关闭连接
        try {
            Config.isConnect = false;
            Config.CaAConn.close();
            controlSwitch(true);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    /**
     * 查询按钮
     */
    private void queryButtonActionPerformed(ActionEvent e) {
        // 获取控件内容
        String table = tableComboBox.getItemAt(tableComboBox.getSelectedIndex());
        String limit = limitCountInput.getText();
        String host = selectHostInput.getText();
        // 判断控件

        if ("".equals(limit) || (conditionCheckBox.isSelected() && "".equals(host))) {
            // host/limit没有内容则弹框提示
            JOptionPane.showMessageDialog(null, "Host or Limit is empty!", "Error",
                    JOptionPane.ERROR_MESSAGE);
        } else {
            try {
                host = conditionCheckBox.isSelected() ? host : "";
                ResultSet rs = ProcessDB.queryData(table, host, limit);
                Vector<String> title = new Vector<>();
                Vector<Vector<String>> data = new Vector<>();
                title.add("id");
                title.add("value");
                title.add("count");

                while (rs.next()) {
                    Vector<String> row = new Vector<>();
                    for (String i : title) {
                        row.add(rs.getString(i));
                    }
                    data.add(row);
                }
                model = (DefaultTableModel) queryResultTable.getModel();
                model.setDataVector(data, title);
                queryResultTable.setModel(model);
                showPanel.setVisible(true);
                if (!idCheckBox.isSelected()) {
                    tableColumnHide(idCheckBox, 0);
                }

                if (!countCheckBox.isSelected()) {
                    tableColumnHide(countCheckBox, 2);
                }
            } catch (SQLException es) {
                es.printStackTrace();
            }
        }

    }

    /**
     * 条件选择
     */
    private void conditionCheckBoxStateChanged(ChangeEvent e) {
        boolean conditionCheckBoxState = conditionCheckBox.isSelected();
        selectHostInput.setEditable(conditionCheckBoxState);
        String[] tableName = conditionCheckBoxState ? Config.CaATableName : Config.CaATablesName;
        tableComboBox.setModel(new DefaultComboBoxModel<>(tableName));
    }

    /**
     * 隐藏表单字段
     */
    private void tableColumnHide(JCheckBox jCheckBox, int columnIndex) {
        int jCheckBoxState = (jCheckBox.isSelected() ? 1 : 0) * 150;
        queryResultTable.getTableHeader().getColumnModel().getColumn(columnIndex)
                .setMaxWidth(jCheckBoxState);
        queryResultTable.getTableHeader().getColumnModel().getColumn(columnIndex)
                .setMinWidth(jCheckBoxState);
        queryResultTable.getTableHeader().getColumnModel().getColumn(columnIndex)
                .setPreferredWidth(jCheckBoxState);
    }


    private void initComponents() {
        // JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents
        tabbedPane1 = new JTabbedPane();
        ConfigPanel = new JPanel();
        panel1 = new JPanel();
        label4 = new JLabel();
        hostInput = new JTextField();
        panel2 = new JPanel();
        label5 = new JLabel();
        portInput = new JTextField();
        panel3 = new JPanel();
        label6 = new JLabel();
        userInput = new JTextField();
        panel4 = new JPanel();
        label7 = new JLabel();
        pwdInput = new JTextField();
        panel5 = new JPanel();
        label8 = new JLabel();
        dbInput = new JTextField();
        panel6 = new JPanel();
        connectButton = new JButton();
        disConnectButton = new JButton();
        QueryPanel = new JPanel();
        panel7 = new JPanel();
        conditionCheckBox = new JCheckBox();
        selectHostInput = new JTextField();
        tableComboBox = new JComboBox<>();
        limitLabel = new JLabel();
        limitCountInput = new JTextField();
        queryButton = new JButton();
        showPanel = new JPanel();
        label1 = new JLabel();
        idCheckBox = new JCheckBox();
        countCheckBox = new JCheckBox();
        scrollPane1 = new JScrollPane();
        queryResultTable = new JTable();

        //======== this ========
        setLayout(new GridBagLayout());
        ((GridBagLayout) getLayout()).columnWidths = new int[]{0, 0};
        ((GridBagLayout) getLayout()).rowHeights = new int[]{0, 0};
        ((GridBagLayout) getLayout()).columnWeights = new double[]{1.0, 1.0E-4};
        ((GridBagLayout) getLayout()).rowWeights = new double[]{1.0, 1.0E-4};

        //======== tabbedPane1 ========
        {

            //======== ConfigPanel ========
            {
                ConfigPanel.setLayout(new GridBagLayout());
                ((GridBagLayout) ConfigPanel.getLayout()).columnWidths = new int[]{0, 243, 326, 260,
                        0, 0};
                ((GridBagLayout) ConfigPanel.getLayout()).rowHeights = new int[]{0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0};
                ((GridBagLayout) ConfigPanel.getLayout()).columnWeights = new double[]{0.2, 0.27,
                        0.04, 0.27, 0.2, 1.0E-4};
                ((GridBagLayout) ConfigPanel.getLayout()).rowWeights = new double[]{0.05, 0.0, 0.0,
                        0.0, 0.0, 0.0, 0.0, 0.0, 0.6, 1.0E-4};

                //======== panel1 ========
                {
                    panel1.setLayout(new GridBagLayout());
                    ((GridBagLayout) panel1.getLayout()).columnWidths = new int[]{80, 0, 0};
                    ((GridBagLayout) panel1.getLayout()).rowHeights = new int[]{0, 0};
                    ((GridBagLayout) panel1.getLayout()).columnWeights = new double[]{0.0, 1.0,
                            1.0E-4};
                    ((GridBagLayout) panel1.getLayout()).rowWeights = new double[]{1.0, 1.0E-4};

                    //---- label4 ----
                    label4.setText("Host:");
                    panel1.add(label4, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
                            GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                            new Insets(0, 0, 0, 0), 0, 0));
                    panel1.add(hostInput, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0,
                            GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                            new Insets(0, 0, 0, 0), 0, 0));
                }
                ConfigPanel.add(panel1, new GridBagConstraints(2, 1, 1, 1, 0.0, 0.0,
                        GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                        new Insets(0, 0, 5, 5), 0, 0));

                //======== panel2 ========
                {
                    panel2.setLayout(new GridBagLayout());
                    ((GridBagLayout) panel2.getLayout()).columnWidths = new int[]{80, 0, 0};
                    ((GridBagLayout) panel2.getLayout()).rowHeights = new int[]{0, 0};
                    ((GridBagLayout) panel2.getLayout()).columnWeights = new double[]{0.0, 1.0,
                            1.0E-4};
                    ((GridBagLayout) panel2.getLayout()).rowWeights = new double[]{1.0, 1.0E-4};

                    //---- label5 ----
                    label5.setText("Port:");
                    panel2.add(label5, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
                            GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                            new Insets(0, 0, 0, 0), 0, 0));
                    panel2.add(portInput, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0,
                            GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                            new Insets(0, 0, 0, 0), 0, 0));
                }
                ConfigPanel.add(panel2, new GridBagConstraints(2, 2, 1, 1, 0.0, 0.0,
                        GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                        new Insets(0, 0, 5, 5), 0, 0));

                //======== panel3 ========
                {
                    panel3.setLayout(new GridBagLayout());
                    ((GridBagLayout) panel3.getLayout()).columnWidths = new int[]{80, 0, 0};
                    ((GridBagLayout) panel3.getLayout()).rowHeights = new int[]{0, 0};
                    ((GridBagLayout) panel3.getLayout()).columnWeights = new double[]{0.0, 0.8,
                            1.0E-4};
                    ((GridBagLayout) panel3.getLayout()).rowWeights = new double[]{1.0, 1.0E-4};

                    //---- label6 ----
                    label6.setText("Username:");
                    panel3.add(label6, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
                            GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                            new Insets(0, 0, 0, 0), 0, 0));
                    panel3.add(userInput, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0,
                            GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                            new Insets(0, 0, 0, 0), 0, 0));
                }
                ConfigPanel.add(panel3, new GridBagConstraints(2, 3, 1, 1, 0.0, 0.0,
                        GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                        new Insets(0, 0, 5, 5), 0, 0));

                //======== panel4 ========
                {
                    panel4.setLayout(new GridBagLayout());
                    ((GridBagLayout) panel4.getLayout()).columnWidths = new int[]{80, 0, 0};
                    ((GridBagLayout) panel4.getLayout()).rowHeights = new int[]{0, 0};
                    ((GridBagLayout) panel4.getLayout()).columnWeights = new double[]{0.0, 1.0,
                            1.0E-4};
                    ((GridBagLayout) panel4.getLayout()).rowWeights = new double[]{1.0, 1.0E-4};

                    //---- label7 ----
                    label7.setText("Password:");
                    panel4.add(label7, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
                            GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                            new Insets(0, 0, 0, 0), 0, 0));
                    panel4.add(pwdInput, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0,
                            GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                            new Insets(0, 0, 0, 0), 0, 0));
                }
                ConfigPanel.add(panel4, new GridBagConstraints(2, 4, 1, 1, 0.0, 0.0,
                        GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                        new Insets(0, 0, 5, 5), 0, 0));

                //======== panel5 ========
                {
                    panel5.setLayout(new GridBagLayout());
                    ((GridBagLayout) panel5.getLayout()).columnWidths = new int[]{80, 0, 0};
                    ((GridBagLayout) panel5.getLayout()).rowHeights = new int[]{0, 0};
                    ((GridBagLayout) panel5.getLayout()).columnWeights = new double[]{0.0, 1.0,
                            1.0E-4};
                    ((GridBagLayout) panel5.getLayout()).rowWeights = new double[]{1.0, 1.0E-4};

                    //---- label8 ----
                    label8.setText("Database:");
                    panel5.add(label8, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
                            GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                            new Insets(0, 0, 0, 0), 0, 0));
                    panel5.add(dbInput, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0,
                            GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                            new Insets(0, 0, 0, 0), 0, 0));
                }
                ConfigPanel.add(panel5, new GridBagConstraints(2, 5, 1, 1, 0.0, 0.0,
                        GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                        new Insets(0, 0, 5, 5), 0, 0));

                //======== panel6 ========
                {
                    panel6.setLayout(new GridBagLayout());
                    ((GridBagLayout) panel6.getLayout()).columnWidths = new int[]{55, 10, 0};
                    ((GridBagLayout) panel6.getLayout()).rowHeights = new int[]{0, 0};
                    ((GridBagLayout) panel6.getLayout()).columnWeights = new double[]{1.0, 1.0,
                            1.0E-4};
                    ((GridBagLayout) panel6.getLayout()).rowWeights = new double[]{0.0, 1.0E-4};

                    //---- connectButton ----
                    connectButton.setText("Connect");
                    connectButton.addActionListener(this::connectButtonActionPerformed);
                    panel6.add(connectButton, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
                            GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                            new Insets(0, 0, 0, 5), 0, 0));

                    //---- disConnectButton ----
                    disConnectButton.setText("Disconnect");
                    disConnectButton.setEnabled(false);
                    disConnectButton.addActionListener(this::disconnectButtonActionPerformed);
                    panel6.add(disConnectButton, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0,
                            GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                            new Insets(0, 0, 0, 0), 0, 0));
                }
                ConfigPanel.add(panel6, new GridBagConstraints(2, 6, 1, 1, 0.0, 0.0,
                        GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                        new Insets(0, 0, 5, 5), 0, 0));
            }
            tabbedPane1.addTab("Config", ConfigPanel);

            //======== QueryPanel ========
            {
                QueryPanel.setLayout(new GridBagLayout());
                ((GridBagLayout) QueryPanel.getLayout()).columnWidths = new int[]{0, 0, 0, 0};
                ((GridBagLayout) QueryPanel.getLayout()).rowHeights = new int[]{25, 0, 0, 0, 0};
                ((GridBagLayout) QueryPanel.getLayout()).columnWeights = new double[]{
                        0.19999999999999998, 0.8, 0.2, 1.0E-4};
                ((GridBagLayout) QueryPanel.getLayout()).rowWeights = new double[]{0.0, 0.0, 0.0,
                        0.0, 1.0E-4};

                //======== panel7 ========
                {
                    panel7.setLayout(new GridBagLayout());
                    ((GridBagLayout) panel7.getLayout()).columnWidths = new int[]{0, 125, 205, 125,
                            55, 105, 0, 0, 0};
                    ((GridBagLayout) panel7.getLayout()).rowHeights = new int[]{0, 0};
                    ((GridBagLayout) panel7.getLayout()).columnWeights = new double[]{0.5, 0.0, 0.0,
                            0.0, 0.0, 0.0, 0.0, 0.5, 1.0E-4};
                    ((GridBagLayout) panel7.getLayout()).rowWeights = new double[]{0.0, 1.0E-4};

                    //---- conditionCheckBox ----
                    conditionCheckBox.setText("Condition:");
                    conditionCheckBox.setSelected(true);
                    conditionCheckBox.addChangeListener(new ChangeListener() {
                        @Override
                        public void stateChanged(ChangeEvent e) {
                            conditionCheckBoxStateChanged(e);
                        }
                    });
                    panel7.add(conditionCheckBox, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0,
                            GridBagConstraints.EAST, GridBagConstraints.VERTICAL,
                            new Insets(0, 0, 0, 5), 0, 0));
                    panel7.add(selectHostInput, new GridBagConstraints(2, 0, 1, 1, 0.0, 0.0,
                            GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                            new Insets(0, 0, 0, 5), 0, 0));

                    //---- tableComboBox ----
                    tableComboBox.setModel(new DefaultComboBoxModel<>(Config.CaATableName));

                    panel7.add(tableComboBox, new GridBagConstraints(3, 0, 1, 1, 0.0, 0.0,
                            GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                            new Insets(0, 0, 0, 5), 0, 0));

                    //---- limitLabel ----
                    limitLabel.setText("Limit:");
                    panel7.add(limitLabel, new GridBagConstraints(4, 0, 1, 1, 0.0, 0.0,
                            GridBagConstraints.EAST, GridBagConstraints.VERTICAL,
                            new Insets(0, 0, 0, 5), 0, 0));

                    //---- limitCountInput ----
                    limitCountInput.setText("100");
                    panel7.add(limitCountInput, new GridBagConstraints(5, 0, 1, 1, 0.0, 0.0,
                            GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                            new Insets(0, 0, 0, 5), 0, 0));

                    //---- queryButton ----
                    queryButton.setText("Query");
                    queryButton.setEnabled(false);
                    queryButton.addActionListener(this::queryButtonActionPerformed);
                    panel7.add(queryButton, new GridBagConstraints(6, 0, 1, 1, 0.0, 0.0,
                            GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                            new Insets(0, 0, 0, 5), 0, 0));
                }
                QueryPanel.add(panel7, new GridBagConstraints(1, 1, 1, 1, 0.0, 0.0,
                        GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                        new Insets(0, 0, 5, 5), 0, 0));

                //======== showPanel ========
                {
                    showPanel.setLayout(new GridBagLayout());
                    ((GridBagLayout) showPanel.getLayout()).columnWidths = new int[]{0, 0, 0, 0, 0,
                            0};
                    ((GridBagLayout) showPanel.getLayout()).rowHeights = new int[]{0, 0};
                    ((GridBagLayout) showPanel.getLayout()).columnWeights = new double[]{0.5, 0.0,
                            0.0,
                            0.0, 0.5, 1.0E-4};
                    ((GridBagLayout) showPanel.getLayout()).rowWeights = new double[]{0.0, 1.0E-4};

                    //---- label1 ----
                    label1.setText("Show:");
                    showPanel.add(label1, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0,
                            GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                            new Insets(0, 0, 0, 5), 0, 0));

                    //---- idCheckBox ----
                    idCheckBox.setText("id");
                    idCheckBox.setSelected(true);
                    idCheckBox.addChangeListener(new ChangeListener() {
                        @Override
                        public void stateChanged(ChangeEvent e) {
                            tableColumnHide(idCheckBox, 0);
                        }
                    });
                    showPanel.add(idCheckBox, new GridBagConstraints(2, 0, 1, 1, 0.0, 0.0,
                            GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                            new Insets(0, 0, 0, 5), 0, 0));

                    //---- countCheckBox ----
                    countCheckBox.setText("count");
                    countCheckBox.setSelected(true);
                    countCheckBox.addChangeListener(new ChangeListener() {
                        @Override
                        public void stateChanged(ChangeEvent e) {
                            tableColumnHide(countCheckBox, 2);
                        }
                    });
                    showPanel.add(countCheckBox, new GridBagConstraints(3, 0, 1, 1, 0.0, 0.0,
                            GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                            new Insets(0, 0, 0, 5), 0, 0));

                    showPanel.setVisible(false);
                }
                QueryPanel.add(showPanel, new GridBagConstraints(1, 2, 1, 1, 0.0, 0.0,
                        GridBagConstraints.EAST, GridBagConstraints.VERTICAL,
                        new Insets(0, 0, 5, 5), 0, 0));

                //======== scrollPane1 ========
                {

                    //---- queryResultTable ----
                    queryResultTable.setModel(new DefaultTableModel());
                    queryResultTable.setColumnSelectionAllowed(true);
                    queryResultTable.setRowSelectionAllowed(false);
                    JTableHeader tableHeader = queryResultTable.getTableHeader();
                    tableHeader.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseReleased(MouseEvent e) {
                            int choose = tableHeader.columnAtPoint(e.getPoint());

                            queryResultTable.addColumnSelectionInterval(choose, choose);

                        }
                    });
                    scrollPane1.setViewportView(queryResultTable);
                }
                QueryPanel.add(scrollPane1, new GridBagConstraints(1, 3, 1, 1, 0.0, 0.0,
                        GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                        new Insets(0, 0, 0, 5), 0, 0));
            }
            tabbedPane1.addTab("Query", QueryPanel);
        }
        add(tabbedPane1, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
                GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                new Insets(0, 0, 0, 0), 0, 0));
        // JFormDesigner - End of component initialization  //GEN-END:initComponents

        // 当配置文件存在时读取内容
        File configFile = new File(Config.CaAConfig);
        if (configFile.isFile() && configFile.exists()) {
            Map<String, String> configMap = ProcessJson.parseJson();
            hostInput.setText(configMap.get("host"));
            portInput.setText(configMap.get("port"));
            userInput.setText(configMap.get("username"));
            pwdInput.setText(configMap.get("password"));
            dbInput.setText(configMap.get("database"));
        }
    }

    // JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables
    private JTabbedPane tabbedPane1;
    private JPanel ConfigPanel;
    private JPanel panel1;
    private JLabel label4;
    private JTextField hostInput;
    private JPanel panel2;
    private JLabel label5;
    private JTextField portInput;
    private JPanel panel3;
    private JLabel label6;
    private JTextField userInput;
    private JPanel panel4;
    private JLabel label7;
    private JTextField pwdInput;
    private JPanel panel5;
    private JLabel label8;
    private JTextField dbInput;
    private JPanel panel6;
    private JButton connectButton;
    private JButton disConnectButton;
    private JPanel QueryPanel;
    private JPanel panel7;
    private JCheckBox conditionCheckBox;
    private JTextField selectHostInput;
    private JComboBox<String> tableComboBox;
    private JLabel limitLabel;
    private JTextField limitCountInput;
    private JButton queryButton;
    private JPanel showPanel;
    private JLabel label1;
    private JCheckBox idCheckBox;
    private JCheckBox countCheckBox;
    private JScrollPane scrollPane1;
    private JTable queryResultTable;
    // JFormDesigner - End of variables declaration  //GEN-END:variables
    private DefaultTableModel model = null;
}
