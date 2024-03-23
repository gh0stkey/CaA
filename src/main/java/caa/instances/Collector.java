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
import caa.Config;
import caa.cache.CachePool;
import caa.utils.HashCalculator;
import caa.utils.JsonTraverser;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.gson.JsonParser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import static burp.api.montoya.scanner.AuditResult.auditResult;
import static burp.api.montoya.scanner.ConsolidationAction.KEEP_BOTH;
import static burp.api.montoya.scanner.ConsolidationAction.KEEP_EXISTING;
import static java.util.Collections.emptyList;

public class Collector implements ScanCheck {
    private final MontoyaApi api;
    private final Database db;
    private Map<String, Object> dataMap;

    // 初始化存储容器
    private Set<String> pathList;
    private Set<String> fileList;
    private Set<String> paramList;
    private SetMultimap<String, String> valueList;

    public Collector(MontoyaApi api, Database db)
    {
        this.api = api;
        this.db = db;
    }

    @Override
    public AuditResult activeAudit(HttpRequestResponse baseRequestResponse, AuditInsertionPoint auditInsertionPoint) {
        return auditResult(emptyList());
    }

    @Override
    public AuditResult passiveAudit(HttpRequestResponse baseRequestResponse) {
        dataMap = new HashMap<>();
        pathList = new HashSet<>();
        fileList = new HashSet<>();
        paramList = new HashSet<>();
        valueList = LinkedHashMultimap.create();

        // 基于被动扫描分析、收集数据
        HttpRequest request = baseRequestResponse.request();
        HttpResponse response = baseRequestResponse.response();
        if (request != null) {
            // 基于URL来获取URI，因为原生的request.path()会获取到参数
            String path = "";
            String host = "";
            try {
                URL u = new URL(request.url());
                path = u.getPath().replaceAll("/+", "/");
                host = u.getHost();
            } catch (Exception ignored) {
            }

            String extension = "";
            int lastIndexOfDot = path.lastIndexOf('.');
            if (lastIndexOfDot != -1) {
                extension = path.substring(lastIndexOfDot + 1);
            }


            // 排除静态后缀请求
            if (!Config.excludeSuffix.contains(extension.toLowerCase())) {
                // -----------------处理请求报文-----------------
                // 收集请求路径，每一层都获取
                if (!"/".equals(path)) {
                    Arrays.stream(path.split("/")).filter(p -> !p.isBlank()).forEach(p -> {
                        if (p.contains(".") && !p.equals(".")) {
                            fileList.add(p);
                        } else {
                            pathList.add(p.replaceAll(":", ""));
                        }
                    });
                }

                // 收集请求参数
                List<ParsedHttpParameter> paramsList = request.parameters();
                for (ParsedHttpParameter param : paramsList) {
                    // 处理URL编码、问号
                    String paramName = decodeParameter(param.name()).trim().replaceAll("\\?", "");

                    if ("_".equals(paramName)) {
                        paramName = paramName.replace("_", "");
                    }

                    if (!paramName.isBlank() && paramName.matches("[\\w\\-\\.]+")) {
                        paramList.add(paramName);
                        String paramValue = decodeParameter(param.value());

                        // 收集、处理参数值为JSON内容的数据
                        if (!paramValue.isBlank()) {
                            Map<String, Object> jsonData = getJsonData(paramValue);

                            if (jsonData != null) {
                                processJsonData(jsonData);
                            } else {
                                valueList.put(paramName, paramValue);
                            }
                        }
                    }
                }

                if (response != null) {
                    if (response.statusCode() != 404) {
                        ByteArray responseBodyBytes = response.body();
                        String hashIndex = HashCalculator.calculateHash(responseBodyBytes.getBytes());
                        Map<String, Object> cachePool = CachePool.getFromCache(hashIndex);

                        if (cachePool == null) {
                            // -----------------处理响应报文-----------------
                            try {
                                // 获取响应报文的主体内容
                                String responseBody = new String(responseBodyBytes.getBytes(), StandardCharsets.UTF_8);

                                Map<String, Object> jsonData = getJsonData(responseBody);

                                if (jsonData != null) {
                                    processJsonData(jsonData);
                                } else {
                                    // 尝试对HTML程序进行解析
                                    Document doc = Jsoup.parse(responseBody);
                                    Elements inputTags = doc.getElementsByTag("input");

                                    for (Element inputTag : inputTags) {
                                        String type = inputTag.attr("type");

                                        if ("hidden".equals(type)) {
                                            String name = inputTag.attr("name");
                                            if (name == null || name.isBlank()) {
                                                name = inputTag.attr("id");
                                            }

                                            if (name != null && !name.isBlank() && name.matches("[\\w\\-\\.]+")) {
                                                String value = inputTag.attr("value");
                                                paramList.add(name);
                                                if (value != null && !value.isBlank()) {
                                                    valueList.put(name, value);
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (Exception ignored) {
                            }

                            // 存储结果到内存中
                            Map<String, Object> collectMap = new HashMap<>();

                            if (pathList.size() > 0) {
                                collectMap.put("Path", pathList);
                                collectMap.put("All Path", pathList);
                            }
                            if (fileList.size() > 0) {
                                collectMap.put("File", fileList);
                                collectMap.put("All File", fileList);
                            }
                            if (paramList.size() > 0) {
                                collectMap.put("Param", paramList);
                                collectMap.put("All Param", paramList);
                            }
                            if (valueList.size() > 0) {
                                collectMap.put("Value", valueList);
                            }

                            if (collectMap.size() > 0) {
                                String finalHost = host;
                                CompletableFuture<Void> future = CompletableFuture.supplyAsync(() -> {
                                    db.insertData(finalHost, collectMap);
                                    return null;
                                });
                            }

                        } else {
                            valueList.putAll((SetMultimap) cachePool.get("jsonKeyValue"));
                            paramList.addAll((HashSet) cachePool.get("jsonKey"));
                        }
                    }
                }
            }

            putCurrentDataToMap();
            getDataToMap(host);
        }

        if (request == null && response != null) {
            ByteArray responseBodyBytes = response.body();
            String hashIndex = HashCalculator.calculateHash(responseBodyBytes.getBytes());
            Map<String, Object> cachePool = CachePool.getFromCache(hashIndex);

            if (cachePool != null) {
                valueList.putAll((SetMultimap) cachePool.get("jsonKeyValue"));
                paramList.addAll((HashSet) cachePool.get("jsonKey"));
                putCurrentDataToMap();
            }
        }

        return auditResult(emptyList());
    }

    private void putCurrentDataToMap() {
        if (paramList.size() > 0) {
            dataMap.put("Current Param", paramList);
        }
        if (valueList.size() > 0) {
            dataMap.put("Current Value", valueList);
        }
    }

    private void getDataToMap(String host) {
        Object pathData = db.selectData(host, "Path", "");
        if (pathData != null) {
            dataMap.put("Path", pathData);
        }
        Object fileData = db.selectData(host, "File", "");
        if (fileData != null) {
            dataMap.put("File", fileData);
        }
        Object paramData = db.selectData(host, "Param", "");
        if (paramData != null) {
            dataMap.put("Param", paramData);
        }
        Object valueData = db.selectData(host, "Value", "");
        if (valueData != null) {
            dataMap.put("Value", valueData);
        }
    }

    private void processJsonData(Map<String, Object> jsonData) {
        Object jsonKeyValue = jsonData.get("jsonKeyValue");
        Object jsonKey = jsonData.get("jsonKey");
        if (jsonKeyValue != null) {
            valueList.putAll((SetMultimap) jsonKeyValue);
        }

        if (jsonKey != null) {
            paramList.addAll((HashSet) jsonKey);
        }
    }

    private String decodeParameter(String input) {
        try {
            input = api.utilities().urlUtils().decode(input);
        } catch (Exception ignored) {
        }
        return input;
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

                Map<String, Object> collectMap = new HashMap<>();
                Set<String> paramList = new LinkedHashSet<>(jsonTraverser.getJsonKeys());
                SetMultimap<String, String> paramValueMap = jsonTraverser.getJsonKeyValues();

                if (paramValueMap != null && paramValueMap.size() > 0) {
                    collectMap.put("jsonKeyValue", paramValueMap);
                }

                if (paramList.size() > 0) {
                    collectMap.put("jsonKey", paramList);
                }

                if (collectMap.size() > 0) {
                    CachePool.addToCache(hashIndex, collectMap);
                    return collectMap;
                } else {
                    return null;
                }

            } catch (Exception e){
                return null;
            }
        }
    }

    public Map<String, Object> getDataMap() {
        return dataMap;
    }

    @Override
    public ConsolidationAction consolidateIssues(AuditIssue newIssue, AuditIssue existingIssue) {
        return existingIssue.name().equals(newIssue.name()) ? KEEP_EXISTING : KEEP_BOTH;
    }
}
