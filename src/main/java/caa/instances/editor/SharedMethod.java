package caa.instances.editor;

import caa.component.member.DatatablePanel;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class SharedMethod {

    public static LinkedHashMap<String, Object> getDataMap(Map<String, Object> collectMap) {
        if (collectMap != null) {
            LinkedHashMap<String, Object> dataMap = new LinkedHashMap<>();
            if (collectMap.get("Param") != null) {
                dataMap.put("All Param", collectMap.get("Param"));
            }
            if (collectMap.get("Path") != null) {
                dataMap.put("All Path", collectMap.get("Path"));
            }
            if (collectMap.get("FullPath") != null) {
                dataMap.put("All FullPath", collectMap.get("FullPath"));
            }
            if (collectMap.get("File") != null) {
                dataMap.put("All File", collectMap.get("File"));
            }
            if (collectMap.get("Value") != null) {
                dataMap.put("Value", collectMap.get("Value"));
            }
            if (collectMap.get("Current Param") != null) {
                dataMap.put("Current Param", collectMap.get("Current Param"));
            }
            if (collectMap.get("Current Value") != null) {
                dataMap.put("Current Value", collectMap.get("Current Value"));
            }
            if (dataMap.size() > 0) {
                return dataMap;
            }
        }
        return null;
    }

    public static Component getComponent(JTabbedPane sourceTabbedPane) {
        Component selectedComponent = sourceTabbedPane.getSelectedComponent();
        if (selectedComponent instanceof JTabbedPane) {
            selectedComponent = ((JTabbedPane) selectedComponent).getSelectedComponent();
        }

        if (selectedComponent instanceof DatatablePanel) {
            selectedComponent = ((DatatablePanel) selectedComponent).getDataTable();
        }

        return selectedComponent;
    }
}
