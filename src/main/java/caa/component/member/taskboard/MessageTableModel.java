package caa.component.member.taskboard;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.UserInterface;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import caa.utils.StringTools;
import info.debatty.java.stringsimilarity.Cosine;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.util.*;
import java.util.List;

import static burp.api.montoya.ui.editor.EditorOptions.READ_ONLY;

public class MessageTableModel extends AbstractTableModel {
    private LinkedList<LogEntry> filteredLog;
    private final MontoyaApi api;
    private final Map<String, LinkedList<LogEntry>> logMap;
    private final Map<String, Boolean> taskStatusMap;
    private final MessageTable messageTable;
    private final JTabbedPane messageTab;
    private final JSplitPane splitPane;

    public MessageTableModel(MontoyaApi api) {
        this.filteredLog = new LinkedList<>();
        this.api = api;
        this.logMap = new HashMap<>();
        this.taskStatusMap = new HashMap<>();

        messageTab = new JTabbedPane();
        UserInterface userInterface = api.userInterface();
        HttpRequestEditor requestViewer = userInterface.createHttpRequestEditor(READ_ONLY);
        HttpResponseEditor responseViewer = userInterface.createHttpResponseEditor(READ_ONLY);
        messageTab.addTab("Request", requestViewer.uiComponent());
        messageTab.addTab("Response", responseViewer.uiComponent());

        // 请求条目表格
        messageTable = new MessageTable(MessageTableModel.this, requestViewer, responseViewer);
        messageTable.setAutoCreateRowSorter(true);
        // Length字段根据大小进行排序
        TableRowSorter<DefaultTableModel> sorter = (TableRowSorter<DefaultTableModel>) messageTable.getRowSorter();

        sorter.setComparator(4, new Comparator<String>() {
            @Override
            public int compare(String s1, String s2) {
                Double age1 = Double.parseDouble(s1);
                Double age2 = Double.parseDouble(s2);
                return age1.compareTo(age2);
            }
        });

        sorter.setComparator(5, new Comparator<String>() {
            @Override
            public int compare(String s1, String s2) {
                Integer age1 = Integer.parseInt(s1);
                Integer age2 = Integer.parseInt(s2);
                return age1.compareTo(age2);
            }
        });

        sorter.setComparator(7, new Comparator<String>() {
            @Override
            public int compare(String s1, String s2) {
                Integer age1 = Integer.parseInt(s1);
                Integer age2 = Integer.parseInt(s2);
                return age1.compareTo(age2);
            }
        });
        messageTable.setRowSorter(sorter);
        messageTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        // 请求/相应文本框
        JScrollPane scrollPane = new JScrollPane(messageTable);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        splitPane.setLeftComponent(scrollPane);
        splitPane.setRightComponent(messageTab);
    }

    public synchronized void add(String taskName, String method, HttpRequestResponse requestResponse)
    {
        LinkedList<LogEntry> tmpLogList = logMap.computeIfAbsent(taskName, k -> new LinkedList<>());
        String oriResponse = "";

        if (!tmpLogList.isEmpty()) {
            oriResponse = tmpLogList.getFirst().getRequestResponse().response().body().toString();
        }

        tmpLogList.add(generateLogEntry(requestResponse, oriResponse, method));
        logMap.put(taskName, tmpLogList);
        setTaskStatus(taskName, false);
    }

    public void setTaskStatus(String taskName, boolean taskStatus) {
        taskStatusMap.remove(taskName);
        taskStatusMap.putIfAbsent(taskName, taskStatus);
    }

    public Boolean getTaskStatus(String taskName) {
        Boolean taskStatus = taskStatusMap.get(taskName);
        return Objects.requireNonNullElse(taskStatus, false);
    }

    public void removeTask(String taskName) {
        if (logMap.get(taskName) != null) {
            logMap.remove(taskName);
            filteredLog.clear();
            fireTableDataChanged();
        }
    }

    private LogEntry generateLogEntry(HttpRequestResponse httpRequestResponse, String oriResponse, String method) {
        HttpRequest httpRequest = httpRequestResponse.request();
        HttpResponse httpResponse = httpRequestResponse.response();
        String similarity = "1.0000";
        if (!oriResponse.isBlank()) {
            String currentResponse = httpResponse.body().toString();

            if (!currentResponse.isEmpty()) {
                Cosine cosine = new Cosine(2);
                Map<String, Integer> profile1 = cosine.getProfile(oriResponse);
                Map<String, Integer> profile2 = cosine.getProfile(currentResponse);

                similarity = String.format("%.4f", cosine.similarity(profile1, profile2));
            } else {
                similarity = "0.0000";
            }
        }
        String host = StringTools.getHost(httpRequest.url());
        String path = httpRequest.pathWithoutQuery();
        String query = httpRequest.query();
        String paramCount = String.valueOf(httpRequest.parameters().size());
        String length = String.valueOf(httpResponse.toByteArray().length());
        String status = String.valueOf(httpResponse.statusCode());
        return new LogEntry(httpRequestResponse, method, host, path, query, similarity, paramCount, status, length);
    }

    public synchronized List<String> getTaskNameList()
    {
        List<String> taskNameList = new ArrayList<>();
        logMap.forEach((key, value) -> {
            taskNameList.add(key);
        });
        return taskNameList;
    }

    public JSplitPane getSplitPane()
    {
        return splitPane;
    }

    public JTable getMessageTable()
    {
        return messageTable;
    }

    public void applyTaskNameFilter(String taskName) {
        filteredLog.clear();
        fireTableDataChanged();
        filteredLog.addAll(logMap.get(taskName));
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return filteredLog.size();
    }

    @Override
    public int getColumnCount() {
        return 8;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (filteredLog.isEmpty()) {
            return "";
        }

        LogEntry logEntry = filteredLog.get(rowIndex);
        switch (columnIndex)
        {
            case 0:
                return logEntry.getMethod();
            case 1:
                return logEntry.getHost();
            case 2:
                return logEntry.getPath();
            case 3:
                return logEntry.getQuery();
            case 4:
                return logEntry.getSimilarity();
            case 5:
                return logEntry.getParamCount();
            case 6:
                return logEntry.getStatus();
            case 7:
                return logEntry.getLength();
            default:
                return "";
        }
    }

    @Override
    public String getColumnName(int columnIndex)
    {
        switch (columnIndex)
        {
            case 0:
                return "Method";
            case 1:
                return "Host";
            case 2:
                return "Path";
            case 3:
                return "Query";
            case 4:
                return "Similarity";
            case 5:
                return "Param count";
            case 6:
                return "Status code";
            case 7:
                return "Length";
            default:
                return "";
        }
    }

    public class MessageTable extends JTable {
        private LogEntry logEntry;
        private SwingWorker<Object, Void> currentWorker;
        // 设置响应报文返回的最大长度为3MB
        private final int MAX_LENGTH = 3145728;
        private int lastSelectedIndex = -1;
        private final HttpRequestEditor requestEditor;
        private final HttpResponseEditor responseEditor;

        public MessageTable(TableModel messageTableModel, HttpRequestEditor requestEditor, HttpResponseEditor responseEditor) {
            super(messageTableModel);
            this.requestEditor = requestEditor;
            this.responseEditor = responseEditor;
        }

        @Override
        public void changeSelection(int row, int col, boolean toggle, boolean extend) {
            super.changeSelection(row, col, toggle, extend);
            int selectedIndex = convertRowIndexToModel(row);
            if (lastSelectedIndex != selectedIndex) {
                lastSelectedIndex = selectedIndex;
                logEntry = filteredLog.get(selectedIndex);

                requestEditor.setRequest(HttpRequest.httpRequest("Loading..."));
                responseEditor.setResponse(HttpResponse.httpResponse("Loading..."));

                if (currentWorker != null && !currentWorker.isDone()) {
                    currentWorker.cancel(true);
                }

                currentWorker = new SwingWorker<>() {
                    @Override
                    protected ByteArray[] doInBackground() {
                        ByteArray requestByte = logEntry.getRequestResponse().request().toByteArray();
                        ByteArray responseByte = logEntry.getRequestResponse().response().toByteArray();

                        if (responseByte.length() > MAX_LENGTH) {
                            String ellipsis = "\r\n......";
                            responseByte = responseByte.subArray(0, MAX_LENGTH).withAppended(ellipsis);
                        }

                        return new ByteArray[]{requestByte, responseByte};
                    }

                    @Override
                    protected void done() {
                        if (!isCancelled()) {
                            try {
                                ByteArray[] result = (ByteArray[]) get();
                                requestEditor.setRequest(HttpRequest.httpRequest(logEntry.getRequestResponse().httpService(), result[0]));
                                responseEditor.setResponse(HttpResponse.httpResponse(result[1]));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                };
                currentWorker.execute();
            }
        }
    }
}
