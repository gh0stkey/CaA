package caa.component.generator;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.UserInterface;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import caa.Config;
import caa.component.WrapLayout;
import caa.instances.generator.GeneratorConfig;
import caa.instances.generator.ParamLocation;
import caa.instances.generator.PayloadIterator;
import caa.utils.ConfigLoader;
import caa.utils.HttpUtils;
import caa.utils.UIEnhancer;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

public class Tab extends JPanel {

    private final MontoyaApi api;
    private final HttpUtils httpUtils;
    private final HttpRequest httpRequest;

    private JCheckBox endpointCheckBox;
    private JComboBox<String> endpointTypeComboBox;
    private DefaultTableModel endpointTableModel;
    private JTable endpointTable;
    private JPanel endpointContentPanel;

    private JCheckBox paramCheckBox;
    private JComboBox<String> paramTypeComboBox;
    private DefaultTableModel paramTableModel;
    private JTable paramTable;
    private JPanel paramContentPanel;

    private JComboBox<String> valueTypeComboBox;
    private JTextField valueInputField;
    private JTextField valueLengthField;

    private JCheckBox methodGet;
    private JCheckBox methodPostForm;
    private JCheckBox methodPostJson;
    private JCheckBox methodPostMultipart;
    private JCheckBox methodPostXml;

    private JComboBox<ParamLocation> postParamLocation;

    private JLabel endpointCountLabel;
    private JLabel paramCountLabel;
    private JLabel estimationLabel;

    private JTextField urlField;
    private HttpRequestEditor requestEditor;

    private JButton generateButton;

    private final String initPayloadType;
    private final String initPayloads;
    private final boolean initIsEndpoint;

    public Tab(
        MontoyaApi api,
        ConfigLoader configLoader,
        HttpRequest httpRequest,
        String payloadType,
        String payloads,
        boolean isEndpointDimension
    ) {
        this.api = api;
        this.httpUtils = new HttpUtils(api, configLoader);
        this.httpRequest = httpRequest;
        this.initPayloadType = payloadType;
        this.initPayloads = payloads;
        this.initIsEndpoint = isEndpointDimension;

        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.addComponentListener(
            new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    splitPane.setDividerLocation(0.4);
                }
            }
        );

        splitPane.setLeftComponent(createLeftPanel());
        splitPane.setRightComponent(createRightPanel());

        add(splitPane, BorderLayout.CENTER);
        applyInitialData();
    }

    private JPanel createLeftPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(new EmptyBorder(5, 5, 5, 5));

        JPanel urlPanel = new JPanel(new BorderLayout(10, 0));
        urlField = new JTextField(httpRequest.url());
        UIEnhancer.addSimpleDocumentListener(urlField, () -> {
            try {
                HttpService httpService = HttpService.httpService(
                    urlField.getText()
                );
                requestEditor.setRequest(
                    HttpRequest.httpRequest(
                        httpService,
                        requestEditor.getRequest().toByteArray()
                    )
                );
            } catch (Exception ignored) {}
        });

        JPanel generatePanel = new JPanel(new BorderLayout(5, 0));
        generateButton = new JButton("Generate");
        estimationLabel = new JLabel("0 requests");
        estimationLabel.setForeground(Color.GRAY);
        generatePanel.add(generateButton, BorderLayout.WEST);
        generatePanel.add(estimationLabel, BorderLayout.CENTER);
        generateButton.addActionListener(e -> performGenerate());

        urlPanel.add(new JLabel("URL:"), BorderLayout.WEST);
        urlPanel.add(urlField, BorderLayout.CENTER);
        urlPanel.add(generatePanel, BorderLayout.EAST);

        JPanel requestPanel = new JPanel(new BorderLayout(0, 5));
        JLabel requestLabel = new JLabel("Request");
        requestLabel.setFont(
            new Font(requestLabel.getFont().getName(), Font.BOLD, 14)
        );

        UserInterface userInterface = api.userInterface();
        requestEditor = userInterface.createHttpRequestEditor();
        requestEditor.setRequest(httpRequest);
        requestPanel.add(requestLabel, BorderLayout.NORTH);
        requestPanel.add(requestEditor.uiComponent(), BorderLayout.CENTER);
        requestPanel.add(createMethodsPanel(), BorderLayout.SOUTH);

        panel.add(urlPanel, BorderLayout.NORTH);
        panel.add(requestPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createRightPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(5, 5, 5, 5));

        JSplitPane dimensionSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        dimensionSplit.addComponentListener(
            new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    dimensionSplit.setDividerLocation(0.5);
                }
            }
        );

        dimensionSplit.setTopComponent(createEndpointDimensionPanel());
        dimensionSplit.setBottomComponent(createParamDimensionPanel());

        panel.add(dimensionSplit, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createDimensionPanel(
        JCheckBox checkBox,
        JLabel countLabel,
        JComboBox<String> typeComboBox,
        DefaultTableModel tableModel,
        JTable table,
        JPanel contentPanel,
        String placeholder
    ) {
        JPanel wrapper = new JPanel(new BorderLayout());

        checkBox.setFont(new Font(checkBox.getFont().getName(), Font.BOLD, 13));
        checkBox.addItemListener(e -> {
            UIEnhancer.setContainerEnabled(contentPanel, checkBox.isSelected());
            if (paramCheckBox != null && paramCheckBox.isSelected()) {
                updateValueEnabled();
            }
            updateEstimation();
        });

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.add(checkBox, BorderLayout.WEST);
        countLabel.setForeground(Color.GRAY);
        headerPanel.add(countLabel, BorderLayout.CENTER);

        contentPanel.setBorder(new EmptyBorder(3, 0, 0, 0));

        JPanel typePanel = new JPanel(new BorderLayout(10, 0));
        typePanel.add(new JLabel("Type:"), BorderLayout.WEST);
        typePanel.add(typeComboBox, BorderLayout.CENTER);
        typePanel.setBorder(new EmptyBorder(0, 0, 3, 0));

        JTextField inputField = new JTextField();
        UIEnhancer.setTextFieldPlaceholder(inputField, placeholder);

        JPanel inputPanel = new JPanel(new BorderLayout(10, 0));
        inputPanel.add(new JLabel("Input:"), BorderLayout.WEST);
        inputPanel.add(inputField, BorderLayout.CENTER);

        JPanel topInput = new JPanel(new BorderLayout());
        topInput.setBorder(new EmptyBorder(0, 0, 3, 0));
        topInput.add(typePanel, BorderLayout.NORTH);
        topInput.add(inputPanel, BorderLayout.CENTER);

        tableModel.addColumn("Name");
        JScrollPane scrollPane = new JScrollPane(table);
        tableModel.addTableModelListener(e -> updateEstimation());

        // 搜索框（表格底部）
        JTextField searchField = new JTextField();
        UIEnhancer.setTextFieldPlaceholder(searchField, "Search");
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setBorder(new EmptyBorder(3, 0, 0, 0));
        searchPanel.add(searchField, BorderLayout.CENTER);

        // 表格排序和过滤
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(
            tableModel
        );
        table.setRowSorter(sorter);
        UIEnhancer.addSimpleDocumentListener(searchField, () -> {
            if (UIEnhancer.hasUserInput(searchField)) {
                String text = searchField.getText().toLowerCase();
                sorter.setRowFilter(RowFilter.regexFilter("(?i)" + text, 0));
            } else {
                sorter.setRowFilter(null);
            }
        });

        JButton addBtn = new JButton("Add");
        JButton removeBtn = new JButton("Remove");
        JButton pasteBtn = new JButton("Paste");
        JButton clearBtn = new JButton("Clear");

        UIEnhancer.bindAddAction(
            addBtn,
            inputField,
            tableModel,
            this::addDataToTable
        );
        UIEnhancer.addButtonListener(
            pasteBtn,
            removeBtn,
            clearBtn,
            table,
            tableModel,
            this::addDataToTable
        );

        JPanel buttonPanel = UIEnhancer.createButtonColumn(
            addBtn,
            removeBtn,
            pasteBtn,
            clearBtn
        );

        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.add(topInput, BorderLayout.NORTH);
        tablePanel.add(scrollPane, BorderLayout.CENTER);
        tablePanel.add(searchPanel, BorderLayout.SOUTH);

        contentPanel.add(tablePanel, BorderLayout.CENTER);
        contentPanel.add(buttonPanel, BorderLayout.EAST);

        wrapper.add(headerPanel, BorderLayout.NORTH);
        wrapper.add(contentPanel, BorderLayout.CENTER);

        return wrapper;
    }

    private JPanel createEndpointDimensionPanel() {
        endpointCheckBox = new JCheckBox("Endpoint", true);
        endpointCountLabel = new JLabel("");
        endpointTypeComboBox = new JComboBox<>(
            new String[] { "FullPath", "Path", "File" }
        );
        endpointTableModel = new DefaultTableModel();
        endpointTable = new JTable(endpointTableModel);
        endpointContentPanel = new JPanel(new BorderLayout());

        JPanel panel = createDimensionPanel(
            endpointCheckBox,
            endpointCountLabel,
            endpointTypeComboBox,
            endpointTableModel,
            endpointTable,
            endpointContentPanel,
            "Enter a new item"
        );

        endpointTypeComboBox.addActionListener(e -> updateEstimation());

        return panel;
    }

    private JPanel createParamDimensionPanel() {
        paramCheckBox = new JCheckBox("Parameter", true);
        paramCountLabel = new JLabel("");
        paramTypeComboBox = new JComboBox<>(new String[] { "Param", "Value" });
        paramTableModel = new DefaultTableModel();
        paramTable = new JTable(paramTableModel);
        paramContentPanel = new JPanel(new BorderLayout());

        JPanel panel = createDimensionPanel(
            paramCheckBox,
            paramCountLabel,
            paramTypeComboBox,
            paramTableModel,
            paramTable,
            paramContentPanel,
            "Enter a new item"
        );

        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem renameMenuItem = new JMenuItem("Rename");
        popupMenu.add(renameMenuItem);
        renameMenuItem.addActionListener(e -> {
            int[] selectedRows = paramTable.getSelectedRows();
            String newName = JOptionPane.showInputDialog(
                this,
                "Please enter the new name."
            );
            if (newName != null && !newName.isBlank()) {
                for (int row : selectedRows) {
                    paramTable.getModel().setValueAt(newName, row, 0);
                }
            }
        });

        MouseAdapter mouseAdapter = new MouseAdapter() {
            public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    popupMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        };

        paramTypeComboBox.addActionListener(e -> {
            String selected = paramTypeComboBox.getSelectedItem().toString();
            if (selected.equals("Value")) {
                if (paramTableModel.getColumnCount() < 2) {
                    paramTableModel.addColumn("Value");
                }
                paramTableModel.setColumnCount(2);
                paramTable.addMouseListener(mouseAdapter);
            } else {
                paramTableModel.setColumnCount(1);
                paramTable.removeMouseListener(mouseAdapter);
            }
            updateValueEnabled();
        });

        Component tablePanel = (
            (BorderLayout) paramContentPanel.getLayout()
        ).getLayoutComponent(BorderLayout.CENTER);
        paramContentPanel.remove(tablePanel);
        JPanel tableAndConfig = new JPanel(new BorderLayout());
        tableAndConfig.add(tablePanel, BorderLayout.CENTER);
        tableAndConfig.add(createValueConfigPanel(), BorderLayout.SOUTH);
        paramContentPanel.add(tableAndConfig, BorderLayout.CENTER);

        // 在Type行添加Location选项
        postParamLocation = new JComboBox<>(ParamLocation.values());
        postParamLocation.setSelectedItem(ParamLocation.BOTH);
        postParamLocation.addActionListener(e -> updateEstimation());

        JPanel locationPanel = new JPanel(
            new FlowLayout(FlowLayout.RIGHT, 5, 0)
        );
        locationPanel.add(new JLabel("Location:"));
        locationPanel.add(postParamLocation);

        Container typePanel = paramTypeComboBox.getParent();
        if (typePanel instanceof JPanel) {
            ((JPanel) typePanel).add(locationPanel, BorderLayout.EAST);
        }

        return panel;
    }

    private JPanel createValueConfigPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Value",
                TitledBorder.LEFT,
                TitledBorder.TOP
            )
        );

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(2, 5, 2, 5);
        c.gridy = 0;

        valueTypeComboBox = new JComboBox<>(
            new String[] { "Random", "Custom" }
        );
        valueTypeComboBox.addActionListener(e -> {
            String selected = valueTypeComboBox.getSelectedItem().toString();
            valueLengthField.setEnabled(selected.equals("Random"));
        });

        valueInputField = new JTextField(Config.alphanumericChars);
        valueLengthField = new JTextField(String.valueOf(Config.defaultLength));
        valueLengthField.setColumns(4);

        c.gridx = 0;
        c.weightx = 0;
        panel.add(new JLabel("Type:"), c);

        c.gridx = 1;
        c.fill = GridBagConstraints.NONE;
        panel.add(valueTypeComboBox, c);

        c.gridx = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(new JLabel("  Input:"), c);

        c.gridx = 3;
        c.weightx = 1.0;
        panel.add(valueInputField, c);

        c.gridx = 4;
        c.weightx = 0;
        panel.add(new JLabel("  Length:"), c);

        c.gridx = 5;
        c.fill = GridBagConstraints.NONE;
        panel.add(valueLengthField, c);

        return panel;
    }

    private JPanel createMethodsPanel() {
        JPanel panel = new JPanel(new WrapLayout(FlowLayout.LEFT, 10, 2));
        panel.setBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Methods",
                TitledBorder.LEFT,
                TitledBorder.TOP
            )
        );

        methodGet = new JCheckBox("GET", true);
        methodPostForm = new JCheckBox("POST Form", true);
        methodPostJson = new JCheckBox("POST JSON", true);
        methodPostMultipart = new JCheckBox("POST Multipart", true);
        methodPostXml = new JCheckBox("POST XML", true);

        methodGet.addItemListener(e -> updateEstimation());
        methodPostForm.addItemListener(e -> updateEstimation());
        methodPostJson.addItemListener(e -> updateEstimation());
        methodPostMultipart.addItemListener(e -> updateEstimation());
        methodPostXml.addItemListener(e -> updateEstimation());

        panel.add(methodGet);
        panel.add(methodPostForm);
        panel.add(methodPostJson);
        panel.add(methodPostMultipart);
        panel.add(methodPostXml);

        return panel;
    }

    private int getExpandedEndpointCount() {
        List<String> endpoints = getTableDataAsList(endpointTable);
        if (endpoints.isEmpty()) {
            return 0;
        }
        String type = endpointTypeComboBox.getSelectedItem().toString();
        String basePath = requestEditor.getRequest().path();
        return PayloadIterator.expandEndpoints(
            basePath,
            endpoints,
            type
        ).size();
    }

    private int getSelectedMethodCount() {
        int count = 0;
        ParamLocation location =
            (ParamLocation) postParamLocation.getSelectedItem();
        int postMultiplier = (location == ParamLocation.BOTH) ? 2 : 1;

        if (methodGet.isSelected()) count++;
        if (methodPostForm.isSelected()) count += postMultiplier;
        if (methodPostJson.isSelected()) count += postMultiplier;
        if (methodPostMultipart.isSelected()) count += postMultiplier;
        if (methodPostXml.isSelected()) count += postMultiplier;
        return count;
    }

    // 计算参数迭代次数
    private int getParamIterationCount() {
        if (paramTableModel.getRowCount() == 0) {
            return 0;
        }

        boolean isValueMode = "Value".equals(
            paramTypeComboBox.getSelectedItem()
        );

        if (isValueMode) {
            // Value 模式：累积笛卡尔积
            Map<String, Integer> valueCounts = new LinkedHashMap<>();
            for (int row = 0; row < paramTableModel.getRowCount(); row++) {
                Object nameObj = paramTableModel.getValueAt(row, 0);
                if (nameObj == null) continue;
                String name = nameObj.toString();
                valueCounts.merge(name, 1, Integer::sum);
            }
            return calculateCumulativeCartesian(valueCounts);
        } else {
            return paramTableModel.getRowCount();
        }
    }

    // 从参数列表计算迭代次数
    private int calculateParamIterations(
        List<String> params,
        boolean isValueMode
    ) {
        if (params.isEmpty()) {
            return 0;
        }

        if (isValueMode) {
            // Value 模式：累积笛卡尔积
            Map<String, Integer> valueCounts = new LinkedHashMap<>();
            for (String item : params) {
                String name = item.contains("=") ? item.split("=", 2)[0] : item;
                valueCounts.merge(name, 1, Integer::sum);
            }
            return calculateCumulativeCartesian(valueCounts);
        } else {
            return params.size();
        }
    }

    // 累积笛卡尔积: sum(product(counts[0..i]) for i in 0..n-1)
    private int calculateCumulativeCartesian(Map<String, Integer> valueCounts) {
        if (valueCounts.isEmpty()) {
            return 0;
        }
        int total = 0;
        int product = 1;
        for (int count : valueCounts.values()) {
            product *= count;
            total += product;
        }
        return total;
    }

    private Map<String, ParamLocation> getSelectedMethods() {
        Map<String, ParamLocation> methods = new HashMap<>();
        ParamLocation location =
            (ParamLocation) postParamLocation.getSelectedItem();
        if (methodGet.isSelected()) methods.put(
            "GET",
            ParamLocation.QUERY_ONLY
        );
        if (methodPostForm.isSelected()) methods.put("POST Form", location);
        if (methodPostJson.isSelected()) methods.put("POST JSON", location);
        if (methodPostMultipart.isSelected()) methods.put(
            "POST Multipart",
            location
        );
        if (methodPostXml.isSelected()) methods.put("POST XML", location);
        return methods;
    }

    private void updateValueEnabled() {
        // Param 模式或 Value 模式都启用 Value Type 配置
        // Value 模式下用于给没有值的参数生成值
        boolean enabled = paramCheckBox.isSelected();
        valueTypeComboBox.setEnabled(enabled);
        valueInputField.setEnabled(enabled);
        valueLengthField.setEnabled(
            enabled && "Random".equals(valueTypeComboBox.getSelectedItem())
        );
    }

    private void updateEstimation() {
        boolean epEnabled =
            endpointCheckBox.isSelected() &&
            endpointTableModel.getRowCount() > 0;
        boolean pmEnabled =
            paramCheckBox.isSelected() && paramTableModel.getRowCount() > 0;

        int endpointCount = epEnabled ? getExpandedEndpointCount() : 0;
        int paramCombinations = getParamIterationCount();
        int methodCount = getSelectedMethodCount();

        int epRows = endpointTableModel.getRowCount();
        int pmRows = paramTableModel.getRowCount();
        endpointCountLabel.setText(epRows > 0 ? "(" + epRows + " items)" : "");
        paramCountLabel.setText(pmRows > 0 ? "(" + pmRows + " items)" : "");

        int total;
        if (epEnabled && pmEnabled) {
            total = endpointCount * paramCombinations * methodCount;
        } else if (pmEnabled) {
            total = paramCombinations * methodCount;
        } else if (epEnabled) {
            total = endpointCount * methodCount;
        } else {
            total = 0;
        }

        estimationLabel.setText(total + " requests");
    }

    private void performGenerate() {
        boolean epEnabled = endpointCheckBox.isSelected();
        boolean pmEnabled = paramCheckBox.isSelected();

        if (!epEnabled && !pmEnabled) {
            JOptionPane.showMessageDialog(
                this,
                "Please enable at least one dimension (Endpoint or Parameter).",
                "CaA",
                JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        List<String> endpoints = epEnabled
            ? getTableDataAsList(endpointTable)
            : List.of();
        List<String> params = pmEnabled
            ? getTableDataAsList(paramTable)
            : List.of();

        if (epEnabled && endpoints.isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                "Endpoint dimension is enabled but has no payloads.",
                "CaA",
                JOptionPane.ERROR_MESSAGE
            );
            return;
        }
        if (pmEnabled && params.isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                "Parameter dimension is enabled but has no payloads.",
                "CaA",
                JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        HttpService httpService = HttpService.httpService(urlField.getText());
        HttpRequest targetRequest = HttpRequest.httpRequest(
            httpService,
            requestEditor.getRequest().toByteArray()
        );

        // 构建并保存配置（延迟生成）
        String paramType = paramTypeComboBox.getSelectedItem().toString();
        String endpointType = epEnabled
            ? endpointTypeComboBox.getSelectedItem().toString()
            : "";
        GeneratorConfig config = new GeneratorConfig(
            targetRequest,
            endpoints,
            endpointType,
            params,
            paramType,
            valueTypeComboBox.getSelectedItem().toString(),
            valueInputField.getText(),
            Integer.parseInt(valueLengthField.getText()),
            getSelectedMethods()
        );

        Config.generatorConfig = config;

        // 计算参数迭代次数
        boolean isValueMode = "Value".equals(paramType);
        int paramCombinations = calculateParamIterations(params, isValueMode);
        int expandedEndpointCount = epEnabled
            ? PayloadIterator.expandEndpoints(
                  targetRequest.path(),
                  endpoints,
                  endpointType
              ).size()
            : 0;
        int estimation = calculateEstimation(
            epEnabled,
            pmEnabled,
            expandedEndpointCount,
            paramCombinations
        );

        JOptionPane.showMessageDialog(
            this,
            String.format(
                "Configuration saved! Estimated %d payload(s).",
                estimation
            ),
            "CaA",
            JOptionPane.INFORMATION_MESSAGE
        );
    }

    private int calculateEstimation(
        boolean epEnabled,
        boolean pmEnabled,
        int endpointCount,
        int paramCombinations
    ) {
        int methodCount = getSelectedMethodCount();
        if (epEnabled && pmEnabled) {
            return endpointCount * paramCombinations * methodCount;
        } else if (pmEnabled) {
            return paramCombinations * methodCount;
        } else if (epEnabled) {
            return endpointCount * methodCount;
        }
        return 0;
    }

    private List<String> getTableDataAsList(JTable table) {
        List<String> items = new ArrayList<>();
        for (int row = 0; row < table.getRowCount(); row++) {
            if (
                table.getColumnCount() >= 2 && table.getValueAt(row, 1) != null
            ) {
                items.add(
                    String.format(
                        "%s=%s",
                        table.getValueAt(row, 0),
                        table.getValueAt(row, 1)
                    )
                );
            } else {
                items.add(table.getValueAt(row, 0).toString());
            }
        }
        return items;
    }

    private void applyInitialData() {
        if (initPayloads == null || initPayloads.isEmpty()) {
            return;
        }

        if (initIsEndpoint) {
            endpointCheckBox.setSelected(true);
            endpointTypeComboBox.setSelectedItem(initPayloadType);
            addDataToTable(initPayloads, endpointTableModel);
        } else {
            paramCheckBox.setSelected(true);
            paramTypeComboBox.setSelectedItem(initPayloadType);
            addDataToTable(initPayloads, paramTableModel);
        }
    }

    private void addDataToTable(String data, DefaultTableModel model) {
        UIEnhancer.addDataToTable(
            data,
            model,
            true,
            httpUtils::decodeParameter
        );
    }
}
