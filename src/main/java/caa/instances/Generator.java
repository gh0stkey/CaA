package caa.instances;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.params.HttpParameter;
import burp.api.montoya.http.message.params.HttpParameterType;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.requests.HttpTransformation;
import burp.api.montoya.utilities.RandomUtils;
import caa.Config;
import caa.utils.ConfigLoader;
import caa.utils.HttpUtils;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.text.MessageFormat;
import java.util.*;

public class Generator {
    private final MontoyaApi api;
    private final ConfigLoader configLoader;
    private final HttpUtils httpUtils;

    public Generator(MontoyaApi api, ConfigLoader configLoader) {
        this.api = api;
        this.configLoader = configLoader;
        this.httpUtils = new HttpUtils(api, configLoader);
    }

    public boolean generateRequest(HttpRequest request, String payload, String payloadMode, int randomStringFlag, int randomStringLength) {
        Object payloadObj = null;

        if (payload.contains("=")) {
            List<String> dataList = Arrays.stream(payload.split("\r\n")).toList();
            SetMultimap<String, String> keyValues = HashMultimap.create();
            for (String data : dataList) {
                String param = data.split("=")[0];
                String value = data.split("=")[1];
                keyValues.put(param, value);
            }
            payloadObj = keyValues;
        } else {
            payloadObj = Arrays.stream(payload.split("\r\n")).toList();
        }
        List<HttpRequest> requestList = new ArrayList<>();
        switch (payloadMode) {
            case "Param", "Value", "Current" ->
                    requestList = generateParamAndValueWithRequest(request, payloadObj, randomStringFlag, randomStringLength);
            case "FullPath", "Path", "File" ->
                    requestList = generateEndpointWithRequest(request, (List<String>) payloadObj);
        }

        if (!requestList.isEmpty()) {
            Config.globalPayload.clear();
            putPayloads(requestList);
        }

        return true;
    }

    private HttpRequest generateRequestByGetMethodRequest(HttpRequest httpRequest) {
        String method = httpRequest.method();
        List<ParsedHttpParameter> parameterList = httpRequest.parameters();
        if (method.equals("POST")) {
            httpRequest = httpRequest.withTransformationApplied(HttpTransformation.TOGGLE_METHOD);
            for (ParsedHttpParameter p : parameterList) {
                switch (p.type()) {
                    case JSON, XML -> {
                        HttpParameter urlParam = HttpParameter.urlParameter(p.name(), p.value());
                        httpRequest.withAddedParameters(urlParam);
                    }
                }
            }
        }

        return httpRequest;
    }

    private List<HttpRequest> generateParamAndValueWithRequest(HttpRequest originalRequest, Object payloadObj, int randomStringFlag, int randomStringLength) {
        SetMultimap<String, String> paramValueMap = LinkedHashMultimap.create();
        List<HttpRequest> requestList = new ArrayList<>();

        if (payloadObj instanceof List) {
            List<String> paramList = (List) payloadObj;
            for (int i = 0; i < paramList.size(); i++) {
                paramValueMap.put(paramList.get(i), generateRandomString(randomStringLength, randomStringFlag));
            }
        } else {
            paramValueMap.putAll((SetMultimap) payloadObj);
        }

        HttpRequest httpRequestByGetMethod = generateRequestByGetMethodRequest(originalRequest);
        HttpRequest httpRequestByPostMethod = httpRequestByGetMethod.withTransformationApplied(HttpTransformation.TOGGLE_METHOD);
        for (Map.Entry<String, String> entry : paramValueMap.entries()) {

            String param = entry.getKey();
            String value = entry.getValue();
            String encodeValue = this.api.utilities().urlUtils().encode(value);

            // GET请求，正常参数
            HttpParameter httpParameterByUrl = HttpParameter.urlParameter(param, encodeValue);
            httpRequestByGetMethod = httpRequestByGetMethod.withAddedParameters(httpParameterByUrl);
            requestList.add(httpRequestByGetMethod);

            // POST请求，正常主体形式的参数
            HttpParameter httpParameterByBody = HttpParameter.bodyParameter(param, encodeValue);
            httpRequestByPostMethod = httpRequestByPostMethod.withAddedParameters(httpParameterByBody);
            requestList.add(httpRequestByPostMethod);

            // POST请求，JSON主体形式的参数
            HttpRequest requestWithJsonBody = generateRequestByPostMethodWithJson(httpRequestByPostMethod);
            requestList.add(requestWithJsonBody);

            // POST请求，Multipart主体形式的参数
            HttpRequest requestByMultipartMethod = generateRequestByMultipartMethod(httpRequestByPostMethod);
            requestList.add(requestByMultipartMethod);

            // POST请求，XML主体形式的参数
            HttpRequest requestByXmlMethod = generateRequestByPostMethodWithXml(httpRequestByPostMethod);
            requestList.add(requestByXmlMethod);
        }

        return requestList;
    }

    private List<HttpRequest> generateEndpointWithRequest(HttpRequest originalRequest, List<String> payloadObj) {
        String oldEndpoint = originalRequest.path();
        Set<String> endpointList = generatePathList(oldEndpoint, payloadObj);
        List<HttpRequest> requestList = new ArrayList<>();
        HttpRequest httpRequestByGetMethod = generateRequestByGetMethodRequest(originalRequest);
        HttpRequest httpRequestByPostMethod = httpRequestByGetMethod.withTransformationApplied(HttpTransformation.TOGGLE_METHOD);

        for (String endpoint : endpointList) {
            // GET请求，正常参数
            requestList.add(httpRequestByGetMethod.withPath(endpoint));

            // POST请求，正常主体形式的参数
            requestList.add(httpRequestByPostMethod.withPath(endpoint));

            // POST请求，JSON主体形式的参数
            requestList.add(generateRequestByPostMethodWithJson(httpRequestByPostMethod.withPath(endpoint)));

            // POST请求，Multipart主体形式的参数
            requestList.add(generateRequestByMultipartMethod(httpRequestByPostMethod.withPath(endpoint)));

            // POST请求，XML主体形式的参数
            requestList.add(generateRequestByPostMethodWithXml(httpRequestByPostMethod.withPath(endpoint)));
        }

        return requestList;
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

    private void putPayloads(List<HttpRequest> httpRequestList) {
        for (HttpRequest httpRequest : httpRequestList) {
            Config.globalPayload.add(httpRequest.toString());
        }
    }

    private String getHttpRequestWithoutBody(HttpRequest httpRequest) {
        byte[] requestByteArray = httpRequest.toByteArray().getBytes();
        byte[] bodyByteArray = httpRequest.body().getBytes();
        return new String(Arrays.copyOfRange(requestByteArray, 0, requestByteArray.length - bodyByteArray.length));
    }

    private HttpRequest generateRequestByPostMethodWithXml(HttpRequest httpRequest) {
        String requestStrWithoutBody = getHttpRequestWithoutBody(httpRequest);
        List<ParsedHttpParameter> parameterList = httpRequest.parameters();
        List<String> paramValueList = new ArrayList<>();
        for (ParsedHttpParameter param : parameterList) {
            if (param.type() == HttpParameterType.BODY) {
                paramValueList.add(MessageFormat.format("<{0}>{1}</{0}>", param.name(), param.value()));
            }
        }
        String xmlStr = String.join("", paramValueList);
        String newRequestStr = String.format("%s%s", requestStrWithoutBody, xmlStr);
        return HttpRequest.httpRequest(httpRequest.httpService(), newRequestStr).withUpdatedHeader("Content-Length", String.valueOf(xmlStr.length()));
    }

    private HttpRequest generateRequestByPostMethodWithJson(HttpRequest httpRequest) {
        String requestStrWithoutBody = getHttpRequestWithoutBody(httpRequest);
        List<ParsedHttpParameter> parameterList = httpRequest.parameters();
        JsonObject jsonObject = new JsonObject();
        for (ParsedHttpParameter param : parameterList) {
            if (param.type() == HttpParameterType.BODY) {
                jsonObject.addProperty(param.name(), param.value());
            }
        }
        String jsonStr = new Gson().toJson(jsonObject);
        String newRequestStr = String.format("%s%s", requestStrWithoutBody, jsonStr);
        return HttpRequest.httpRequest(httpRequest.httpService(), newRequestStr).withUpdatedHeader("Content-Length", String.valueOf(jsonStr.length()));
    }

    private HttpRequest generateRequestByMultipartMethod(HttpRequest httpRequest) {
        String boundary = generateRandomString(32, 1);
        StringBuilder newBody = new StringBuilder();

        List<ParsedHttpParameter> parameterList = httpRequest.parameters();

        for (ParsedHttpParameter param : parameterList) {
            if (param.type() == HttpParameterType.BODY) {
                newBody.append(String.format("--%s\r\nContent-Disposition: form-data; name=\"%s\"\r\n\r\n%s\r\n", boundary, param.name(), param.value()));
            }
        }

        newBody.append("--").append(boundary).append("--\r\n");
        httpRequest = httpRequest.withUpdatedHeader("Content-Type", "multipart/form-data; boundary=" + boundary).withBody(newBody.toString());

        return httpRequest;
    }

    private String generateRandomString(int length, int strFlag) {
        return this.api.utilities().randomUtils().randomString(length, strFlag == 1 ? RandomUtils.CharacterSet.ASCII_LETTERS : RandomUtils.CharacterSet.DIGITS);
    }

    public String generateRawParam(String payload) {
        List<String> paramValueList = new ArrayList<>();
        String formatString = "{0}={1}";
        if (payload.contains("=")) {
            for (String paramValue : payload.split("\r\n")) {
                String param = paramValue.split("=")[0];
                String value = httpUtils.decodeParameter(paramValue.split("=")[1]);
                paramValueList.add(MessageFormat.format(formatString, param, value));
            }
        } else {
            for (String param : payload.split("\r\n")) {
                paramValueList.add(MessageFormat.format(formatString, param, generateRandomString(6, 1)));
            }
        }
        return String.join("&", paramValueList);
    }

    public String generateJsonParam(String payload) {
        JsonObject jsonObject = new JsonObject();
        if (payload.contains("=")) {
            for (String paramValue : payload.split("\r\n")) {
                String param = paramValue.split("=")[0];
                String value = httpUtils.decodeParameter(paramValue.split("=")[1]);
                jsonObject.addProperty(param, value);
            }
        } else {
            for (String param : payload.split("\r\n")) {
                jsonObject.addProperty(param, generateRandomString(6, 1));
            }
        }
        return new Gson().toJson(jsonObject);
    }

    public String generateXmlParam(String payload) {
        List<String> paramValueList = new ArrayList<>();
        String formatString = "<{0}>{1}</{0}>";
        if (payload.contains("=")) {
            for (String paramValue : payload.split("\r\n")) {
                String param = paramValue.split("=")[0];
                String value = httpUtils.decodeParameter(paramValue.split("=")[1]);
                paramValueList.add(MessageFormat.format(formatString, param, value));
            }
        } else {
            for (String param : payload.split("\r\n")) {
                paramValueList.add(MessageFormat.format(formatString, param, generateRandomString(5, 1)));
            }
        }
        return String.join("", paramValueList);
    }
}

