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
import caa.component.member.DatatablePanel;
import caa.instances.Collector;
import caa.instances.Database;
import caa.utils.ConfigLoader;
import caa.utils.HttpUtils;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class ResponseEditor implements HttpResponseEditorProvider {
    private final MontoyaApi api;
    private final Database db;
    private final ConfigLoader configLoader;

    public ResponseEditor(MontoyaApi api, Database db, ConfigLoader configLoader) {
        this.api = api;
        this.db = db;
        this.configLoader = configLoader;
    }

    @Override
    public ExtensionProvidedHttpResponseEditor provideHttpResponseEditor(EditorCreationContext creationContext) {
        return new Editor(api, creationContext, db, configLoader);
    }

    private static class Editor implements ExtensionProvidedHttpResponseEditor {
        private final MontoyaApi api;
        private final ConfigLoader configLoader;
        private final HttpUtils httpUtils;
        private final JTabbedPane jTabbedPane;
        private final JTabbedPane jTabbedPaneA;
        private DatatablePanel dataPanel;
        private JTable dataTable;
        private final JTabbedPane jTabbedPaneB;
        private final Database db;
        private final EditorCreationContext creationContext;
        private HttpRequestResponse requestResponse;
        private LinkedHashMap<String, Object> dataMap;

        public Editor(MontoyaApi api, EditorCreationContext creationContext, Database db, ConfigLoader configLoader) {
            this.api = api;
            this.db = db;
            this.configLoader = configLoader;
            this.httpUtils = new HttpUtils(api, configLoader);
            this.creationContext = creationContext;
            this.jTabbedPane = new JTabbedPane();
            this.jTabbedPaneA = new JTabbedPane();
            this.jTabbedPaneB = new JTabbedPane();
        }

        @Override
        public HttpResponse getResponse() {
            return requestResponse.response();
        }

        @Override
        public void setRequestResponse(HttpRequestResponse requestResponse) {
            this.requestResponse = requestResponse;
            if (dataMap != null && !dataMap.isEmpty()) {
                SharedTabBuilder tabBuilder = new SharedTabBuilder(api, db, configLoader, jTabbedPane, jTabbedPaneA, jTabbedPaneB);
                dataPanel = tabBuilder.generateTabWithData(dataMap, requestResponse.request());
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
                    } catch (Exception ignored) {
                    }
                }

                if (!matches) {
                    Collector collector = new Collector(api, db, configLoader);
                    collector.passiveAudit(requestResponse);

                    Map<String, Object> collectMap = collector.getDataMap();
                    dataMap = SharedMethod.getDataMap(collectMap);

                    return dataMap != null && !dataMap.isEmpty();
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
                dataTable = (JTable) SharedMethod.getComponent(sourceTabbedPane);
            };

            jTabbedPaneA.addChangeListener(tabChangeListener);
            jTabbedPaneB.addChangeListener(tabChangeListener);
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
    }
}