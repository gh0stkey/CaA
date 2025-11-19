package caa.instances.editor;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.core.Range;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.Selection;
import burp.api.montoya.ui.editor.extension.EditorCreationContext;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpResponseEditor;
import burp.api.montoya.ui.editor.extension.HttpResponseEditorProvider;
import caa.component.datatable.Datatable;
import caa.component.datatable.Mode;
import caa.component.generator.Generator;
import caa.instances.Collector;
import caa.instances.Database;
import caa.utils.ConfigLoader;
import caa.utils.HttpUtils;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class ResponseEditor implements HttpResponseEditorProvider {
    private final MontoyaApi api;
    private final Database db;
    private final ConfigLoader configLoader;
    private final Generator generator;

    public ResponseEditor(MontoyaApi api, Database db, ConfigLoader configLoader, Generator generator) {
        this.api = api;
        this.db = db;
        this.configLoader = configLoader;
        this.generator = generator;
    }

    @Override
    public ExtensionProvidedHttpResponseEditor provideHttpResponseEditor(EditorCreationContext creationContext) {
        return new Editor(api, creationContext, db, configLoader, generator);
    }

    private static class Editor implements ExtensionProvidedHttpResponseEditor {
        private final MontoyaApi api;
        private final Database db;
        private final ConfigLoader configLoader;
        private final Generator generator;

        private final HttpUtils httpUtils;
        private final JTabbedPane jTabbedPane;
        private final EditorCreationContext creationContext;
        private Datatable dataPanel;
        private JTable dataTable;
        private HttpRequestResponse requestResponse;
        private LinkedHashMap<String, Object> dataMap;

        public Editor(MontoyaApi api, EditorCreationContext creationContext, Database db, ConfigLoader configLoader, Generator generator) {
            this.api = api;
            this.db = db;
            this.configLoader = configLoader;
            this.generator = generator;

            this.httpUtils = new HttpUtils(api, configLoader);
            this.creationContext = creationContext;
            this.jTabbedPane = new JTabbedPane();
        }

        @Override
        public HttpResponse getResponse() {
            return requestResponse.response();
        }

        @Override
        public void setRequestResponse(HttpRequestResponse requestResponse) {
            this.requestResponse = requestResponse;
            if (dataMap != null && !dataMap.isEmpty()) {
                dataPanel = generateTabWithData(requestResponse.request());
            }
        }

        @Override
        public synchronized boolean isEnabledFor(HttpRequestResponse requestResponse) {
            HttpResponse response = requestResponse.response();

            if (response != null) {
                HttpRequest request = requestResponse.request();
                boolean matches = false;

                if (request != null) {
                    try {
                        String host = httpUtils.getHostByUrl(request.url());
                        if (!host.isEmpty()) {
                            String toolType = creationContext.toolSource().toolType().toolName();
                            matches = httpUtils.verifyHttpRequestResponse(requestResponse, toolType);
                        }
                    } catch (Exception e) {
                        api.logging().logToError("Failed to verify request in ResponseEditor: " + e.getMessage());
                    }
                }

                if (!matches) {
                    Collector collector = new Collector(api, db, configLoader);
                    dataMap = new LinkedHashMap<>(collector.collect(requestResponse));

                    return !dataMap.isEmpty();
                }
            }

            return false;
        }

        @Override
        public String caption() {
            return "CollectInfo";
        }

        @Override
        public Component uiComponent() {
            ChangeListener tabChangeListener = e -> {
                JTabbedPane sourceTabbedPane = (JTabbedPane) e.getSource();
                dataTable = (JTable) getComponent(sourceTabbedPane);
            };

            jTabbedPane.addChangeListener(tabChangeListener);

            return jTabbedPane;
        }

        @Override
        public Selection selectedData() {
            // 老版本API返回是Byte[]，现在变成了Selection，使用如下方式依旧可以复用之前的代码
            return new Selection() {
                @Override
                public ByteArray contents() {
                    return ByteArray.byteArray(dataPanel.getSelectedDataAtTable(dataTable));
                }

                @Override
                public Range offsets() {
                    return null;
                }
            };
        }

        @Override
        public boolean isModified() {
            return false;
        }

        private Datatable generateTabWithData(HttpRequest httpRequest) {
            jTabbedPane.removeAll();

            Datatable component = null;
            List<String> columnNameA = new ArrayList<>();
            columnNameA.add("Name");
            for (String i : dataMap.keySet()) {
                if (i.equals("Value")) {
                    columnNameA.add("Value");
                }
                component = new Datatable(api, db, configLoader, generator, columnNameA, dataMap.get(i), httpRequest, i, Mode.STANDARD);
                jTabbedPane.addTab(i, component);
            }

            return component;
        }

        private Component getComponent(JTabbedPane sourceTabbedPane) {
            Component selectedComponent = sourceTabbedPane.getSelectedComponent();
            if (selectedComponent instanceof JTabbedPane) {
                selectedComponent = ((JTabbedPane) selectedComponent).getSelectedComponent();
            }

            if (selectedComponent instanceof Datatable) {
                selectedComponent = ((Datatable) selectedComponent).getDataTable();
            }

            return selectedComponent;
        }
    }
}