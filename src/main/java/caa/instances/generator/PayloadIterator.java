package caa.instances.generator;

import burp.api.montoya.http.message.params.HttpParameter;
import burp.api.montoya.http.message.params.HttpParameterType;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.requests.HttpTransformation;
import caa.Config;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.text.MessageFormat;
import java.util.*;

public class PayloadIterator implements Iterator<String> {

    private final GeneratorConfig config;
    private final List<String> expandedEndpoints;
    private final List<MethodVariant> methodVariants;
    private final boolean isValueMode;

    // Param 模式：线性累加
    private final List<Map.Entry<String, String>> paramEntries;

    // Value 模式：累积笛卡尔积
    private final List<String> paramNames;
    private final Map<String, List<String>> paramValuesByName;
    private int[] paramValueIndices;
    private int paramStageIndex = 0;
    private int paramStageComboIndex = 0;
    private int currentStageTotal = 0;

    private final int totalParamIterations;

    private int endpointIndex = 0;
    private int paramIndex = 0;
    private int methodIndex = 0;

    private record MethodVariant(String method, ParamLocation location) {}

    public PayloadIterator(GeneratorConfig config) {
        this.config = config;
        this.isValueMode = config.isValueMode();

        if (config.hasEndpoints()) {
            this.expandedEndpoints = new ArrayList<>(
                expandEndpoints(
                    config.getBaseRequest().path(),
                    config.getEndpoints(),
                    config.getEndpointType()
                )
            );
        } else {
            this.expandedEndpoints = List.of("");
        }

        if (config.hasParams()) {
            if (isValueMode) {
                // Value 模式：按参数名分组
                this.paramValuesByName = groupParamsByName(
                    config.getParams(),
                    config.getValueType(),
                    config.getValueInput(),
                    config.getValueLength()
                );
                this.paramNames = new ArrayList<>(paramValuesByName.keySet());
                this.paramValueIndices = new int[paramNames.size()];
                this.paramEntries = null;
                this.totalParamIterations = calculateCumulativeCartesian();
                if (!paramNames.isEmpty()) {
                    this.currentStageTotal = paramValuesByName
                        .get(paramNames.get(0))
                        .size();
                }
            } else {
                // Param 模式：线性累加
                this.paramEntries = expandParamsLinear(
                    config.getParams(),
                    config.getValueType(),
                    config.getValueInput(),
                    config.getValueLength()
                );
                this.paramNames = null;
                this.paramValuesByName = null;
                this.paramValueIndices = null;
                this.totalParamIterations = paramEntries.size();
            }
        } else {
            this.paramEntries = List.of();
            this.paramNames = List.of();
            this.paramValuesByName = Map.of();
            this.paramValueIndices = null;
            this.totalParamIterations = 0;
        }

        this.methodVariants = buildMethodVariants(config.getSelectedMethods());
    }

    // 累积笛卡尔积总数
    private int calculateCumulativeCartesian() {
        if (paramNames == null || paramNames.isEmpty()) return 0;
        int total = 0;
        int product = 1;
        for (String name : paramNames) {
            product *= paramValuesByName.get(name).size();
            total += product;
        }
        return total;
    }

    @Override
    public boolean hasNext() {
        if (methodVariants.isEmpty()) {
            return false;
        }

        if (totalParamIterations == 0) {
            return endpointIndex < expandedEndpoints.size();
        } else if (!config.hasEndpoints()) {
            return paramIndex < totalParamIterations;
        } else {
            return endpointIndex < expandedEndpoints.size();
        }
    }

    @Override
    public String next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }

        String result;

        if (totalParamIterations == 0) {
            result = generateEndpointOnlyRequest();
            advanceEndpointOnly();
        } else if (isValueMode) {
            result = generateValueModeRequest();
            advanceValueMode();
        } else {
            result = generateParamModeRequest();
            advanceParamMode();
        }

        return result;
    }

    private String generateEndpointOnlyRequest() {
        String endpoint = expandedEndpoints.get(endpointIndex);
        MethodVariant variant = methodVariants.get(methodIndex);

        HttpRequest baseRequest = config.getBaseRequest();
        HttpRequest getRequest = prepareGetRequest(baseRequest);
        HttpRequest postRequest = getRequest.withTransformationApplied(
            HttpTransformation.TOGGLE_METHOD
        );

        if (!endpoint.isEmpty()) {
            getRequest = getRequest.withPath(endpoint);
            postRequest = postRequest.withPath(endpoint);
        }

        return buildMethodRequest(getRequest, postRequest, variant).toString();
    }

    // Param 模式：线性累加请求
    private String generateParamModeRequest() {
        HttpRequest baseRequest = config.getBaseRequest();

        if (config.hasEndpoints() && endpointIndex < expandedEndpoints.size()) {
            String endpoint = expandedEndpoints.get(endpointIndex);
            if (!endpoint.isEmpty()) {
                baseRequest = baseRequest.withPath(endpoint);
            }
        }

        HttpRequest getRequest = prepareGetRequest(baseRequest);
        HttpRequest postRequest = getRequest.withTransformationApplied(
            HttpTransformation.TOGGLE_METHOD
        );

        // 累加参数从 0 到 paramIndex
        for (int i = 0; i <= paramIndex && i < paramEntries.size(); i++) {
            Map.Entry<String, String> entry = paramEntries.get(i);
            String encodedValue = urlEncode(entry.getValue());
            getRequest = getRequest.withAddedParameters(
                HttpParameter.urlParameter(entry.getKey(), encodedValue)
            );
            postRequest = postRequest.withAddedParameters(
                HttpParameter.bodyParameter(entry.getKey(), encodedValue)
            );
        }

        MethodVariant variant = methodVariants.get(methodIndex);
        return buildMethodRequest(getRequest, postRequest, variant).toString();
    }

    // Value 模式：累积笛卡尔积请求
    private String generateValueModeRequest() {
        HttpRequest baseRequest = config.getBaseRequest();

        if (config.hasEndpoints() && endpointIndex < expandedEndpoints.size()) {
            String endpoint = expandedEndpoints.get(endpointIndex);
            if (!endpoint.isEmpty()) {
                baseRequest = baseRequest.withPath(endpoint);
            }
        }

        HttpRequest getRequest = prepareGetRequest(baseRequest);
        HttpRequest postRequest = getRequest.withTransformationApplied(
            HttpTransformation.TOGGLE_METHOD
        );

        // 添加当前阶段的参数组合（参数名 0 到 paramStageIndex）
        for (int i = 0; i <= paramStageIndex && i < paramNames.size(); i++) {
            String paramName = paramNames.get(i);
            List<String> values = paramValuesByName.get(paramName);
            String value = values.get(paramValueIndices[i]);
            String encodedValue = urlEncode(value);

            getRequest = getRequest.withAddedParameters(
                HttpParameter.urlParameter(paramName, encodedValue)
            );
            postRequest = postRequest.withAddedParameters(
                HttpParameter.bodyParameter(paramName, encodedValue)
            );
        }

        MethodVariant variant = methodVariants.get(methodIndex);
        return buildMethodRequest(getRequest, postRequest, variant).toString();
    }

    private void advanceEndpointOnly() {
        methodIndex++;
        if (methodIndex >= methodVariants.size()) {
            methodIndex = 0;
            endpointIndex++;
        }
    }

    private void advanceParamMode() {
        methodIndex++;
        if (methodIndex >= methodVariants.size()) {
            methodIndex = 0;
            paramIndex++;

            if (config.hasEndpoints() && paramIndex >= paramEntries.size()) {
                paramIndex = 0;
                endpointIndex++;
            }
        }
    }

    private void advanceValueMode() {
        methodIndex++;
        if (methodIndex >= methodVariants.size()) {
            methodIndex = 0;
            paramIndex++;
            paramStageComboIndex++;

            // 推进当前阶段的笛卡尔积索引
            advanceStageIndices();

            // 检查是否需要进入下一个阶段
            if (paramStageComboIndex >= currentStageTotal) {
                paramStageIndex++;
                paramStageComboIndex = 0;
                Arrays.fill(paramValueIndices, 0);
                if (paramStageIndex < paramNames.size()) {
                    currentStageTotal = calculateStageTotal(paramStageIndex);
                }
            }

            // 检查是否需要进入下一个 endpoint
            if (config.hasEndpoints() && paramIndex >= totalParamIterations) {
                paramIndex = 0;
                paramStageIndex = 0;
                paramStageComboIndex = 0;
                Arrays.fill(paramValueIndices, 0);
                if (!paramNames.isEmpty()) {
                    currentStageTotal = paramValuesByName
                        .get(paramNames.get(0))
                        .size();
                }
                endpointIndex++;
            }
        }
    }

    // 推进当前阶段内的笛卡尔积索引
    private void advanceStageIndices() {
        for (int i = paramStageIndex; i >= 0; i--) {
            paramValueIndices[i]++;
            if (
                paramValueIndices[i] <
                paramValuesByName.get(paramNames.get(i)).size()
            ) {
                break;
            }
            paramValueIndices[i] = 0;
        }
    }

    // 计算阶段 k 的笛卡尔积总数（参数 0 到 k 的值数量乘积）
    private int calculateStageTotal(int stageIndex) {
        int total = 1;
        for (int i = 0; i <= stageIndex && i < paramNames.size(); i++) {
            total *= paramValuesByName.get(paramNames.get(i)).size();
        }
        return total;
    }

    private HttpRequest prepareGetRequest(HttpRequest request) {
        String method = request.method();
        if ("POST".equals(method)) {
            request = request.withTransformationApplied(
                HttpTransformation.TOGGLE_METHOD
            );
            for (ParsedHttpParameter p : request.parameters()) {
                if (
                    p.type() == HttpParameterType.JSON ||
                    p.type() == HttpParameterType.XML
                ) {
                    request = request.withAddedParameters(
                        HttpParameter.urlParameter(p.name(), p.value())
                    );
                }
            }
        }
        return request;
    }

    private HttpRequest buildMethodRequest(
        HttpRequest getRequest,
        HttpRequest postRequest,
        MethodVariant variant
    ) {
        return switch (variant.method()) {
            case "GET" -> getRequest;
            case "POST Form" -> applyParamLocation(
                getRequest,
                postRequest,
                variant.location()
            );
            case "POST JSON" -> generateJsonRequest(
                applyParamLocation(getRequest, postRequest, variant.location()),
                variant.location()
            );
            case "POST XML" -> generateXmlRequest(
                applyParamLocation(getRequest, postRequest, variant.location()),
                variant.location()
            );
            case "POST Multipart" -> generateMultipartRequest(
                applyParamLocation(getRequest, postRequest, variant.location()),
                variant.location()
            );
            default -> getRequest;
        };
    }

    private HttpRequest applyParamLocation(
        HttpRequest getRequest,
        HttpRequest postRequest,
        ParamLocation location
    ) {
        if (location == ParamLocation.QUERY_ONLY) {
            HttpRequest result = postRequest;
            for (ParsedHttpParameter param : getRequest.parameters()) {
                if (param.type() == HttpParameterType.URL) {
                    result = result.withAddedParameters(
                        HttpParameter.urlParameter(param.name(), param.value())
                    );
                }
            }
            return result.withBody("");
        }
        return postRequest;
    }

    private HttpRequest generateJsonRequest(
        HttpRequest request,
        ParamLocation location
    ) {
        String headerPart = getRequestWithoutBody(request);
        JsonObject json = new JsonObject();

        if (location != ParamLocation.QUERY_ONLY) {
            for (ParsedHttpParameter param : request.parameters()) {
                if (param.type() == HttpParameterType.BODY) {
                    json.addProperty(param.name(), param.value());
                }
            }
        }

        String jsonStr = new Gson().toJson(json);
        return HttpRequest.httpRequest(
            request.httpService(),
            headerPart + jsonStr
        )
            .withUpdatedHeader(
                "Content-Length",
                String.valueOf(jsonStr.length())
            )
            .withUpdatedHeader("Content-Type", "application/json");
    }

    private HttpRequest generateXmlRequest(
        HttpRequest request,
        ParamLocation location
    ) {
        String headerPart = getRequestWithoutBody(request);
        List<String> parts = new ArrayList<>();

        if (location != ParamLocation.QUERY_ONLY) {
            for (ParsedHttpParameter param : request.parameters()) {
                if (param.type() == HttpParameterType.BODY) {
                    parts.add(
                        MessageFormat.format(
                            "<{0}>{1}</{0}>",
                            param.name(),
                            param.value()
                        )
                    );
                }
            }
        }

        String xmlStr = String.join("", parts);
        return HttpRequest.httpRequest(
            request.httpService(),
            headerPart + xmlStr
        ).withUpdatedHeader("Content-Length", String.valueOf(xmlStr.length()));
    }

    private HttpRequest generateMultipartRequest(
        HttpRequest request,
        ParamLocation location
    ) {
        String boundary = generateRandomString(32);
        StringBuilder body = new StringBuilder();

        if (location != ParamLocation.QUERY_ONLY) {
            for (ParsedHttpParameter param : request.parameters()) {
                if (param.type() == HttpParameterType.BODY) {
                    body.append(
                        String.format(
                            "--%s\r\nContent-Disposition: form-data; name=\"%s\"\r\n\r\n%s\r\n",
                            boundary,
                            param.name(),
                            param.value()
                        )
                    );
                }
            }
        }

        body.append("--").append(boundary).append("--\r\n");
        return request
            .withUpdatedHeader(
                "Content-Type",
                "multipart/form-data; boundary=" + boundary
            )
            .withBody(body.toString());
    }

    private String getRequestWithoutBody(HttpRequest request) {
        byte[] full = request.toByteArray().getBytes();
        byte[] bodyBytes = request.body().getBytes();
        return new String(
            Arrays.copyOfRange(full, 0, full.length - bodyBytes.length)
        );
    }

    // === Static helpers ===

    private static Map.Entry<String, String> parseParamItem(
        String item,
        String valueType,
        String valueInput,
        int valueLength
    ) {
        String name, value;
        if (item.contains("=")) {
            String[] parts = item.split("=", 2);
            name = parts[0];
            value =
                parts.length > 1 && !parts[1].isEmpty()
                    ? parts[1]
                    : generateValue(valueType, valueInput, valueLength);
        } else {
            name = item;
            value = generateValue(valueType, valueInput, valueLength);
        }
        return Map.entry(name, value);
    }

    private static List<Map.Entry<String, String>> expandParamsLinear(
        List<String> params,
        String valueType,
        String valueInput,
        int valueLength
    ) {
        List<Map.Entry<String, String>> entries = new ArrayList<>();
        for (String item : params) {
            entries.add(
                parseParamItem(item, valueType, valueInput, valueLength)
            );
        }
        return entries;
    }

    private static Map<String, List<String>> groupParamsByName(
        List<String> params,
        String valueType,
        String valueInput,
        int valueLength
    ) {
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (String item : params) {
            Map.Entry<String, String> entry = parseParamItem(
                item,
                valueType,
                valueInput,
                valueLength
            );
            grouped
                .computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                .add(entry.getValue());
        }
        return grouped;
    }

    public static Set<String> expandEndpoints(
        String basePath,
        List<String> endpoints,
        String type
    ) {
        if ("FullPath".equals(type)) {
            return new LinkedHashSet<>(endpoints);
        } else if ("File".equals(type)) {
            return expandFileEndpoints(basePath, endpoints);
        } else {
            return expandPathEndpoints(basePath, endpoints);
        }
    }

    private static Set<String> expandPathEndpoints(
        String inputPath,
        List<String> payloads
    ) {
        Set<String> paths = new HashSet<>();
        String path = inputPath.endsWith("/")
            ? inputPath.substring(0, inputPath.length() - 1)
            : inputPath;

        for (String fuzz : payloads) {
            String[] elements = path.split("/");
            StringBuilder subPath = new StringBuilder();

            for (String elem : elements) {
                if (!elem.isBlank() && !elem.contains(".")) {
                    subPath.append("/").append(elem);
                    paths.add((subPath + "/" + fuzz).replaceAll("/+", "/"));
                } else {
                    paths.add(("/" + fuzz).replaceAll("/+", "/"));
                }
            }
        }
        return paths;
    }

    private static Set<String> expandFileEndpoints(
        String inputPath,
        List<String> payloads
    ) {
        Set<String> paths = new LinkedHashSet<>();
        String path = inputPath.endsWith("/")
            ? inputPath.substring(0, inputPath.length() - 1)
            : inputPath;

        int lastSlash = path.lastIndexOf('/');
        String lastSegment =
            lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
        String basePath = lastSegment.contains(".")
            ? (lastSlash >= 0 ? path.substring(0, lastSlash) : "")
            : path;

        for (String file : payloads) {
            String newPath = (basePath + "/" + file).replaceAll("/+", "/");
            if (!newPath.startsWith("/")) newPath = "/" + newPath;
            paths.add(newPath);
        }
        return paths;
    }

    private static List<MethodVariant> buildMethodVariants(
        Map<String, ParamLocation> methods
    ) {
        List<MethodVariant> variants = new ArrayList<>();

        if (methods.containsKey("GET")) {
            variants.add(new MethodVariant("GET", ParamLocation.QUERY_ONLY));
        }

        for (String method : List.of(
            "POST Form",
            "POST JSON",
            "POST Multipart",
            "POST XML"
        )) {
            if (methods.containsKey(method)) {
                ParamLocation loc = methods.get(method);
                if (loc == ParamLocation.BOTH) {
                    variants.add(
                        new MethodVariant(method, ParamLocation.BODY_ONLY)
                    );
                    variants.add(
                        new MethodVariant(method, ParamLocation.QUERY_ONLY)
                    );
                } else {
                    variants.add(new MethodVariant(method, loc));
                }
            }
        }

        return variants;
    }

    private static String generateValue(
        String valueType,
        String valueInput,
        int length
    ) {
        if ("Random".equals(valueType) && Config.api != null) {
            return Config.api
                .utilities()
                .randomUtils()
                .randomString(length, valueInput);
        }
        return valueInput;
    }

    private static String generateRandomString(int length) {
        if (Config.api != null) {
            return Config.api
                .utilities()
                .randomUtils()
                .randomString(length, Config.allChars);
        }
        Random r = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(
                Config.allChars.charAt(r.nextInt(Config.allChars.length()))
            );
        }
        return sb.toString();
    }

    private static String urlEncode(String value) {
        if (Config.api != null) {
            return Config.api.utilities().urlUtils().encode(value);
        }
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }
}
