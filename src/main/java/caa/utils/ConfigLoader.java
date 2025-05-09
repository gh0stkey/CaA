package caa.utils;

import burp.api.montoya.MontoyaApi;
import caa.Config;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

public class ConfigLoader {
    private final MontoyaApi api;
    private final String configFilePath;
    private final String dbFilePath;

    public ConfigLoader(MontoyaApi api) {
        this.api = api;

        String configPath = determineConfigPath();
        this.configFilePath = String.format("%s/%s", configPath, "Config.json");
        this.dbFilePath = String.format("%s/Data/%s", configPath, "CaA.db");

        // 构造函数，初始化配置
        File CaAConfigPathFile = new File(configPath);
        if (!(CaAConfigPathFile.exists() && CaAConfigPathFile.isDirectory())) {
            CaAConfigPathFile.mkdirs();
        }

        File configFilePath = new File(this.configFilePath);
        if (!(configFilePath.exists() && configFilePath.isFile())) {
            initConfig();
        }
    }

    private static boolean isValidConfigPath(String configPath) {
        File configPathFile = new File(configPath);
        return configPathFile.exists() && configPathFile.isDirectory();
    }

    public String getConfigFilePath() {
        return configFilePath;
    }

    public String getDbFilePath() {
        return dbFilePath;
    }

    private String determineConfigPath() {
        // 优先级1：用户根目录
        String userConfigPath = String.format("%s/.config/CaA", System.getProperty("user.home"));
        if (isValidConfigPath(userConfigPath)) {
            return userConfigPath;
        }

        // 优先级2：Jar包所在目录
        String jarPath = api.extension().filename();
        String jarDirectory = new File(jarPath).getParent();
        String jarConfigPath = String.format("%s/.config/CaA", jarDirectory);
        if (isValidConfigPath(jarConfigPath)) {
            return jarConfigPath;
        }

        return userConfigPath;
    }

    public void initConfig() {
        setExcludeSuffix(getExcludeSuffix());
        setExcludeStatus(getExcludeStatus());
        setBlockHost(getBlockHost());
        setScope(getScope());
    }

    // 获取规则配置
    public String getBlockHost() {
        return getValueFromConfig("BlockHost", Config.host);
    }

    public void setBlockHost(String blockHost) {
        setValueToConfig("BlockHost", blockHost);
    }

    public String getExcludeSuffix() {
        return getValueFromConfig("ExcludeSuffix", Config.suffix);
    }

    public void setExcludeSuffix(String excludeSuffix) {
        setValueToConfig("ExcludeSuffix", excludeSuffix);
    }

    public String getExcludeStatus() {
        return getValueFromConfig("ExcludeStatus", Config.status);
    }

    public void setExcludeStatus(String status) {
        setValueToConfig("ExcludeStatus", status);
    }

    public String getScope() {
        return getValueFromConfig("CaAScope", Config.scopeOptions);
    }

    public void setScope(String scope) {
        setValueToConfig("CaAScope", scope);
    }

    private String getValueFromConfig(String name, String value) {
        Map<String, String> configContent = loadCurrentConfig();
        if (configContent.containsKey(name)) {
            return configContent.get(name);
        }
        return value;
    }

    private void setValueToConfig(String name, String value) {
        Map<String, String> configContent = loadCurrentConfig();
        configContent.put(name, value);

        // 写入文件
        Gson gson = new Gson();
        String configString = gson.toJson(configContent);
        try (FileWriter fileWriter = new FileWriter(configFilePath)) {
            fileWriter.write(configString);
        } catch (Exception ignored) {
        }
    }

    private Map<String, String> loadCurrentConfig() {
        Path filePath = Paths.get(configFilePath);
        if (!Files.exists(filePath)) {
            return new LinkedHashMap<>(); // 返回空的Map，表示没有当前配置
        }

        try {
            Gson gson = new Gson();
            Type type = new com.google.gson.reflect.TypeToken<Map<String, Object>>() {
            }.getType();
            return gson.fromJson(new String(Files.readAllBytes(filePath)), type);
        } catch (Exception e) {
            api.logging().logToOutput(e.toString());
        }

        return new LinkedHashMap<>();
    }
}
