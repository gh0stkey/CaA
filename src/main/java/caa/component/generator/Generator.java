package caa.component.generator;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import caa.utils.ConfigLoader;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class Generator extends JTabbedPane {
    private final MontoyaApi api;
    private final ConfigLoader configLoader;

    private final JTextField tabNameTextField;
    private Component tabComponent;
    private int selectedIndex;
    private final Action cancelActionPerformed = new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (selectedIndex >= 0) {
                setTabComponentAt(selectedIndex, tabComponent);

                tabNameTextField.setVisible(false);
                tabNameTextField.setPreferredSize(null);
                selectedIndex = -1;
                tabComponent = null;

                requestFocusInWindow();
            }
        }
    };
    private final Action renameTitleActionPerformed = new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
            String title = tabNameTextField.getText();
            if (!title.isEmpty() && selectedIndex >= 0) {
                setTitleAt(selectedIndex, title);
            }
            cancelActionPerformed.actionPerformed(null);
        }
    };
    private int newTabIndex = 0;

    public Generator(MontoyaApi api, ConfigLoader configLoader) {
        this.api = api;
        this.configLoader = configLoader;
        this.tabNameTextField = new JTextField();

        initComponents();
    }

    private void initComponents() {
        addTab("...", null);
        insertNewTab();

        JMenuItem deleteMenuItem = new JMenuItem("Delete");
        JPopupMenu popupMenu = new JPopupMenu();
        popupMenu.add(deleteMenuItem);

        deleteMenuItem.addActionListener(this::deleteTabActionPerformed);

        tabNameTextField.setBorder(BorderFactory.createEmptyBorder());
        tabNameTextField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                renameTitleActionPerformed.actionPerformed(null);
            }
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int index = indexAtLocation(e.getX(), e.getY());
                if (index < 0) {
                    return;
                }

                switch (e.getButton()) {
                    case MouseEvent.BUTTON1:
                        if (e.getClickCount() == 2) {
                            selectedIndex = index;
                            tabComponent = getTabComponentAt(selectedIndex);
                            String tabName = getTitleAt(selectedIndex);

                            if (!"...".equals(tabName)) {
                                setTabComponentAt(selectedIndex, tabNameTextField);
                                tabNameTextField.setVisible(true);
                                tabNameTextField.setText(tabName);
                                tabNameTextField.selectAll();
                                tabNameTextField.requestFocusInWindow();
                                tabNameTextField.setMinimumSize(tabNameTextField.getPreferredSize());
                            }
                        } else if (e.getClickCount() == 1) {
                            String title = getTitleAt(index);
                            if ("...".equals(title)) {
                                // 阻止默认的选中行为
                                e.consume();
                                // 直接创建新标签
                                insertNewTab();
                            } else {
                                renameTitleActionPerformed.actionPerformed(null);
                            }
                        }
                        break;
                    case MouseEvent.BUTTON3:
                        if (!"...".equals(getTitleAt(index))) {
                            popupMenu.show(e.getComponent(), e.getX(), e.getY());
                        }
                        break;
                    default:
                        break;
                }
            }
        });


        InputMap im = tabNameTextField.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = tabNameTextField.getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        am.put("cancel", cancelActionPerformed);
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "rename");
        am.put("rename", renameTitleActionPerformed);
    }

    private void insertNewTab() {
        insertTab(String.valueOf(newTabIndex), null, new Tab(api, configLoader, HttpRequest.httpRequestFromUrl("http://localhost:80"), "Param", "id"), null, getTabCount() - 1);
        setSelectedIndex(getTabCount() - 2);
        newTabIndex++;
    }

    public void insertNewTab(HttpRequest request, String payloadType, String payloads) {
        insertTab(String.valueOf(newTabIndex), null, new Tab(api, configLoader, request, payloadType, payloads), null, getTabCount() - 1);
        setSelectedIndex(getTabCount() - 2);
        newTabIndex++;
    }

    private void deleteTabActionPerformed(ActionEvent e) {
        if (getTabCount() > 2) {
            int retCode = JOptionPane.showConfirmDialog(this, "Do you want to delete this tab?", "Info",
                    JOptionPane.YES_NO_OPTION);
            if (retCode == JOptionPane.YES_OPTION) {
                remove(getSelectedIndex());
                setSelectedIndex(getSelectedIndex() - 1);
            }
        }
    }
}




