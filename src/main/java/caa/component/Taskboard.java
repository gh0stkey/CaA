package caa.component;

import burp.api.montoya.MontoyaApi;
import caa.component.member.taskboard.MessageTableModel;
import caa.component.utils.UITools;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableColumnModel;

import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;

public class Taskboard extends JPanel {
    private final MontoyaApi api;
    private final MessageTableModel messageTableModel;
    private JTextField taskNameTextField;
    private JComboBox taskComboBox;
    private DefaultComboBoxModel comboBoxModel;
    private static boolean isMatched = false;
    private JLabel taskStatusLabel;
    private final String taskLogoPath = "task/%s";
    private final String inProgressString = "InProgress";
    private final String completedString = "Completed";
    private String currentIconPath = "";

    public Taskboard(MontoyaApi api, MessageTableModel messageTableModel) {
        this.api = api;
        this.messageTableModel = messageTableModel;
        initComponents();
    }

    private void initComponents() {
        taskNameTextField = new JTextField();
        comboBoxModel = new DefaultComboBoxModel<>();
        taskComboBox = new JComboBox(comboBoxModel);

        setLayout(new GridBagLayout());
        ((GridBagLayout)getLayout()).columnWidths = new int[] {25, 0, 0, 0, 0, 20, 0};
        ((GridBagLayout)getLayout()).rowHeights = new int[] {0, 65, 20, 0};
        ((GridBagLayout)getLayout()).columnWeights = new double[] {0.0, 0.0, 1.0, 0.0, 0.0, 1.0E-4};
        ((GridBagLayout)getLayout()).rowWeights = new double[] {0.0, 1.0, 0.0, 1.0E-4};

        JLabel taskNameLabel = new JLabel("Task name:");
        JButton removeButton = new JButton("Remove");
        taskStatusLabel = new JLabel("");

        add(taskNameLabel, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(8, 0, 5, 5), 0, 0));
        add(taskNameTextField, new GridBagConstraints(2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(8, 0, 5, 5), 0, 0));
        add(taskStatusLabel,  new GridBagConstraints(3, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(8, 0, 5, 5), 0, 0));
        add(removeButton,  new GridBagConstraints(4, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(8, 0, 5, 5), 0, 0));

        taskComboBox.setMaximumRowCount(5);
        add(taskComboBox, new GridBagConstraints(2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(8, 0, 5, 5), 0, 0));

        JSplitPane splitPane = messageTableModel.getSplitPane();
        add(splitPane, new GridBagConstraints(1, 1, 4, 4, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(8, 0, 5, 5), 0, 0));

        addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent e) {
                TableColumnModel columnModel = messageTableModel.getMessageTable().getColumnModel();
                int totalWidth = getWidth();
                columnModel.getColumn(0).setPreferredWidth((int) (totalWidth * 0.1));
                columnModel.getColumn(1).setPreferredWidth((int) (totalWidth * 0.15));
                columnModel.getColumn(2).setPreferredWidth((int) (totalWidth * 0.15));
                columnModel.getColumn(3).setPreferredWidth((int) (totalWidth * 0.2));
                columnModel.getColumn(4).setPreferredWidth((int) (totalWidth * 0.1));
                columnModel.getColumn(5).setPreferredWidth((int) (totalWidth * 0.1));
                columnModel.getColumn(6).setPreferredWidth((int) (totalWidth * 0.1));
                columnModel.getColumn(7).setPreferredWidth((int) (totalWidth * 0.05));
            }
        });

        removeButton.addActionListener(e->{
            messageTableModel.removeTask(taskNameTextField.getText());
            taskStatusLabel.setIcon(null);
        });

        addPropertyChangeListener("background", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent e) {
                boolean isDarkBg = isDarkBg();
                taskStatusLabel.setIcon(UITools.getImageIcon(isDarkBg, currentIconPath, 20, 20, Image.SCALE_SMOOTH));
            }

            private boolean isDarkBg() {
                Color bg = getBackground();
                int r = bg.getRed();
                int g = bg.getGreen();
                int b = bg.getBlue();
                int avg = (r + g + b) / 3;

                return avg < 128;
            }
        });

        setAutoMatch();
    }

    /**
     * 设置输入自动匹配
     */
    private void setAutoMatch() {

        taskComboBox.setSelectedItem(null);
        taskComboBox.addActionListener(this::handleComboBoxAction);

        taskNameTextField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleKeyEvents(e);
            }
        });

        taskNameTextField.getDocument().addDocumentListener(new DocumentListener() {
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

    private void handleComboBoxAction(ActionEvent e) {
        if (!isMatched && taskComboBox.getSelectedItem() != null) {
            String selectedTaskName = taskComboBox.getSelectedItem().toString();
            taskNameTextField.setText(selectedTaskName);
            boolean taskStatus = messageTableModel.getTaskStatus(selectedTaskName);
            currentIconPath = String.format(taskLogoPath, taskStatus ? completedString : inProgressString);
            taskStatusLabel.setIcon(UITools.getImageIcon(false, currentIconPath, 20, 20, Image.SCALE_SMOOTH));
            messageTableModel.applyTaskNameFilter(selectedTaskName);
        }
    }

    private void handleKeyEvents(KeyEvent e) {
        isMatched = true;
        int keyCode = e.getKeyCode();

        if (keyCode == KeyEvent.VK_SPACE && taskComboBox.isPopupVisible()) {
            e.setKeyCode(KeyEvent.VK_ENTER);
        }

        if (Arrays.asList(KeyEvent.VK_DOWN, KeyEvent.VK_UP).contains(keyCode)) {
            taskComboBox.dispatchEvent(e);
        }

        if (keyCode == KeyEvent.VK_ENTER) {
            isMatched = false;
            handleComboBoxAction(null);
            taskComboBox.setPopupVisible(false);
        }

        if (keyCode == KeyEvent.VK_ESCAPE) {
            taskComboBox.setPopupVisible(false);
        }

        isMatched = false;
    }

    private void filterComboBoxList() {
        isMatched = true;
        comboBoxModel.removeAllElements();
        String input = taskNameTextField.getText().toLowerCase();

        if (!input.isEmpty()) {
            for (String taskName : messageTableModel.getTaskNameList()) {
                String lowerCaseTaskName = taskName.toLowerCase();
                if (lowerCaseTaskName.contains(input)) {
                    if (lowerCaseTaskName.equals(input)) {
                        comboBoxModel.insertElementAt(taskName, 0);
                        comboBoxModel.setSelectedItem(taskName);
                    } else {
                        comboBoxModel.addElement(taskName);
                    }
                }
            }
        }

        taskComboBox.setPopupVisible(comboBoxModel.getSize() > 0);
        isMatched = false;
    }
}

