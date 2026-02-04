package caa;

import java.util.List;

public class Config {
    public static String randomType = "Random";
    public static String alphanumericChars = "abcdefghijklmnopqrstuvwxyz0123456789";
    public static String allChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    public static int defaultLength = 6;

    public static String host = "gh0st.cn";

    public static String status = "404";

    public static String scopeOptions = "Suite|Target|Proxy|Scanner|Intruder|Repeater|Logger|Sequencer|Decoder|Comparer|Extensions|Organizer|Recorded login replayer";

    public static String suffix = "3g2|3gp|7z|aac|abw|aif|aifc|aiff|apk|arc|au|avi|azw|bat|bin|bmp|bz|bz2|cmd|cmx|cod|com|csh|css|csv|dll|doc|docx|ear|eot|epub|exe|flac|flv|gif|gz|ico|ics|ief|jar|jfif|jpe|jpeg|jpg|less|m3u|mid|midi|mjs|mkv|mov|mp2|mp3|mp4|mpa|mpe|mpeg|mpg|mpkg|mpp|mpv2|odp|ods|odt|oga|ogg|ogv|ogx|otf|pbm|pdf|pgm|png|pnm|ppm|ppt|pptx|ra|ram|rar|ras|rgb|rmi|rtf|scss|sh|snd|svg|swf|tar|tif|tiff|ttf|vsd|war|wav|weba|webm|webp|wmv|woff|woff2|xbm|xls|xlsx|xpm|xul|xwd|zip|avif";

    public static String[] CaATableName = new String[]{
            "Param",
            "File",
            "Path",
            "FullPath",
            "Value",
            "All Param",
            "All File",
            "All Path",
            "All FullPath"
    };

    public static volatile List<String> globalPayload = List.of();
}
