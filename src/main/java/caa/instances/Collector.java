package caa.instances;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.scanner.AuditResult;
import burp.api.montoya.scanner.ConsolidationAction;
import burp.api.montoya.scanner.ScanCheck;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import caa.cache.CachePool;
import caa.utils.ConfigLoader;
import caa.utils.HashCalculator;
import caa.utils.HttpUtils;
import caa.utils.JsonTraverser;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.gson.JsonParser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static burp.api.montoya.scanner.AuditResult.auditResult;
import static burp.api.montoya.scanner.ConsolidationAction.KEEP_BOTH;
import static burp.api.montoya.scanner.ConsolidationAction.KEEP_EXISTING;
import static java.util.Collections.emptyList;

public class Collector implements ScanCheck {
    private final MontoyaApi api;
    private final Database db;
    private final ConfigLoader configLoader;
    private final HttpUtils httpUtils;

    public Collector(MontoyaApi api, Database db, ConfigLoader configLoader) {
        this.api = api;
        this.db = db;
        this.configLoader = configLoader;
        this.httpUtils = new HttpUtils(api, configLoader);
    }

    public static Map<String, Object> getJsonData(String responseBody) {
        String hashIndex = HashCalculator.calculateHash(responseBody.getBytes());
        Map<String, Object> cachePool = CachePool.getFromCache(hashIndex);

        if (cachePool != null) {
            return cachePool;
        } else {
            // 遍历JSON Keys
            try {
                JsonTraverser jsonTraverser = new JsonTraverser();
                jsonTraverser.foreachJsonKey(JsonParser.parseString(responseBody).getAsJsonObject());

                Map<String, Object> collectMap = getJsonObjectMap(jsonTraverser);

                if (!collectMap.isEmpty()) {
                    CachePool.addToCache(hashIndex, collectMap);
                    return collectMap;
                } else {
                    return null;
                }

            } catch (Exception e) {
                return null;
            }
        }
    }

    private static Map<String, Object> getJsonObjectMap(JsonTraverser jsonTraverser) {
        Map<String, Object> collectMap = new HashMap<>();
        Set<String> paramList = new LinkedHashSet<>(jsonTraverser.getJsonKeys());
        SetMultimap<String, String> paramValueMap = jsonTraverser.getJsonKeyValues();

        if (paramValueMap != null && !paramValueMap.isEmpty()) {
            collectMap.put("jsonKeyValue", paramValueMap);
        }

        if (!paramList.isEmpty()) {
            collectMap.put("jsonKey", paramList);
        }

        return collectMap;
    }

    @Override
    public AuditResult activeAudit(HttpRequestResponse baseRequestResponse, AuditInsertionPoint auditInsertionPoint) {
        return auditResult(emptyList());
    }

    @Override
    public AuditResult passiveAudit(HttpRequestResponse baseRequestResponse) {
        // 使用局部变量确保线程安全
        Set<String> pathList = new HashSet<>();
        Set<String> fullPathList = new HashSet<>();
        Set<String> fileList = new HashSet<>();
        Set<String> paramList = new HashSet<>();
        SetMultimap<String, String> valueList = LinkedHashMultimap.create();

        HttpRequest request = baseRequestResponse.request();
        HttpResponse response = baseRequestResponse.response();
        
        if (request != null) {
            String path = "";
            String host = "";
            try {
                URL u = new URL(request.url());
                path = u.getPath().replaceAll("/+", "/");
                host = u.getHost();
            } catch (Exception e) {
                api.logging().logToError("Failed to parse URL: " + e.getMessage());
            }

            boolean matches = httpUtils.verifyHttpRequestResponse(baseRequestResponse, "Proxy");
            if (!matches) {
                // 处理请求路径
                processPath(path, pathList, fileList, fullPathList);

                // 处理请求参数
                processParameters(request.parameters(), paramList, valueList);

                // 处理响应
                if (response != null) {
                    processResponseBody(response.body(), paramList, valueList);

                    // 存储结果到数据库
                    Map<String, Object> collectMap = new HashMap<>();
                    if (!pathList.isEmpty()) {
                        collectMap.put("Path", pathList);
                        collectMap.put("All Path", pathList);
                    }
                    if (!fullPathList.isEmpty()) {
                        collectMap.put("FullPath", fullPathList);
                        collectMap.put("All FullPath", fullPathList);
                    }
                    if (!fileList.isEmpty()) {
                        collectMap.put("File", fileList);
                        collectMap.put("All File", fileList);
                    }
                    if (!paramList.isEmpty()) {
                        collectMap.put("Param", paramList);
                        collectMap.put("All Param", paramList);
                    }
                    if (!valueList.isEmpty()) {
                        collectMap.put("Value", valueList);
                    }

                    if (!collectMap.isEmpty()) {
                        String finalHost = host.toLowerCase();
                        CompletableFuture.supplyAsync(() -> {
                            db.insertData(finalHost, collectMap);
                            return null;
                        }).exceptionally(ex -> {
                            api.logging().logToError("Failed to insert data asynchronously: " + ex.getMessage());
                            return null;
                        });
                    }
                }
            }
        }

        // 处理只有响应的情况
        if (request == null && response != null) {
            processResponseOnly(response, valueList, paramList);
        }

        return auditResult(emptyList());
    }

    private void processJsonData(Map<String, Object> jsonData, SetMultimap<String, String> valueList, Set<String> paramList) {
        Object jsonKeyValue = jsonData.get("jsonKeyValue");
        Object jsonKey = jsonData.get("jsonKey");
        if (jsonKeyValue != null) {
            valueList.putAll((SetMultimap) jsonKeyValue);
        }
        if (jsonKey != null) {
            paramList.addAll((HashSet) jsonKey);
        }
    }

    /**
     * 处理路径，分离文件和路径
     */
    private void processPath(String path, Set<String> pathList, Set<String> fileList, Set<String> fullPathList) {
        if ("/".equals(path)) {
            return;
        }
        Arrays.stream(path.split("/")).filter(p -> !p.isBlank()).forEach(p -> {
            if (p.contains(".") && !p.equals(".") && p.indexOf(".") != p.length() - 1) {
                if (fileList != null) {
                    fileList.add(p);
                }
            } else {
                pathList.add(p.replaceAll(":", ""));
            }
        });
        if (fullPathList != null) {
            fullPathList.add(path);
        }
    }

    /**
     * 处理请求参数
     */
    private void processParameters(List<ParsedHttpParameter> paramsList, Set<String> paramList, SetMultimap<String, String> valueList) {
        for (ParsedHttpParameter param : paramsList) {
            String paramName = httpUtils.decodeParameter(param.name()).trim().replaceAll("\\?", "");
            if ("_".equals(paramName)) {
                paramName = paramName.replace("_", "");
            }
            if (!paramName.isBlank() && paramName.matches("[\\w\\-\\.]+")) {
                paramList.add(paramName);
                String paramValue = httpUtils.decodeParameter(param.value());
                if (!paramValue.isBlank()) {
                    Map<String, Object> jsonData = getJsonData(paramValue);
                    if (jsonData != null) {
                        processJsonData(jsonData, valueList, paramList);
                    } else {
                        valueList.put(paramName, paramValue);
                    }
                }
            }
        }
    }

    /**
     * 处理响应体（JSON和HTML）
     */
    private void processResponseBody(ByteArray responseBodyBytes, Set<String> paramList, SetMultimap<String, String> valueList) {
        String hashIndex = HashCalculator.calculateHash(responseBodyBytes.getBytes());
        Map<String, Object> cachePool = CachePool.getFromCache(hashIndex);

        if (cachePool == null) {
            try {
                String responseBody = new String(responseBodyBytes.getBytes(), StandardCharsets.UTF_8);
                Map<String, Object> jsonData = getJsonData(responseBody);

                if (jsonData != null) {
                    processJsonData(jsonData, valueList, paramList);
                    CachePool.addToCache(hashIndex, jsonData);
                } else {
                    // 尝试解析HTML
                    processHtmlInputs(responseBody, paramList, valueList);
                }
            } catch (Exception e) {
                api.logging().logToError("Failed to parse response body: " + e.getMessage());
            }
        } else {
            applyCachedData(cachePool, valueList, paramList);
        }
    }

    /**
     * 解析HTML中的input标签
     */
    private void processHtmlInputs(String html, Set<String> paramList, SetMultimap<String, String> valueList) {
        try {
            Document doc = Jsoup.parse(html);
            Elements inputTags = doc.getElementsByTag("input");

            for (Element inputTag : inputTags) {
                String type = inputTag.attr("type");
                if ("hidden".equals(type) || "text".equals(type)) {
                    String name = inputTag.attr("name");
                    if (name.isBlank()) {
                        name = inputTag.attr("id");
                    }
                    if (!name.isBlank() && name.matches("[\\w\\-\\.]+")) {
                        paramList.add(name);
                        String value = inputTag.attr("value");
                        if (!value.isBlank()) {
                            valueList.put(name, value);
                        }
                    }
                }
            }
        } catch (Exception e) {
            api.logging().logToError("Failed to parse HTML: " + e.getMessage());
        }
    }

    /**
     * 应用缓存数据
     */
    private void applyCachedData(Map<String, Object> cachePool, SetMultimap<String, String> valueList, Set<String> paramList) {
        Object cachedValueList = cachePool.get("jsonKeyValue");
        Object cachedParamList = cachePool.get("jsonKey");
        if (cachedValueList != null) {
            valueList.putAll((SetMultimap) cachedValueList);
        }
        if (cachedParamList != null) {
            paramList.addAll((HashSet) cachedParamList);
        }
    }

    /**
     * 处理只有响应没有请求的情况
     */
    private void processResponseOnly(HttpResponse response, SetMultimap<String, String> valueList, Set<String> paramList) {
        if (response != null) {
            String hashIndex = HashCalculator.calculateHash(response.body().getBytes());
            Map<String, Object> cachePool = CachePool.getFromCache(hashIndex);
            if (cachePool != null) {
                applyCachedData(cachePool, valueList, paramList);
            }
        }
    }

    /**
     * 收集HTTP请求响应中的数据，不存储到数据库，直接返回结果
     * 用于ResponseEditor等组件
     */
    public Map<String, Object> collect(HttpRequestResponse baseRequestResponse) {
        Map<String, Object> resultMap = new HashMap<>();
        Set<String> pathList = new HashSet<>();
        Set<String> paramList = new HashSet<>();
        SetMultimap<String, String> valueList = LinkedHashMultimap.create();

        HttpRequest request = baseRequestResponse.request();
        HttpResponse response = baseRequestResponse.response();

        if (request != null) {
            String path = "";
            try {
                URL u = new URL(request.url());
                path = u.getPath().replaceAll("/+", "/");
            } catch (Exception e) {
                api.logging().logToError("Failed to parse URL in collect: " + e.getMessage());
            }

            boolean matches = httpUtils.verifyHttpRequestResponse(baseRequestResponse, "Proxy");
            if (!matches) {
                // 处理路径（不收集文件和全路径）
                processPath(path, pathList, null, null);

                // 处理请求参数
                processParameters(request.parameters(), paramList, valueList);

                // 处理响应数据（只解析JSON，不解析HTML）
                if (response != null) {
                    String hashIndex = HashCalculator.calculateHash(response.body().getBytes());
                    Map<String, Object> cachePool = CachePool.getFromCache(hashIndex);

                    if (cachePool == null) {
                        try {
                            String responseBody = new String(response.body().getBytes(), StandardCharsets.UTF_8);
                            Map<String, Object> jsonData = getJsonData(responseBody);
                            if (jsonData != null) {
                                processJsonData(jsonData, valueList, paramList);
                                CachePool.addToCache(hashIndex, jsonData);
                            }
                        } catch (Exception e) {
                            api.logging().logToError("Failed to parse response in collect: " + e.getMessage());
                        }
                    } else {
                        applyCachedData(cachePool, valueList, paramList);
                    }
                }
            }
        }

        // 处理只有响应的情况
        if (request == null && response != null) {
            processResponseOnly(response, valueList, paramList);
        }

        // 返回结果
        if (!paramList.isEmpty()) {
            resultMap.put("Param", paramList);
        }
        if (!valueList.isEmpty()) {
            resultMap.put("Value", valueList);
        }
        if (!pathList.isEmpty()) {
            resultMap.put("Path", pathList);
        }

        return resultMap;
    }

    @Override
    public ConsolidationAction consolidateIssues(AuditIssue newIssue, AuditIssue existingIssue) {
        return existingIssue.name().equals(newIssue.name()) ? KEEP_EXISTING : KEEP_BOTH;
    }
}
