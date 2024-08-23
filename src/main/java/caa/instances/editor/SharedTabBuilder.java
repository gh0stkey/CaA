package caa.instances.editor;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import caa.component.member.DatatablePanel;
import caa.instances.Database;

import javax.swing.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class SharedTabBuilder {
    private final MontoyaApi api;
    private final Database db;
    private final JTabbedPane jTabbedPane;
    private final JTabbedPane jTabbedPaneA;
    private final JTabbedPane jTabbedPaneB;

    public SharedTabBuilder(MontoyaApi api, Database db, JTabbedPane jTabbedPane, JTabbedPane jTabbedPaneA, JTabbedPane jTabbedPaneB) {
        this.api = api;
        this.db = db;
        this.jTabbedPane = jTabbedPane;
        this.jTabbedPaneA = jTabbedPaneA;
        this.jTabbedPaneB = jTabbedPaneB;
    }

    public DatatablePanel generateTabWithData(LinkedHashMap<String, Object> dataMap, HttpRequest httpRequest) {
        jTabbedPane.removeAll();
        jTabbedPaneA.removeAll();
        jTabbedPaneB.removeAll();

        DatatablePanel component = null;
        List<String> columnNameA = new ArrayList<>();
        columnNameA.add("Name");
        List<String> columnNameB = new ArrayList<>();
        columnNameB.add("Name");
        columnNameB.add("Value");
        for (String i : dataMap.keySet()) {
            if (i.equals("Value")) {
                component = new DatatablePanel(api, db, columnNameB, dataMap.get(i), httpRequest, i);
                jTabbedPaneB.addTab(i, component);
            } else if (i.equals("Current Value")) {
                String name = i.replace("Current ", "");
                component = new DatatablePanel(api, db, columnNameB, dataMap.get(i), httpRequest, name);
                jTabbedPaneA.addTab(name, component);
            } else if (i.contains("Current ")) {
                String name = i.replace("Current ", "");
                component = new DatatablePanel(api, db, columnNameA, dataMap.get(i), httpRequest, name);
                jTabbedPaneA.addTab(name, component);
            } else if (i.contains("All")) {
                component = new DatatablePanel(api, db, columnNameA, dataMap.get(i), httpRequest, i);
                jTabbedPaneB.addTab(i.replace("All ", ""), component);
            }
        }

        if (jTabbedPaneA.getTabCount() > 0) {
            jTabbedPane.add("Current", jTabbedPaneA);
        }

        if (jTabbedPaneB.getTabCount() > 0) {
            jTabbedPane.addTab("All", jTabbedPaneB);
        }

        return component;
    }
}
