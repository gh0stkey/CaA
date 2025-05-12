package caa.component;

import burp.api.montoya.MontoyaApi;
import caa.Config;
import caa.component.generator.Generator;
import caa.component.member.DatatablePanel;
import caa.component.member.DisplayMode;
import caa.instances.Database;
import caa.utils.ConfigLoader;
import caa.utils.HttpUtils;
import caa.utils.UITools;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.*;

public class Databoard extends JPanel {
    private static Boolean isMatchHost = false;

    private final MontoyaApi api;
    private final Database db;
    private final ConfigLoader configLoader;
    private final Generator generator;
    private final String defaultText = "Please enter the host";
    private final DefaultComboBoxModel comboBoxModel = new DefaultComboBoxModel();
    private final JComboBox hostComboBox = new JComboBox(comboBoxModel);
    private Map<String, List<String>> hostCache = new HashMap<>();
    private JTextField hostTextField;
    private JComboBox<String> tableComboBox;
    private JComboBox<String> limitComboBox;
    private JPanel dataPanel;
    private SwingWorker<Object, Void> handleComboBoxWorker;

    private String previousHostText = "";

    private final ActionListener actionListener = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
            String selected = tableComboBox.getSelectedItem().toString();
            dataPanel.removeAll();

            if (selected.contains("All")) {
                hostTextField.setEnabled(false);
                handleComboBoxAction(null, "*");
            } else {
                hostTextField.setEnabled(true);
                String host = hostTextField.getText();
                if (host.equals("*")) {
                    hostTextField.setText("");
                    hostTextField.setForeground(Color.BLACK);
                } else if (hostTextField.getForeground().equals(Color.BLACK)) {
                    handleComboBoxAction(null, host);
                }
            }
        }
    };

    public Databoard(MontoyaApi api, Database db, ConfigLoader configLoader, Generator generator) {
        this.api = api;
        this.db = db;
        this.configLoader = configLoader;
        this.generator = generator;

        initComponents();
    }

    private void initComponents() {
        setLayout(new GridBagLayout());
        ((GridBagLayout) getLayout()).columnWidths = new int[]{10, 0, 0, 20, 0};
        ((GridBagLayout) getLayout()).rowHeights = new int[]{0, 65, 20, 0};
        ((GridBagLayout) getLayout()).columnWeights = new double[]{0.0, 0.0, 1.0, 0.0, 1.0E-4};
        ((GridBagLayout) getLayout()).rowWeights = new double[]{0.0, 1.0, 0.0, 1.0E-4};

        tableComboBox = new JComboBox<>();
        tableComboBox.setModel(new DefaultComboBoxModel<>(Config.CaATableName));

        hostComboBox.setMaximumRowCount(5);

        limitComboBox = new JComboBox<>();
        limitComboBox.setModel(new DefaultComboBoxModel<>(new String[]{
                "100",
                "1000",
                "10000",
                "100000"
        }));

        // 添加选项监听器
        tableComboBox.addActionListener(actionListener);
        limitComboBox.addActionListener(actionListener);

        hostTextField = new JTextField();
        hostTextField.setText(defaultText);
        hostTextField.setForeground(Color.GRAY);

        UITools.setTextFieldPlaceholder(hostTextField, defaultText);

        dataPanel = new JPanel();
        dataPanel.setLayout(new BorderLayout());

        add(tableComboBox, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(8, 0, 5, 5), 0, 0));
        add(hostTextField, new GridBagConstraints(2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(8, 0, 5, 5), 0, 0));
        add(limitComboBox, new GridBagConstraints(3, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(8, 0, 5, 5), 0, 0));
        add(dataPanel, new GridBagConstraints(1, 1, 4, 3, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(8, 0, 5, 5), 0, 0));

        add(hostComboBox, new GridBagConstraints(2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(8, 0, 5, 5), 0, 0));

        setAutoMatch();
    }

    private void setAutoMatch() {
        hostComboBox.setSelectedItem(null);
        hostComboBox.addActionListener(e -> handleComboBoxAction(e, ""));

        hostTextField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleKeyEvents(e);
            }
        });

        hostTextField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filterComboBoxList();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filterComboBoxList();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filterComboBoxList();
            }
        });
    }

    private void filterComboBoxList() {
        isMatchHost = true;
        comboBoxModel.removeAllElements();
        String tableName = tableComboBox.getSelectedItem().toString();
        String input = hostTextField.getText().toLowerCase();

        if (!input.isEmpty() && !input.equals("*")) {
            for (String host : getHostByList(tableName)) {
                String lowerCaseHost = host.toLowerCase();
                if (lowerCaseHost.contains(input)) {
                    if (lowerCaseHost.equals(input)) {
                        comboBoxModel.insertElementAt(lowerCaseHost, 0);
                        comboBoxModel.setSelectedItem(lowerCaseHost);
                    } else {
                        comboBoxModel.addElement(host);
                    }

                    previousHostText = input;
                }
            }
        }

        hostComboBox.setPopupVisible(comboBoxModel.getSize() > 0);
        isMatchHost = false;
    }

    private void handleComboBoxAction(ActionEvent e, String host) {
        if (!isMatchHost) {
            String tableName = tableComboBox.getSelectedItem().toString();
            String selectedHost;
            Object selectedItem = hostComboBox.getSelectedItem();

            if (host.equals("*")) {
                selectedHost = "*";
            } else if (getHostByList(tableName).contains(selectedItem.toString())) {
                selectedHost = selectedItem.toString();
            } else {
                selectedHost = "";
            }

            if (!selectedHost.isBlank() || selectedHost.equals("*")) {
                if (handleComboBoxWorker != null && !handleComboBoxWorker.isDone()) {
                    handleComboBoxWorker.cancel(true);
                }

                handleComboBoxWorker = new SwingWorker<Object, Void>() {
                    @Override
                    protected Object doInBackground() {
                        String limitSize = limitComboBox.getSelectedItem().toString();
                        dataPanel.removeAll();
                        return db.selectData(selectedHost.equals("*") ? "" : selectedHost, tableName, limitSize);
                    }

                    @Override
                    protected void done() {
                        if (!isCancelled()) {
                            try {
                                Object selectedObject = get();

                                if (tableName.equals("Value")) {
                                    List<String> columnNameB = new ArrayList<>();
                                    columnNameB.add("Name");
                                    columnNameB.add("Value");
                                    columnNameB.add("Count");
                                    dataPanel.add(new DatatablePanel(api, db, configLoader, generator, columnNameB, selectedObject, null, tableName, DisplayMode.COUNT), BorderLayout.CENTER);
                                } else {
                                    List<String> columnNameA = new ArrayList<>();
                                    columnNameA.add("Name");
                                    columnNameA.add("Count");
                                    dataPanel.add(new DatatablePanel(api, db, configLoader, generator, columnNameA, selectedObject, null, tableName, DisplayMode.COUNT), BorderLayout.CENTER);
                                }

                                if (!selectedHost.equals(previousHostText)) {
                                    hostTextField.setText(selectedHost);
                                }
                                hostComboBox.setPopupVisible(false);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                };

                handleComboBoxWorker.execute();
            }
        }
    }

    private List<String> getHostByList(String tableName) {
        // 先从缓存中查找
        if (hostCache.containsKey(tableName)) {
            return hostCache.get(tableName);
        }

        // 缓存中没有，则查询数据库
        List<String> hosts = db.getAllHosts(tableName);
        hosts = new ArrayList<>(new HashSet<>(hosts)); // 去重

        // 用于暂存新生成的 anyHost
        List<String> newAnyHosts = new ArrayList<>();

        // 添加通配符Host
        for (String host : hosts) {
            if (!HttpUtils.matchHostIsIp(host)) {
                String[] splitHost = host.split("\\.");
                if (splitHost.length > 2) {
                    String anyHost = HttpUtils.replaceFirstOccurrence(host, splitHost[0], "*");
                    if (!anyHost.isEmpty()) {
                        newAnyHosts.add(anyHost);
                    }
                }
            }
        }

        // 统一添加所有新生成的 anyHost
        hosts.addAll(newAnyHosts);

        // 再次去重
        hosts = new ArrayList<>(new HashSet<>(hosts));

        hostCache.put(tableName, hosts);
        return hosts;
    }

    private void handleKeyEvents(KeyEvent e) {
        isMatchHost = true;
        int keyCode = e.getKeyCode();

        if (keyCode == KeyEvent.VK_SPACE && hostComboBox.isPopupVisible()) {
            e.setKeyCode(KeyEvent.VK_ENTER);
        }

        if (Arrays.asList(KeyEvent.VK_DOWN, KeyEvent.VK_UP).contains(keyCode)) {
            hostComboBox.dispatchEvent(e);
        }

        if (keyCode == KeyEvent.VK_ENTER) {
            isMatchHost = false;
            handleComboBoxAction(null, "");
        }

        if (keyCode == KeyEvent.VK_ESCAPE) {
            hostComboBox.setPopupVisible(false);
        }

        isMatchHost = false;
    }
}
