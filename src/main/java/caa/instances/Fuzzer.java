package caa.instances;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.params.HttpParameter;
import burp.api.montoya.http.message.params.HttpParameterType;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.requests.HttpTransformation;
import burp.api.montoya.utilities.RandomUtils;
import caa.component.member.taskboard.MessageTableModel;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.concurrent.*;

import java.util.*;

public class Fuzzer {
    private final MontoyaApi api;
    private final String taskName;
    private final String fuzzType;
    private final Object payloadObj;
    private final MessageTableModel messageTableModel;
    private final Database db;
    private HttpRequest requestByGetMethod;
    private HttpRequest requestByPostMethod;
    private HttpRequest requestByPostMethodWithJson;
    private  JsonObject jsonObject;
    private List<HttpParameter> allParamByUrl;
    private List<HttpParameter> allParamByBody;
    private List<HttpParameter> allParamByCookie;
    private List<HttpParameter> urlParameterList;
    private List<HttpParameter> bodyParameterList;
    private List<HttpRequest> requestListByGetMethod;
    private List<HttpRequest> requestListByPostMethod;
    private List<HttpRequest> requestListByPostMethodWithMultipart;
    private List<HttpRequest> requestListByPostMethodWithJson;

    public Fuzzer(MontoyaApi api, Database db, MessageTableModel messageTableModel, String taskName, String fuzzType, Object payloadObj) {
        this.api = api;
        this.db = db;
        this.taskName = taskName;
        this.fuzzType = fuzzType;
        this.payloadObj = payloadObj;
        this.messageTableModel = messageTableModel;
    }

    public void fuzzRequest(HttpRequest httpRequest) {
        // 添加第一条
        messageTableModel.add(taskName, String.format("%s (Based on)", httpRequest.method()), api.http().sendRequest(httpRequest));

        requestListByGetMethod = new ArrayList<>();
        requestListByPostMethod = new ArrayList<>();
        requestListByPostMethodWithMultipart = new ArrayList<>();
        requestListByPostMethodWithJson = new ArrayList<>();

        switch (fuzzType) {
            case "Param", "All Param", "Current", "Value" -> fuzzParamAndValue(httpRequest, payloadObj);
            case "Path", "All Path", "File", "All File" -> fuzzEndpoint(httpRequest, (List<String>) payloadObj);
        }

        if (allParamByBody.size() > 1) {
            requestListByPostMethod.add(requestByPostMethod.withAddedParameters(allParamByBody).withAddedParameters(allParamByCookie));
            requestListByPostMethodWithMultipart.add(generateRequestByMultipartMethod(requestByPostMethod.withAddedParameters(allParamByBody)));
        }

        if (allParamByUrl.size() > 1) {
            requestListByGetMethod.add(requestByGetMethod.withAddedParameters(allParamByUrl).withAddedParameters(allParamByCookie));
        }

        if (requestListByGetMethod.size() > 0) {
            sendRequests("GET", requestListByGetMethod);
        }

        if (requestListByPostMethod.size() > 0) {
            sendRequests("POST", requestListByPostMethod);
        }

        if (requestListByPostMethodWithJson.size() > 0) {
            sendRequests("POST with JSON", requestListByPostMethodWithJson);
        }

        if (requestListByPostMethodWithMultipart.size() > 0) {
            sendRequests("POST with Multipart", requestListByPostMethodWithMultipart);
        }

        // 循环结束，任务完成
        messageTableModel.setTaskStatus(taskName, true);
    }

    private void sendRequests(String method, List<HttpRequest> requestList) {
        // 去除重复的请求
        Set<String> uniqueSet = new HashSet<>();
        List<HttpRequest> uniqueList = requestList.stream()
                .filter(e -> uniqueSet.add(e.toString()))
                .toList();

        // 初始化线程池
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (HttpRequest request : uniqueList) {
            // 提交任务并立即处理完成后的回调
            CompletableFuture<Void> future = CompletableFuture.supplyAsync(() -> api.http().sendRequest(request), executor).thenAcceptAsync(requestResponse -> {
                // 对Fuzz的结果也过一层被动式收集
                Collector collector = new Collector(api, db);
                collector.passiveAudit(requestResponse);
                messageTableModel.add(taskName, method, requestResponse);
            });

            futures.add(future);
        }

        // 等待所有的 CompletableFuture 都运行结束。
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 关闭线程池
        executor.shutdown();
    }

    private void handleRequestParameters(List<ParsedHttpParameter> parameterList) {
        jsonObject = new JsonObject();
        allParamByUrl = new ArrayList<>();
        allParamByBody = new ArrayList<>();
        allParamByCookie = new ArrayList<>();

        urlParameterList = new ArrayList<>();
        bodyParameterList = new ArrayList<>();
        if (parameterList.size() > 0) {
            for (ParsedHttpParameter parsedHttpParameter : parameterList) {
                String parameterName = parsedHttpParameter.name();
                String parameterValue = parsedHttpParameter.value();
                jsonObject.addProperty(parameterName, parameterValue);

                HttpParameter urlParameter = HttpParameter.urlParameter(parameterName, parameterValue);
                HttpParameter bodyParameter = HttpParameter.bodyParameter(parameterName, parameterValue);
                HttpParameter cookieParameter = HttpParameter.cookieParameter(parameterName, parameterValue);

                switch (parsedHttpParameter.type()) {
                    case URL -> {
                        urlParameterList.add(urlParameter);
                        allParamByBody.add(bodyParameter);
                        allParamByCookie.add(cookieParameter);
                    }
                    case BODY -> {
                        bodyParameterList.add(bodyParameter);
                        allParamByCookie.add(cookieParameter);
                    }
                    case JSON -> {
                        allParamByBody.add(bodyParameter);
                        allParamByUrl.add(urlParameter);
                        allParamByCookie.add(cookieParameter);
                    }
                    case COOKIE -> {
                        allParamByBody.add(bodyParameter);
                        allParamByUrl.add(urlParameter);
                    }
                }
            }

        }
    }

    private void handleRequest(HttpRequest httpRequest) {
        requestByGetMethod = null;
        requestByPostMethod = null;
        requestByPostMethodWithJson = null;

        String oriMethod = httpRequest.method();

        if (oriMethod.equalsIgnoreCase("GET")) {
            requestByGetMethod = httpRequest;
            requestByPostMethod = httpRequest.withRemovedParameters(urlParameterList).withTransformationApplied(HttpTransformation.TOGGLE_METHOD);
            requestByPostMethodWithJson = requestByPostMethod;
        } else {
            requestByGetMethod = httpRequest.withTransformationApplied(HttpTransformation.TOGGLE_METHOD);
            requestByPostMethod = httpRequest.withTransformationApplied(HttpTransformation.TOGGLE_METHOD).withTransformationApplied(HttpTransformation.TOGGLE_METHOD);
            requestByPostMethodWithJson = requestByPostMethod.withRemovedParameters(bodyParameterList);
        }


        requestByPostMethodWithJson = requestByPostMethodWithJson.withRemovedHeader("Content-Type").withAddedHeader("Content-Type", "application/json");
    }

    private void fuzzParamAndValue(HttpRequest httpRequest, Object payloadList) {
        // Fuzz参数，先单个过一遍，然后全部组合过一遍

        handleRequestParameters(httpRequest.parameters());
        handleRequest(httpRequest);


        SetMultimap<String, String> paramValueMap = LinkedHashMultimap.create();
        if (payloadList instanceof List) {
            List<String> paramList = (List) payloadList;
            for (int i = 0; i < paramList.size(); i++) {
                paramValueMap.put(paramList.get(i), this.api.utilities().randomUtils().randomString(8, RandomUtils.CharacterSet.ASCII_LETTERS));
            }
        } else {
            paramValueMap.putAll((SetMultimap) payloadList);
        }

        for (Map.Entry<String, String> entry : paramValueMap.entries()) {
            String param = entry.getKey();
            String value = this.api.utilities().urlUtils().encode(entry.getValue());

            // GET请求，正常参数
            HttpParameter httpParameterByUrl = HttpParameter.urlParameter(param, value);
            allParamByUrl.add(httpParameterByUrl);
            requestListByGetMethod.add(requestByGetMethod.withAddedParameters(httpParameterByUrl));

            // POST请求，正常主体形式的参数
            HttpParameter httpParameterByBody = HttpParameter.bodyParameter(param, value);
            allParamByBody.add(httpParameterByBody);
            HttpRequest requestByPost = requestByPostMethod.withAddedParameters(httpParameterByBody);
            requestListByPostMethod.add(requestByPost);

            // POST请求，JSON主体形式的参数
            jsonObject.addProperty(param, value);
            HttpRequest requestWithJsonBody = generateRequestByPostMethodWithJson(requestByPostMethodWithJson, jsonObject);
            requestListByPostMethodWithJson.add(requestWithJsonBody);

            // POST Multipart请求
            if (requestByPost.parameters().size() > 0) {
                HttpRequest requestByMultipartMethod = generateRequestByMultipartMethod(requestByPost);
                requestListByPostMethodWithMultipart.add(requestByMultipartMethod);
            }

            // 添加所有参数到Cookie中
            HttpParameter httpParameterByCookie = HttpParameter.cookieParameter(param, value);
            allParamByCookie.add(httpParameterByCookie);
        }
    }

    private void fuzzEndpoint(HttpRequest httpRequest, List<String> payloadList) {
        // Fuzz参数，先单个过一遍，然后全部组合过一遍
        String oldEndpoint = httpRequest.path();
        Set<String> endpointList = generatePathList(oldEndpoint, payloadList);
        for (String endpoint : endpointList) {
            httpRequest = httpRequest.withPath(endpoint);
            handleRequestParameters(httpRequest.parameters());
            handleRequest(httpRequest);

            // GET请求，正常参数
            requestListByGetMethod.add(requestByGetMethod.withAddedParameters(allParamByUrl));

            // POST请求，正常主体形式的参数
            HttpRequest requestByPost = requestByPostMethod.withAddedParameters(allParamByBody);
            requestListByPostMethod.add(requestByPost);

            // POST请求，JSON主体形式的参数
            HttpRequest requestWithJsonBody = generateRequestByPostMethodWithJson(requestByPostMethodWithJson, jsonObject);
            requestListByPostMethodWithJson.add(requestWithJsonBody);

            // POST Multipart请求
            if (requestByPost.parameters().size() > 0) {
                HttpRequest requestByMultipartMethod = generateRequestByMultipartMethod(requestByPost);
                requestListByPostMethodWithMultipart.add(requestByMultipartMethod);
            }
        }
    }

    private HttpRequest generateRequestByPostMethodWithJson(HttpRequest httpRequest, JsonObject jsonObject) {
        // 创建一个Gson对象以转换jsonObject为json字符串
        String jsonStr = "";
        if (jsonObject == null || jsonObject.size() < 1) {
            jsonStr = "{}";
        } else {
            Gson gson = new Gson();
            jsonStr = gson.toJson(jsonObject);
        }

        String requestStr = httpRequest.toByteArray().toString();
        String newRequestStr = String.format("%s%s", requestStr, jsonStr);
        return HttpRequest.httpRequest(httpRequest.httpService(), newRequestStr).withUpdatedHeader("Content-Length", String.valueOf(jsonStr.length()));
    }

    private HttpRequest generateRequestByMultipartMethod(HttpRequest httpRequest) {
        String boundary = api.utilities().randomUtils().randomString(32, RandomUtils.CharacterSet.ASCII_LETTERS);

        StringBuilder newBody = new StringBuilder();

        if (!httpRequest.method().equalsIgnoreCase("POST")) {
            httpRequest = httpRequest.withTransformationApplied(HttpTransformation.TOGGLE_METHOD);
        }

        List<ParsedHttpParameter> parameterList = httpRequest.parameters();

        for (ParsedHttpParameter param : parameterList) {
            if (param.type() == HttpParameterType.BODY || param.type() == HttpParameterType.JSON || param.type() == HttpParameterType.URL) {
                newBody.append(String.format("--%s\r\nContent-Disposition: form-data; name=\"%s\"\r\n\r\n%s\r\n", boundary, param.name(), param.value()));
            }
        }

        newBody.append("--").append(boundary).append("--\r\n");

        httpRequest = httpRequest.withUpdatedHeader("Content-Type", "multipart/form-data; boundary=" + boundary).withBody(newBody.toString());


        return httpRequest;
    }

    private Set<String> generatePathList(String inputPath, List<String> payloadList) {
        Set<String> paths = new HashSet<>();

        for (String fuzzValue : payloadList) {
            if (inputPath.endsWith("/")) {
                inputPath = inputPath.substring(0, inputPath.length() - 1);
            }

            String[] pathElements = inputPath.split("/");
            StringBuilder subPath = new StringBuilder();

            for (String pathElement : pathElements) {
                String newPath = "";
                if (!pathElement.isBlank() && !pathElement.contains(".")) {
                    subPath.append("/").append(pathElement);
                    newPath = subPath + "/" + fuzzValue;
                    paths.add(newPath);
                } else {
                    newPath = "/" + fuzzValue;
                }
                newPath = newPath.replaceAll("/+", "/");
                paths.add(newPath);
            }
        }

        return paths;
    }

}

