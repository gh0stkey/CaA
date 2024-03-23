package caa.instances.editor;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.core.Range;
import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.Selection;
import burp.api.montoya.ui.editor.extension.EditorCreationContext;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpRequestEditor;
import burp.api.montoya.ui.editor.extension.HttpRequestEditorProvider;
import caa.component.member.DatatablePanel;
import caa.component.member.taskboard.MessageTableModel;
import caa.instances.Collector;
import caa.instances.Database;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.*;

public class RequestEditor implements HttpRequestEditorProvider {
    private final MontoyaApi api;
    private final Database db;
    private final MessageTableModel messageTableModel;

    public RequestEditor(MontoyaApi api, Database db, MessageTableModel messageTableModel)
    {
        this.api = api;
        this.db = db;
        this.messageTableModel = messageTableModel;
    }

    @Override
    public ExtensionProvidedHttpRequestEditor provideHttpRequestEditor(EditorCreationContext creationContext) {
        return new Editor(api, creationContext, db, messageTableModel);
    }

    private static class Editor implements ExtensionProvidedHttpRequestEditor {
        private final MontoyaApi api;
        private final JTabbedPane jTabbedPane;
        private final JTabbedPane jTabbedPaneA;
        private final JTabbedPane jTabbedPaneB;
        private DatatablePanel dataPanel;
        private JTable dataTable;
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
        public HttpRequest getRequest() {
            return requestResponse.request();
        }

        @Override
        public void setRequestResponse(HttpRequestResponse requestResponse) {
            this.requestResponse = requestResponse;
        }

        @Override
        public synchronized boolean isEnabledFor(HttpRequestResponse requestResponse) {
            ToolType toolType = creationContext.toolSource().toolType();
            HttpRequest request = requestResponse.request();
            if ((toolType == ToolType.EXTENSIONS) && request != null) {
                try {
                    Collector collector = new Collector(api, db);
                    collector.passiveAudit(requestResponse);
                    Map<String, Object> collectMap = collector.getDataMap();
                    LinkedHashMap<String, Object> dataMap = SharedMethod.getDataMap(collectMap);

                    if (dataMap != null) {
                        SharedTabBuilder tabBuilder = new SharedTabBuilder(api, db, messageTableModel, jTabbedPane, jTabbedPaneA, jTabbedPaneB);
                        dataPanel = tabBuilder.generateTabWithData(dataMap, request);
                        return true;
                    }
                } catch (Exception ignored) {
                    api.logging().logToError(ignored);
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