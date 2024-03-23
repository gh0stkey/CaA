package caa.component;

import javax.swing.*;
import burp.api.montoya.MontoyaApi;
import caa.component.member.taskboard.MessageTableModel;
import caa.component.utils.UITools;
import caa.instances.Database;

import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class CaAMain extends JPanel {
    private final MontoyaApi api;
    private final Database db;
    private final MessageTableModel messageTableModel;

    public CaAMain(MontoyaApi api, Database db, MessageTableModel messageTableModel) {
        this.api = api;
        this.db = db;
        this.messageTableModel = messageTableModel;
        initComponents();
    }

    private void initComponents() {
        setLayout(new GridBagLayout());
        ((GridBagLayout)getLayout()).columnWidths = new int[] {0, 0};
        ((GridBagLayout)getLayout()).rowHeights = new int[] {0, 0};
        ((GridBagLayout)getLayout()).columnWeights = new double[] {1.0, 1.0E-4};
        ((GridBagLayout)getLayout()).rowWeights = new double[] {1.0, 1.0E-4};

        JTabbedPane mainTabbedPane = new JTabbedPane();

        // 新增Logo
        JTabbedPane CaATabbedPane = new JTabbedPane();
        CaATabbedPane.addTab("", UITools.getImageIcon(false, "logo", 30, 20, Image.SCALE_AREA_AVERAGING), mainTabbedPane);
        // 中文Slogan：信息洞察，智探千方
        CaATabbedPane.addTab(" Collector and Analyzer - Insight into information, exploring with intelligence in a thousand ways. ", null);
        CaATabbedPane.setEnabledAt(1, false);
        CaATabbedPane.addPropertyChangeListener("background", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent e) {
                boolean isDarkBg = isDarkBg();
                CaATabbedPane.setIconAt(0, UITools.getImageIcon(isDarkBg, "logo", 30, 20, Image.SCALE_AREA_AVERAGING));
            }

            private boolean isDarkBg() {
                Color bg = CaATabbedPane.getBackground();
                int r = bg.getRed();
                int g = bg.getGreen();
                int b = bg.getBlue();
                int avg = (r + g + b) / 3;

                return avg < 128;
            }
        });

        add(CaATabbedPane, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
                GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                new Insets(0, 0, 0, 0), 0, 0));

        mainTabbedPane.addTab("Databoard", new Databoard(api, db, messageTableModel));
        mainTabbedPane.addTab("Taskboard", new Taskboard(api, messageTableModel));
    }
}
