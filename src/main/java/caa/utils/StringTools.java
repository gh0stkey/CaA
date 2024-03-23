package caa.utils;

import java.net.URL;

public class StringTools {
    public static String getHost(String url) {
        String host = "";

        try {
            URL u = new URL(url);
            host = u.getHost();
        } catch (Exception ignored) {
        }

        return host;
    }
}
