package caa.utils;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

import java.net.URL;
import java.util.Arrays;
import java.util.List;

public class HttpUtils {
    private final MontoyaApi api;
    private final ConfigLoader configLoader;

    public HttpUtils(MontoyaApi api, ConfigLoader configLoader) {
        this.api = api;
        this.configLoader = configLoader;
    }

    public String decodeParameter(String input) {
        try {
            input = api.utilities().urlUtils().decode(input);
        } catch (Exception ignored) {
        }
        return input;
    }

    public static String replaceFirstOccurrence(String original, String find, String replace) {
        int index = original.indexOf(find);
        if (index != -1) {
            return original.substring(0, index) + replace + original.substring(index + find.length());
        }
        return original;
    }

    public static boolean matchFromEnd(String input, String pattern) {
        int inputLength = input.length();
        int patternLength = pattern.length();

        int inputIndex = inputLength - 1;
        int patternIndex = patternLength - 1;

        while (inputIndex >= 0 && patternIndex >= 0) {
            if (input.charAt(inputIndex) != pattern.charAt(patternIndex)) {
                return false;
            }
            inputIndex--;
            patternIndex--;
        }

        // 如果patternIndex为-1，表示pattern字符串已经完全匹配
        return patternIndex == -1;
    }

    public String getHostByUrl(String url) {
        String host = "";

        try {
            URL u = new URL(url);
            int port = u.getPort();
            if (port == -1) {
                host = u.getHost();
            } else {
                host = String.format("%s:%s", u.getHost(), port);
            }
        } catch (Exception ignored) {
        }

        return host;
    }

    private boolean isBlockHost(String[] hostList, String host) {
        boolean isBlockHost = false;
        for (String hostName : hostList) {
            String cleanedHost = replaceFirstOccurrence(hostName, "*.", "");
            if (hostName.contains("*.") && matchFromEnd(host, cleanedHost)) {
                isBlockHost = true;
            } else if (host.equals(hostName) || hostName.equals("*")) {
                isBlockHost = true;
            }
        }
        return isBlockHost;
    }

    public boolean verifyHttpRequestResponse(HttpRequestResponse requestResponse, String toolType) {
        HttpRequest request = requestResponse.request();
        HttpResponse response = requestResponse.response();
        boolean retStatus = false;
        try {
            String host = getHostByUrl(request.url());
            String[] hostList = configLoader.getBlockHost().split("\\|");
            boolean isBlockHost = isBlockHost(hostList, host);

            List<String> suffixList = Arrays.asList(configLoader.getExcludeSuffix().split("\\|"));
            boolean isExcludeSuffix = suffixList.contains(request.fileExtension().toLowerCase());

            boolean isToolScope = !configLoader.getScope().contains(toolType);

            List<String> statusList = Arrays.asList(configLoader.getExcludeStatus().split("\\|"));
            boolean isExcludeStatus = statusList.contains(String.valueOf(response.statusCode()));

            retStatus = isExcludeSuffix || isBlockHost || isToolScope || isExcludeStatus;
        } catch (Exception ignored) {
        }

        return retStatus;
    }
}
