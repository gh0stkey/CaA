package burp.db;

import burp.Config;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

/**
 * @author 0chencc && EvilChen
 */

public class ProcessDB {


    /**
     * 数据库初始化（需要自建数据库）
     */
    public static void initDB(String db) {
        Connection conn = Config.CaAConn;

        HashMap tables = new HashMap(10) {
        };

        String sqlByCreate = "create table %s (id int UNSIGNED AUTO_INCREMENT,%s value varchar(300) null,count int null,PRIMARY KEY (id));";

        String[] tableName = Config.concatTableName();
        // 循环遍历
        for (String name : tableName) {
            String tmlSQL;
            if (name.contains("s")) {
                tmlSQL = String.format(sqlByCreate, name, "");
            } else {
                tmlSQL = String.format(sqlByCreate, name, " host varchar(300) null,");
            }

            tables.put(name, tmlSQL);
        }

        try {
            ResultSet set = conn.getMetaData().getTables(db, null, "%", null);
            // 查询表，如若不存在则创建
            while (set.next()) {
                String i = set.getString("TABLE_NAME");
                tables.remove(i);
            }
            for (Object sql : tables.values()) {
                PreparedStatement psSql = conn.prepareStatement((String) sql);
                psSql.execute();
            }
        } catch (SQLException ignored) {

        }
    }

    /**
     * 查询数据
     */
    public static ResultSet queryData(String table, String host, String limit) {
        Connection conn = Config.CaAConn;
        String sql = "select * from " + table + " %s order by count desc limit ?;";
        PreparedStatement psSql;
        ResultSet resultSet = null;
        // 判断Host有无内容
        try {
            if ("".equals(host)) {
                sql = String.format(sql, "");
                psSql = conn.prepareStatement(sql);
                psSql.setInt(1, Integer.parseInt(limit));
            } else {
                sql = String.format(sql, "where host = ?");
                psSql = conn.prepareStatement(sql);
                psSql.setString(1, host);
                psSql.setInt(2, Integer.parseInt(limit));
            }
            resultSet = psSql.executeQuery();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return resultSet;
    }

    /**
     * 插入数据
     */
    public static void insertData(int tableIndex, String host, String value) {
        Connection conn = Config.CaAConn;
        try {
            String[] tableName = new String[]{Config.CaATableName[tableIndex],
                    Config.CaATablesName[tableIndex]};
            for (String table : tableName) {
                PreparedStatement psSql;
                String sqlBySelectCount =
                        "select * from " + table + " where value = ?";
                // 编译SQL语句 && 判断表
                if (table.contains("s")) {
                    sqlBySelectCount += ";";
                    psSql = conn.prepareStatement(sqlBySelectCount);
                    psSql.setString(1, value);
                } else {
                    sqlBySelectCount += " and host = ?;";
                    psSql = conn.prepareStatement(sqlBySelectCount);
                    psSql.setString(1, value);
                    psSql.setString(2, host);
                }

                // 查询count
                ResultSet resultSet = psSql.executeQuery();
                resultSet.next();
                int count = 0;
                try {
                    count = resultSet.getInt("count");
                } catch (Exception ignored) {

                }
                // 当count>=1时表示数据已经存在表中，这样就可以直接更新count字段即可，否则就插入新的内容
                if (count >= 1) {
                    // Update
                    String sqlByUpdateCount = String.format(
                            "update %s set count = ? where value = ?",
                            table);
                    // 判断表
                    if (table.contains("s")) {
                        sqlByUpdateCount += ";";
                        psSql = conn.prepareStatement(sqlByUpdateCount);
                        psSql.setInt(1, count + 1);
                        psSql.setString(2, value);
                    } else {
                        sqlByUpdateCount += " and host = ?;";
                        psSql = conn.prepareStatement(sqlByUpdateCount);
                        psSql.setInt(1, count + 1);
                        psSql.setString(2, value);
                        psSql.setString(3, host);
                    }

                } else {
                    // Insert into
                    String sqlByInsertData = String.format(
                            "insert into %s",
                            table);
                    // 判断表
                    if (table.contains("s")) {
                        sqlByInsertData += " (value, count) VALUES (?, 1);";
                        psSql = conn.prepareStatement(sqlByInsertData);
                        psSql.setString(1, value);
                    } else {
                        sqlByInsertData += " (host, value, count) VALUES (?, ?, 1);";
                        psSql = conn.prepareStatement(sqlByInsertData);
                        psSql.setString(1, host);
                        psSql.setString(2, value);
                    }
                }
                psSql.execute();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
