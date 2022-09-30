package burp;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * @author EvilChen
 */

public class Config {

    // CaA配置文件路径


    public static String CaAPath = String.format("%s/.config/CaA", System.getProperty("user.home"));
    public static String CaAConfigPath = String.format("%s/%s", CaAPath, "Config.json");

    // 数据库连接状态
    public static boolean isConnect = false;

    // 数据库连接
    public static Connection CaAConn = null;

    // CaA数据库表名
    public static String[] CaATableName = new String[]{
            "Param",
            "Endpoint",
            "File",
            "Path",
            "FullPath",
    };

    // CaA数据库表名
    public static String[] CaATablesName = new String[]{
            "Params",
            "Endpoints",
            "Files",
            "Paths",
            "FullPaths"
    };

    // 合并表内容
    public static String[] concatTableName() {
        String[] tableName = new String[CaATableName.length + CaATablesName.length];
        System.arraycopy(CaATableName, 0, tableName, 0, CaATableName.length);
        System.arraycopy(CaATablesName, 0, tableName, CaATableName.length, CaATablesName.length);
        return tableName;
    }

    // CaA数据库连接Url
    public static void setConn(String host, String port, String username, String password,
                               String database) {
        String jdbcUrl = String.format("jdbc:mysql://%s:%s/%s?useServerPrepStmts=true", host, port,
                database);
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            CaAConn = DriverManager.getConnection(jdbcUrl, username, password);
            isConnect = true;
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    // 排除后缀名
    public static String excludeSuffix = "3g2|3gp|7z|aac|abw|aif|aifc|aiff|arc|au|avi|azw|bin|bmp|bz|bz2|cmx|cod|csh|css|csv|doc|docx|eot|epub|gif|gz|ico|ics|ief|jar|jfif|jpe|jpeg|jpg|m3u|mid|midi|mjs|mp2|mp3|mpa|mpe|mpeg|mpg|mpkg|mpp|mpv2|odp|ods|odt|oga|ogv|ogx|otf|pbm|pdf|pgm|png|pnm|ppm|ppt|pptx|ra|ram|rar|ras|rgb|rmi|rtf|snd|svg|swf|tar|tif|tiff|ttf|vsd|wav|weba|webm|webp|woff|woff2|xbm|xls|xlsx|xpm|xul|xwd|zip|zip";

}
