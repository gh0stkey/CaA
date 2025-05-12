package caa.component;

import burp.api.montoya.MontoyaApi;
import caa.component.generator.Generator;
import caa.instances.Database;
import caa.utils.ConfigLoader;

import javax.swing.*;
import java.awt.*;
import java.net.URL;

public class Main extends JPanel {
    private final MontoyaApi api;
    private final Database db;
    private final ConfigLoader configLoader;
    private final Generator generator;

    public Main(MontoyaApi api, Database db, ConfigLoader configLoader, Generator generator) {
        this.api = api;
        this.db = db;
        this.configLoader = configLoader;
        this.generator = generator;

        initComponents();
    }

    private void initComponents() {
        setLayout(new GridBagLayout());
        ((GridBagLayout) getLayout()).columnWidths = new int[]{0, 0};
        ((GridBagLayout) getLayout()).rowHeights = new int[]{0, 0};
        ((GridBagLayout) getLayout()).columnWeights = new double[]{1.0, 1.0E-4};
        ((GridBagLayout) getLayout()).rowWeights = new double[]{1.0, 1.0E-4};

        JTabbedPane mainTabbedPane = new JTabbedPane();

        JTabbedPane CaATabbedPane = new JTabbedPane();
        boolean isDarkBg = isDarkBg(CaATabbedPane);
        CaATabbedPane.addTab("", getImageIcon(isDarkBg), mainTabbedPane);
        // 中文Slogan：信息洞察，智探千方
        CaATabbedPane.addTab(" Collector and Analyzer - Insight into information, exploring with intelligence in a thousand ways. ", null);
        CaATabbedPane.setEnabledAt(1, false);
        CaATabbedPane.addPropertyChangeListener("background", e -> {
            CaATabbedPane.setIconAt(0, getImageIcon(isDarkBg(CaATabbedPane)));
        });

        add(CaATabbedPane, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
                GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                new Insets(0, 0, 0, 0), 0, 0));

        mainTabbedPane.addTab("Generator", generator);
        mainTabbedPane.addTab("Databoard", new Databoard(api, db, configLoader, generator));
        mainTabbedPane.addTab("Config", new Config(api, configLoader));
    }

    private ImageIcon getImageIcon(boolean isDark) {
        ClassLoader classLoader = getClass().getClassLoader();
        URL imageURL;
        if (isDark) {
            imageURL = classLoader.getResource("logo/logo.png");
        } else {
            imageURL = classLoader.getResource("logo/logo_black.png");
        }
        ImageIcon originalIcon = new ImageIcon(imageURL);
        Image originalImage = originalIcon.getImage();
        Image scaledImage = originalImage.getScaledInstance(30, 20, Image.SCALE_AREA_AVERAGING);
        return new ImageIcon(scaledImage);
    }

    private boolean isDarkBg(JTabbedPane CaATabbedPane) {
        Color bg = CaATabbedPane.getBackground();
        int r = bg.getRed();
        int g = bg.getGreen();
        int b = bg.getBlue();
        int avg = (r + g + b) / 3;

        return avg < 128;
    }
}
