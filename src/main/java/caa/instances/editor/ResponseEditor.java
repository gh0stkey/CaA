package caa.instances.editor;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.core.Range;
import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.Selection;
import burp.api.montoya.ui.editor.extension.EditorCreationContext;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpResponseEditor;
import burp.api.montoya.ui.editor.extension.HttpResponseEditorProvider;
import caa.component.member.DatatablePanel;
import caa.component.member.taskboard.MessageTableModel;
import caa.instances.Collector;
import caa.instances.Database;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.*;

public class ResponseEditor implements HttpResponseEditorProvider {
    private final MontoyaApi api;
    private final Database db;
    private final MessageTableModel messageTableModel;

    public ResponseEditor(MontoyaApi api, Database db, MessageTableModel messageTableModel)
    {
        this.api = api;
        this.db = db;
        this.messageTableModel = messageTableModel;
    }

    @Override
    public ExtensionProvidedHttpResponseEditor provideHttpResponseEditor(EditorCreationContext creationContext) {
        return new Editor(api, creationContext, db, messageTableModel);
    }

    private static class Editor implements ExtensionProvidedHttpResponseEditor {
        private final MontoyaApi api;
        private final JTabbedPane jTabbedPane;
        private final JTabbedPane jTabbedPaneA;
        private DatatablePanel dataPanel;
        private JTable dataTable;
        private final JTabbedPane jTabbedPaneB;
        private final Database db;
        private final EditorCreationContext creationContext;
        private final MessageTableModel messageTableModel;
        private HttpRequestResponse requestResponse;

        public Editor(MontoyaApi api, EditorCreationContext creationContext, Database db, MessageTableModel messageTableModel)
        {
            this.api = api;
            this.db = db;
            this.creationContext = creationContext;
            this.jTabbedPane = new JTabbedPane();
            this.jTabbedPaneA = new JTabbedPane();
            this.jTabbedPaneB = new JTabbedPane();
            this.messageTableModel = messageTableModel;
        }

        @Override
        public HttpResponse getResponse() {
            return requestResponse.response();
        }

        @Override
        public void setRequestResponse(HttpRequestResponse requestResponse) {
            this.requestResponse = requestResponse;
        }

        @Override
        public synchronized boolean isEnabledFor(HttpRequestResponse requestResponse) {
            ToolType toolType = creationContext.toolSource().toolType();
            if (toolType == ToolType.PROXY || toolType == ToolType.REPEATER || toolType == ToolType.INTRUDER || toolType == ToolType.EXTENSIONS) {
                // 标签页也过一遍收集器
                Collector collector = new Collector(api, db);
                collector.passiveAudit(requestResponse);

                HttpRequest request = requestResponse.request();

                Map<String, Object> collectMap = collector.getDataMap();
                LinkedHashMap<String, Object> dataMap = SharedMethod.getDataMap(collectMap);

                if (dataMap != null) {
                    SharedTabBuilder tabBuilder = new SharedTabBuilder(api, db, messageTableModel, jTabbedPane, jTabbedPaneA, jTabbedPaneB);
                    dataPanel = tabBuilder.generateTabWithData(dataMap, request);
                    return true;
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
                    return ByteArray.byteArray(dataPanel.getSelectedData(dataTable));
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