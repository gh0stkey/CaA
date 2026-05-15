package caa.utils;

import burp.api.montoya.MontoyaApi;
import caa.Config;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * 参数格式化工具类
 * 用于Datatable的参数预览/复制功能
 */
public class ParamFormatter {

    private final MontoyaApi api;
    private final HttpUtils httpUtils;

    public ParamFormatter(MontoyaApi api, ConfigLoader configLoader) {
        this.api = api;
        this.httpUtils = new HttpUtils(api, configLoader);
    }

    private String generateRandomString(
            String valueType,
            String valueInput,
            int valueLength
    ) {
        if (valueType.equals("Random")) {
            return this.api.utilities()
                    .randomUtils()
                    .randomString(valueLength, valueInput);
        } else {
            return valueInput;
        }
    }

    private List<String[]> parsePayloadPairs(
            String payload,
            String customValue
    ) {
        List<String[]> pairs = new ArrayList<>();
        for (String line : payload.split("\r\n")) {
            String param;
            if (payload.contains("=") && line.contains("=")) {
                param = line.split("=")[0];
            } else {
                param = line;
            }
            String value;
            if (customValue != null) {
                value = customValue;
            } else if (payload.contains("=") && line.contains("=")) {
                value = httpUtils.decodeParameter(line.split("=")[1]);
            } else {
                value = generateRandomString(
                        Config.randomType,
                        Config.alphanumericChars,
                        Config.defaultLength
                );
            }
            pairs.add(new String[]{param, value});
        }
        return pairs;
    }

    public String generateRawParam(
            String payload,
            String formatChar,
            String delimiter
    ) {
        return generateRawParam(payload, formatChar, delimiter, null);
    }

    public String generateRawParam(
            String payload,
            String formatChar,
            String delimiter,
            String customValue
    ) {
        List<String> paramValueList = new ArrayList<>();
        String formatString = "{0}{1}{2}";
        for (String[] pair : parsePayloadPairs(payload, customValue)) {
            paramValueList.add(
                    MessageFormat.format(formatString, pair[0], formatChar, pair[1])
            );
        }
        return String.join(delimiter, paramValueList);
    }

    public String generateJsonParam(String payload) {
        return generateJsonParam(payload, null);
    }

    public String generateJsonParam(String payload, String customValue) {
        JsonObject jsonObject = new JsonObject();
        for (String[] pair : parsePayloadPairs(payload, customValue)) {
            jsonObject.addProperty(pair[0], pair[1]);
        }
        return new Gson().toJson(jsonObject);
    }

    public String generateXmlParam(String payload) {
        return generateXmlParam(payload, null);
    }

    public String generateXmlParam(String payload, String customValue) {
        List<String> paramValueList = new ArrayList<>();
        String formatString = "<{0}>{1}</{0}>";
        for (String[] pair : parsePayloadPairs(payload, customValue)) {
            paramValueList.add(
                    MessageFormat.format(formatString, pair[0], pair[1])
            );
        }
        return String.join("", paramValueList);
    }
}
