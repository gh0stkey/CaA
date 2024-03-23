package caa.component.member.taskboard;

import burp.api.montoya.http.message.HttpRequestResponse;

public class LogEntry {
    private final HttpRequestResponse requestResponse;
    private final String host;
    private final String length;
    private final String status;
    private final String method;
    private final String path;
    private final String query;
    private final String paramCount;
    private final String similarity;

    LogEntry(HttpRequestResponse requestResponse, String method, String host, String path, String query, String similarity, String paramCount, String status, String length) {
        this.requestResponse = requestResponse;
        this.method = method;
        this.host = host;
        this.path = path;
        this.query = query;
        this.paramCount = paramCount;
        this.length = length;
        this.status = status;
        this.similarity = similarity;
    }

    public String getSimilarity() {
        return this.similarity;
    }

    public String getHost() {
        return this.host;
    }

    public String getPath() {
        return this.path;
    }

    public String getQuery() {
        return this.query;
    }

    public String getParamCount() {
        return this.paramCount;
    }

    public String getLength() {
        return this.length;
    }

    public String getMethod() {
        return this.method;
    }

    public String getStatus() {
        return this.status;
    }

    public HttpRequestResponse getRequestResponse() {
        return this.requestResponse;
    }
}
