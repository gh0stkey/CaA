package caa.component;

import burp.api.montoya.MontoyaApi;
import caa.Config;
import caa.component.datatable.Datatable;
import caa.component.datatable.Mode;
import caa.component.generator.Generator;
import caa.instances.Database;
import caa.utils.ConfigLoader;
import caa.utils.HttpUtils;
import caa.utils.UIEnhancer;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;

public class Databoard extends JPanel {
    private boolean isMatchHost = false;

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
            Object selectedItem = tableComboBox.getSelectedItem();
            if (selectedItem == null) {
                return;
            }
            String selected = selectedItem.toString();
            
            // 立即清空面板并刷新UI，防止旧数据停留
            dataPanel.removeAll();
            dataPanel.revalidate();
            dataPanel.repaint();

            if (selected.contains("All")) {
                hostTextField.setEnabled(false);
                handleComboBoxAction(null, "*");
            } else {
                hostTextField.setEnabled(true);
                String host = hostTextField.getText();
                if (host != null) {
                    if (host.equals("*")) {
                        hostTextField.setText("");
                        hostTextField.setForeground(Color.BLACK);
                    } else if (hostTextField.getForeground().equals(Color.BLACK)) {
                        handleComboBoxAction(null, host);
                    }
                }
            }
        }
    };

    public Databoard(MontoyaApi api, Database db, ConfigLoader configLoader, Generator generator) {
        this.api = api;
        this.db = db;
        this.configLoader = configLoader;
        this.generator = generator;

        // 注册缓存失效回调，当数据库插入数据时自动清空缓存
        db.setCacheInvalidationCallback(this::clearHostCache);

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

        UIEnhancer.setTextFieldPlaceholder(hostTextField, defaultText);

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
        Object tableItem = tableComboBox.getSelectedItem();
        if (tableItem == null) {
            isMatchHost = false;
            return;
        }
        String tableName = tableItem.toString();
        String input = hostTextField.getText();
        if (input == null) {
            isMatchHost = false;
            return;
        }
        input = input.toLowerCase();

        if (!input.isEmpty() && !input.equals("*")) {
            for (String host : getHostByList(tableName, input)) {
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
            Object tableItem = tableComboBox.getSelectedItem();
            if (tableItem == null) {
                return;
            }
            String tableName = tableItem.toString();
            String selectedHost;
            Object selectedItem = hostComboBox.getSelectedItem();

            if (host.equals("*")) {
                selectedHost = "*";
            } else if (selectedItem != null && getHostByList(tableName, selectedItem.toString()).contains(selectedItem.toString())) {
                selectedHost = selectedItem.toString();
            } else {
                selectedHost = "";
            }

            if (!selectedHost.isBlank() || selectedHost.equals("*")) {
                if (handleComboBoxWorker != null && !handleComboBoxWorker.isDone()) {
                    handleComboBoxWorker.cancel(true);
                }

                // 立即显示加载提示
                dataPanel.removeAll();
                JLabel loadingLabel = new JLabel("Loading...", SwingConstants.CENTER);
                loadingLabel.setForeground(Color.GRAY);
                dataPanel.add(loadingLabel, BorderLayout.CENTER);
                dataPanel.revalidate();
                dataPanel.repaint();

                handleComboBoxWorker = new SwingWorker<Object, Void>() {
                    @Override
                    protected Object doInBackground() {
                        String limitSize = limitComboBox.getSelectedItem().toString();
                        return db.selectData(selectedHost.equals("*") ? "" : selectedHost, tableName, limitSize);
                    }

                    @Override
                    protected void done() {
                        if (!isCancelled()) {
                            try {
                                Object selectedObject = get();
                                
                                dataPanel.removeAll();
                                
                                // 检查数据是否为空
                                boolean hasData = false;
                                if (selectedObject != null) {
                                    if (selectedObject instanceof Map) {
                                        hasData = !((Map<?, ?>) selectedObject).isEmpty();
                                    } else if (selectedObject instanceof com.google.common.collect.SetMultimap) {
                                        hasData = !((com.google.common.collect.SetMultimap<?, ?>) selectedObject).isEmpty();
                                    }
                                }
                                
                                if (hasData) {
                                    Datatable datatableComponent;
                                    if (tableName.equals("Value")) {
                                        List<String> columnNameB = new ArrayList<>();
                                        columnNameB.add("Name");
                                        columnNameB.add("Value");
                                        columnNameB.add("Count");
                                        datatableComponent = new Datatable(api, db, configLoader, generator, columnNameB, selectedObject, null, tableName, Mode.COUNT);
                                    } else {
                                        List<String> columnNameA = new ArrayList<>();
                                        columnNameA.add("Name");
                                        columnNameA.add("Count");
                                        datatableComponent = new Datatable(api, db, configLoader, generator, columnNameA, selectedObject, null, tableName, Mode.COUNT);
                                    }
                                    // 设置当前host，用于删除操作
                                    datatableComponent.setCurrentHost(selectedHost.equals("*") ? "" : selectedHost);
                                    dataPanel.add(datatableComponent, BorderLayout.CENTER);
                                } else {
                                    JLabel noDataLabel = new JLabel("No data found", SwingConstants.CENTER);
                                    noDataLabel.setForeground(Color.GRAY);
                                    noDataLabel.setFont(noDataLabel.getFont().deriveFont(14f));
                                    dataPanel.add(noDataLabel, BorderLayout.CENTER);
                                }
                                
                                dataPanel.revalidate();
                                dataPanel.repaint();

                                if (!selectedHost.equals(previousHostText)) {
                                    hostTextField.setText(selectedHost);
                                }
                                hostComboBox.setPopupVisible(false);
                            } catch (Exception ex) {
                                api.logging().logToError("Failed to process data panel: " + ex.getMessage());
                                dataPanel.removeAll();
                                JLabel errorLabel = new JLabel("Error loading data", SwingConstants.CENTER);
                                errorLabel.setForeground(Color.RED);
                                dataPanel.add(errorLabel, BorderLayout.CENTER);
                                dataPanel.revalidate();
                                dataPanel.repaint();
                            }
                        }
                    }
                };

                handleComboBoxWorker.execute();
            }
        }
    }

    private List<String> getHostByList(String tableName, String hostName) {
        // 先从缓存中查找
        if (hostCache.containsKey(tableName)) {
            return hostCache.get(tableName);
        }

        // 缓存中没有，则查询数据库
        List<String> hosts = db.getAllHosts(tableName);
        if (hosts == null || hosts.isEmpty()) {
            return new ArrayList<>();
        }

        // 使用Set进行去重，提高性能
        Set<String> hostSet = new LinkedHashSet<>(hosts);

        // 添加通配符Host
        List<String> wildcardHosts = new ArrayList<>();
        for (String host : hostSet) {
            if (!HttpUtils.matchHostIsIp(host)) {
                String[] splitHost = host.split("\\.");
                if (splitHost.length > 2) {
                    String anyHost = HttpUtils.replaceFirstOccurrence(host, splitHost[0], "*");
                    if (!anyHost.isEmpty() && !anyHost.equals(host)) {
                        wildcardHosts.add(anyHost);
                    }
                }
            }
        }

        // 添加通配符主机
        hostSet.addAll(wildcardHosts);

        // 转换为List并缓存
        List<String> result = new ArrayList<>(hostSet);
        hostCache.put(tableName, result);
        return result;
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
    
    public void clearHostCache() {
        hostCache.clear();
    }
}
