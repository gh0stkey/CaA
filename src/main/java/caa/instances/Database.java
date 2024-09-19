package caa.instances;

import burp.api.montoya.MontoyaApi;
import caa.Config;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;

import java.sql.*;
import java.util.*;

public class Database {
    private final MontoyaApi api;
    private Connection connection = null;

    public Database(MontoyaApi api, String dbFileName) {
        this.api = api;
        try {
            Class.forName("org.sqlite.JDBC");
            this.connection = DriverManager.getConnection(String.format("jdbc:sqlite:%s", dbFileName));
            Statement statement = this.connection.createStatement();

            // 开启 WAL 模式
            statement.executeUpdate("PRAGMA journal_mode=WAL;");

            // 修改分页大小
            statement.executeUpdate("PRAGMA page_size = 8192;");

            // 开启自动检查点模式
            statement.executeUpdate("PRAGMA wal_autocheckpoint = 2000;");

            // 关闭同步模式
            statement.executeUpdate("PRAGMA synchronous=OFF;");

            // 启用缓存
            statement.executeUpdate("PRAGMA cache_size = 10000;");

            // 设置临时表存储在内存中
            statement.executeUpdate("PRAGMA temp_store = MEMORY;");

            // 禁用安全删除
            statement.executeUpdate("PRAGMA secure_delete = OFF;");

            // 禁用外键约束
            statement.executeUpdate("PRAGMA foreign_keys = OFF;");

            // 优化数据库
            statement.executeUpdate("PRAGMA optimize;");

            // 初始化数据表
            createTables();
        } catch (Exception e) {
            api.logging().logToError(e);
        }
    }

    public Object selectData(String host, String tableName, String limitSize) {
        try {
            if (!connection.isClosed()) {
                String sql = "select name%s from `" + tableName + "` %s order by count desc";
                if (!limitSize.isBlank()) {
                    sql += " limit " + limitSize;
                }
                if (tableName.contains("All")) {
                    sql = String.format(sql, "", "");
                } else if (tableName.equals("Value")) {
                    sql = String.format(sql, ",value", "where host = ?");
                } else {
                    sql = String.format(sql, "", "where host = ?");
                }

                PreparedStatement ps = connection.prepareStatement(sql);
                prepareStatement(host, ps);
                ResultSet rs = ps.executeQuery();

                // 判断结果集是否为空
                if (!rs.isBeforeFirst()) {
                    return null;
                } else {
                    if (tableName.equals("Value")) {
                        SetMultimap<String, String> multimap = LinkedHashMultimap.create();
                        while (rs.next()) {
                            String key = rs.getString(1);
                            String value = rs.getString(2);
                            multimap.put(key, value);
                        }
                        if (multimap.size() <= 0) {
                            return null;
                        }
                        return multimap;
                    } else {
                        Set<String> resultList = new LinkedHashSet<>();
                        while (rs.next()) {
                            String columnValue = rs.getString(1);
                            resultList.add(columnValue);
                        }
                        if (resultList.isEmpty()) {
                            return null;
                        }
                        return resultList;
                    }
                }
            }
        } catch (Exception e) {
            api.logging().logToError(e);
        }

        return null;
    }

    private void createTables() {
        String sqlTemplate = """
                CREATE TABLE IF NOT EXISTS `%s` (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  name TEXT(300) DEFAULT NULL,
                  %s count INTEGER DEFAULT 0%s
                );""";
        for (String name : Config.CaATableName) {
            String fields = "";
            String uniqueField = "";
            if (!name.contains("All")) {
                fields = "host TEXT(300) DEFAULT NULL,";
                uniqueField = ", UNIQUE(name, host%s)";
                if (name.equals("Value")) {
                    fields = String.format("value TEXT(300) DEFAULT NULL, %s", fields);
                    uniqueField = String.format(uniqueField, ", value");
                } else {
                    uniqueField = String.format(uniqueField, "");
                }
            } else {
                uniqueField = ", UNIQUE(name)";
            }
            try {
                Statement stmt = connection.createStatement();
                stmt.execute(String.format(sqlTemplate, name, fields, uniqueField));
            } catch (Exception e) {
                api.logging().logToError(e);
            }
        }
    }

    public List<String> getAllHosts(String tableName) {
        Set<String> setHostList = new HashSet<>();
        if (Arrays.stream(Config.CaATableName).anyMatch(t -> t.equalsIgnoreCase(tableName))) {
            try {
                Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery(String.format("SELECT host FROM `%s` WHERE host IS NOT NULL", tableName));

                while (rs.next()) {
                    String host = rs.getString("host");
                    setHostList.add(host);
                }
            } catch (SQLException e) {
                api.logging().logToError(e);
            }
        }
        return new ArrayList<>(setHostList);
    }

    public void insertData(String host, Map<String, Object> dataObj) {
        try {
            connection.setAutoCommit(false);
            dataObj.forEach((k, v) -> {
                try {
                    PreparedStatement insertPs = generateInsertSql(k);
                    PreparedStatement updatePs = generateUpdateSql(k);

                    if (v instanceof HashSet<?>) {
                        for (String data : (HashSet<String>) v) {
                            addBatchToPs(host, k, insertPs, data);
                            addBatchToPs(host, k, updatePs, data);
                        }
                    } else if (v instanceof SetMultimap) {
                        ((SetMultimap<String, String>) v).forEach((vk, vv) -> {
                            LinkedList<String> dataMap = new LinkedList<>();
                            dataMap.add(vk);
                            dataMap.add(vv);
                            addBatchToPs(host, k, insertPs, dataMap);
                            addBatchToPs(host, k, updatePs, dataMap);
                        });
                    }

                    insertPs.executeBatch();
                    updatePs.executeBatch();
                } catch (Exception ignored) {
                }
            });
            connection.commit();
        } catch (Exception e) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                }
            }
        } finally {
            if (connection != null) {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        }
    }

    private PreparedStatement generateInsertSql(String tableName) throws SQLException {
        String insertParams;
        if (tableName.contains("All")) {
            insertParams = "(name) VALUES (?)";
        } else if (tableName.equals("Value")) {
            insertParams = "(name, value, host) VALUES (?, ?, ?)";
        } else {
            insertParams = "(name, host) VALUES (?, ?)";
        }

        return connection.prepareStatement("INSERT OR IGNORE INTO `" + tableName + "` " + insertParams);
    }

    private PreparedStatement generateUpdateSql(String tableName) throws SQLException {
        String params;
        if (tableName.contains("All")) {
            params = "(name = ?)";
        } else if (tableName.equals("Value")) {
            params = "(name = ? and value = ? and host = ?)";
        } else {
            params = "(name = ? and host = ?)";
        }

        return connection.prepareStatement("UPDATE `" + tableName + "` SET count = count + 1 WHERE " + params);
    }

    public void addBatchToPs(String host, String tableName, PreparedStatement ps, Object dataObj) {
        try {
            boolean isHostBlank = host.isBlank();

            if (tableName.equals("Value") && !isHostBlank) {
                LinkedList<String> list = (LinkedList<String>) dataObj;
                dataObj = new Object[]{list.get(0), list.get(1), host};
            } else if (tableName.contains("All")) {
                dataObj = new Object[]{dataObj};
            } else if (!isHostBlank) {
                dataObj = new Object[]{dataObj, host};
            }

            prepareStatement(dataObj, ps);
        } catch (Exception e) {
            api.logging().logToError(e);
        }
    }

    private void prepareStatement(Object dataObj, PreparedStatement ps) {
        try {
            if (dataObj instanceof Object[] data) {
                for (int i = 0; i < data.length; i++) {
                    ps.setString(i + 1, (String) data[i]);
                }
            } else {
                String data = (String) dataObj;
                if (!data.isBlank()) {
                    ps.setString(1, (String) dataObj);
                }
            }
            ps.addBatch();
        } catch (Exception ignored) {

        }
    }

    public Connection getConnection() {
        return this.connection;
    }

}
