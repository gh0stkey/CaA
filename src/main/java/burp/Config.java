package burp;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * @author EvilChen
 */

public class Config {

    // CaA配置文件路径
    public static String CaAConfig = "CaA-Config.json";

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

}
