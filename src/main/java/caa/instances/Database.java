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
        Statement statement = null;
        try {
            Class.forName("org.sqlite.JDBC");
            this.connection = DriverManager.getConnection(String.format("jdbc:sqlite:%s", dbFileName));
            statement = this.connection.createStatement();

            // 开启 WAL 模式
            statement.executeUpdate("PRAGMA journal_mode=WAL;");

            // 修改分页大小
            statement.executeUpdate("PRAGMA page_size = 8192;");

            // 开启自动检查点模式
            statement.executeUpdate("PRAGMA wal_autocheckpoint = 2000;");

            // 关闭同步模式（提升写入性能）
            statement.executeUpdate("PRAGMA synchronous=NORMAL;");

            // 启用缓存（增加缓存大小）
            statement.executeUpdate("PRAGMA cache_size = 20000;");

            // 设置临时表存储在内存中
            statement.executeUpdate("PRAGMA temp_store = MEMORY;");

            // 禁用安全删除
            statement.executeUpdate("PRAGMA secure_delete = OFF;");

            // 禁用外键约束
            statement.executeUpdate("PRAGMA foreign_keys = OFF;");

            // 设置mmap大小以提升读取性能
            statement.executeUpdate("PRAGMA mmap_size = 268435456;"); // 256MB

            // 使用内存临时存储
            statement.executeUpdate("PRAGMA temp_store = MEMORY;");

            // 初始化数据表
            createTables();

            // 创建索引以提升查询性能
            createIndexes();

            // 优化数据库
            statement.executeUpdate("PRAGMA optimize;");
        } catch (Exception e) {
            api.logging().logToError("Failed to initialize database: " + e.getMessage());
        } finally {
            closeQuietly(statement);
        }
    }

    private static String getSql(String tableName, String limitSize, boolean isLikeQuery) {
        String sql;
        // 模糊查询
        if (isLikeQuery) {
            sql = "SELECT name%s,SUM(count) AS count FROM `" + tableName + "` %s GROUP BY name%s HAVING COUNT(*) > 0 ORDER BY count DESC";
        } else {
            sql = "SELECT name%s,count FROM `" + tableName + "` %s ORDER BY count DESC";
        }

        if (!limitSize.isBlank()) {
            sql += " LIMIT " + limitSize;
        }

        if (tableName.contains("All")) {
            sql = String.format(sql, "", "");
        } else if (tableName.equals("Value")) {
            if (isLikeQuery) {
                sql = String.format(sql, ",value", "WHERE host like ?", ",value");
            } else {
                sql = String.format(sql, ",value", "WHERE host = ?");
            }
        } else {
            if (isLikeQuery) {
                sql = String.format(sql, "", "WHERE host like ?", "");
            } else {
                sql = String.format(sql, "", "WHERE host = ?");
            }
        }
        return sql;
    }

    public Object selectData(String host, String tableName, String limitSize) {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            synchronized (connection) {
                if (connection.isClosed()) {
                    return null;
                }
            }

            boolean isLikeQuery = host.contains("*.");
            String sql = getSql(tableName, limitSize, isLikeQuery);

            synchronized (connection) {
                ps = connection.prepareStatement(sql);
                if (isLikeQuery) {
                    ps.setString(1, "%" + host.replace("*.", "."));
                } else if (!host.isEmpty()) {
                    ps.setString(1, host);
                }
                rs = ps.executeQuery();

                // 判断结果集是否为空
                if (!rs.isBeforeFirst()) {
                    return null;
                }

                // 处理结果集（在synchronized块外）
                if (tableName.equals("Value")) {
                    SetMultimap<String, String> multimap = LinkedHashMultimap.create();
                    while (rs.next()) {
                        String key = rs.getString(1);
                        String value = rs.getString(2);
                        int count = rs.getInt(3);
                        // 将 count 和 value 组合成一个字符串
                        String combinedValue = String.format("%d|%s", count, value);
                        multimap.put(key, combinedValue);
                    }
                    return multimap.isEmpty() ? null : multimap;
                } else {
                    Map<String, Integer> resultMap = new LinkedHashMap<>();
                    while (rs.next()) {
                        String columnValue = rs.getString(1);
                        int count = rs.getInt(2);
                        resultMap.put(columnValue, count);
                    }
                    return resultMap.isEmpty() ? null : resultMap;
                }
            }
        } catch (Exception e) {
            api.logging().logToError("Failed to select data: " + e.getMessage());
        } finally {
            closeQuietly(rs);
            closeQuietly(ps);
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
            Statement stmt = null;
            try {
                stmt = connection.createStatement();
                stmt.execute(String.format(sqlTemplate, name, fields, uniqueField));
            } catch (Exception e) {
                api.logging().logToError("Failed to create table " + name + ": " + e.getMessage());
            } finally {
                closeQuietly(stmt);
            }
        }
    }

    private void createIndexes() {
        Statement stmt = null;
        try {
            stmt = connection.createStatement();

            // 为每个表创建索引以提升查询性能
            for (String tableName : Config.CaATableName) {
                if (!tableName.contains("All")) {
                    // 为host字段创建索引
                    String hostIndexSql = String.format(
                            "CREATE INDEX IF NOT EXISTS idx_%s_host ON `%s`(host);",
                            tableName.toLowerCase().replace(" ", "_"), tableName
                    );
                    stmt.executeUpdate(hostIndexSql);

                    // 为name字段创建索引
                    String nameIndexSql = String.format(
                            "CREATE INDEX IF NOT EXISTS idx_%s_name ON `%s`(name);",
                            tableName.toLowerCase().replace(" ", "_"), tableName
                    );
                    stmt.executeUpdate(nameIndexSql);

                    // 为count字段创建降序索引（用于排序）
                    String countIndexSql = String.format(
                            "CREATE INDEX IF NOT EXISTS idx_%s_count ON `%s`(count DESC);",
                            tableName.toLowerCase().replace(" ", "_"), tableName
                    );
                    stmt.executeUpdate(countIndexSql);
                } else {
                    // All表只需要name和count索引
                    String nameIndexSql = String.format(
                            "CREATE INDEX IF NOT EXISTS idx_%s_name ON `%s`(name);",
                            tableName.toLowerCase().replace(" ", "_"), tableName
                    );
                    stmt.executeUpdate(nameIndexSql);

                    String countIndexSql = String.format(
                            "CREATE INDEX IF NOT EXISTS idx_%s_count ON `%s`(count DESC);",
                            tableName.toLowerCase().replace(" ", "_"), tableName
                    );
                    stmt.executeUpdate(countIndexSql);
                }
            }

            api.logging().logToOutput("[Info] Database indexes created successfully.");
        } catch (Exception e) {
            api.logging().logToError("Failed to create indexes: " + e.getMessage());
        } finally {
            closeQuietly(stmt);
        }
    }

    public List<String> getAllHosts(String tableName) {
        if (!Arrays.stream(Config.CaATableName).anyMatch(t -> t.equalsIgnoreCase(tableName))) {
            return new ArrayList<>();
        }

        Set<String> setHostList = new LinkedHashSet<>();
        Statement stmt = null;
        ResultSet rs = null;
        try {
            synchronized (connection) {
                if (connection.isClosed()) {
                    return new ArrayList<>();
                }
                stmt = connection.createStatement();
                // 使用DISTINCT去重，减少内存处理
                rs = stmt.executeQuery(String.format("SELECT DISTINCT host FROM `%s` WHERE host IS NOT NULL ORDER BY host", tableName));
            }


            while (rs.next()) {
                String host = rs.getString("host");
                if (host != null && !host.isEmpty()) {
                    setHostList.add(host);
                }
            }
        } catch (SQLException e) {
            api.logging().logToError("Failed to get all hosts: " + e.getMessage());
        } finally {
            closeQuietly(rs);
            closeQuietly(stmt);
        }
        return new ArrayList<>(setHostList);
    }

    public void insertData(String host, Map<String, Object> dataObj) {
        synchronized (connection) {
            try {
                connection.setAutoCommit(false);

                for (Map.Entry<String, Object> entry : dataObj.entrySet()) {
                    String k = entry.getKey();
                    Object v = entry.getValue();
                    PreparedStatement insertPs = null;
                    PreparedStatement updatePs = null;
                    try {
                        insertPs = generateInsertSql(k);
                        updatePs = generateUpdateSql(k);


                        if (v instanceof HashSet<?>) {
                            for (String data : (HashSet<String>) v) {
                                addBatchToPs(host, k, insertPs, data);
                                addBatchToPs(host, k, updatePs, data);
                            }
                        } else if (v instanceof SetMultimap) {
                            SetMultimap<String, String> multimap = (SetMultimap<String, String>) v;
                            for (Map.Entry<String, String> mvEntry : multimap.entries()) {
                                LinkedList<String> dataMap = new LinkedList<>();
                                dataMap.add(mvEntry.getKey());
                                dataMap.add(mvEntry.getValue());
                                addBatchToPs(host, k, insertPs, dataMap);
                                addBatchToPs(host, k, updatePs, dataMap);
                            }
                        }

                        insertPs.executeBatch();
                        updatePs.executeBatch();
                    } catch (Exception e) {
                        api.logging().logToError("Failed to insert data for table " + k + ": " + e.getMessage());
                    } finally {
                        closeQuietly(insertPs);
                        closeQuietly(updatePs);
                    }
                }
                connection.commit();
            } catch (Exception e) {
                api.logging().logToError("Failed to insert data: " + e.getMessage());
                if (connection != null) {
                    try {
                        connection.rollback();
                    } catch (SQLException ex) {
                        api.logging().logToError("Failed to rollback: " + ex.getMessage());
                    }
                }
            } finally {
                if (connection != null) {
                    try {
                        connection.setAutoCommit(true);
                    } catch (SQLException ex) {
                        api.logging().logToError("Failed to set auto commit: " + ex.getMessage());
                    }
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
        } catch (Exception e) {
            api.logging().logToError("Failed to prepare statement: " + e.getMessage());
        }
    }

    private void closeQuietly(PreparedStatement ps) {
        if (ps != null) {
            try {
                ps.close();
            } catch (SQLException e) {
                api.logging().logToError("Failed to close PreparedStatement: " + e.getMessage());
            }
        }
    }

    private void closeQuietly(Statement stmt) {
        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException e) {
                api.logging().logToError("Failed to close Statement: " + e.getMessage());
            }
        }
    }

    private void closeQuietly(ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                api.logging().logToError("Failed to close ResultSet: " + e.getMessage());
            }
        }
    }

    /**
     * 删除数据
     *
     * @param host      主机名（对于All表传空字符串）
     * @param tableName 表名
     * @param name      要删除的name值
     * @param value     要删除的value值（仅对Value表有效）
     * @return 是否删除成功
     */
    public boolean deleteData(String host, String tableName, String name, String value) {
        PreparedStatement ps = null;
        try {
            synchronized (connection) {
                if (connection.isClosed()) {
                    return false;
                }

                String sql;
                if (tableName.contains("All")) {
                    sql = "DELETE FROM `" + tableName + "` WHERE name = ?";
                } else if (tableName.equals("Value")) {
                    if (value != null) {
                        sql = "DELETE FROM `" + tableName + "` WHERE name = ? AND value = ? AND host = ?";
                    } else {
                        sql = "DELETE FROM `" + tableName + "` WHERE name = ? AND host = ?";
                    }
                } else {
                    sql = "DELETE FROM `" + tableName + "` WHERE name = ? AND host = ?";
                }

                ps = connection.prepareStatement(sql);
                ps.setString(1, name);

                if (tableName.contains("All")) {
                    // All表只需要name
                } else if (tableName.equals("Value")) {
                    if (value != null) {
                        ps.setString(2, value);
                        ps.setString(3, host);
                    } else {
                        ps.setString(2, host);
                    }
                } else {
                    ps.setString(2, host);
                }

                int affected = ps.executeUpdate();
                return affected > 0;
            }
        } catch (Exception e) {
            api.logging().logToError("Failed to delete data: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(ps);
        }
    }

    /**
     * 批量删除数据
     */
    public int batchDeleteData(String host, String tableName, List<Map<String, String>> dataList) {
        int deletedCount = 0;
        synchronized (connection) {
            try {
                connection.setAutoCommit(false);

                for (Map<String, String> data : dataList) {
                    String name = data.get("name");
                    String value = data.get("value");

                    if (deleteData(host, tableName, name, value)) {
                        deletedCount++;
                    }
                }

                connection.commit();
            } catch (Exception e) {
                api.logging().logToError("Failed to batch delete data: " + e.getMessage());
                try {
                    connection.rollback();
                } catch (SQLException ex) {
                    api.logging().logToError("Failed to rollback: " + ex.getMessage());
                }
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ex) {
                    api.logging().logToError("Failed to set auto commit: " + ex.getMessage());
                }
            }
        }
        return deletedCount;
    }

    public Connection getConnection() {
        return this.connection;
    }

}
