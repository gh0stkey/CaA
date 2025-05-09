package caa;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.extension.ExtensionUnloadingHandler;
import burp.api.montoya.logging.Logging;
import caa.component.Main;
import caa.component.generator.Generator;
import caa.instances.Collector;
import caa.instances.Database;
import caa.instances.editor.ResponseEditor;
import caa.instances.payload.CaAPayloadGeneratorProvider;
import caa.utils.ConfigLoader;

import java.io.File;
import java.sql.Connection;

public class CaA implements BurpExtension {
    @Override
    public void initialize(MontoyaApi api) {
        // 设置扩展名称
        api.extension().setName("CaA - Collector and Analyzer");
        String version = "2.0";

        // 加载扩展后输出的项目信息
        Logging logging = api.logging();
        logging.logToOutput("[ HACK THE WORLD - TO DO IT ]");
        logging.logToOutput("[#] Author: EvilChen && 0chencc");
        logging.logToOutput("[#] Github: https://github.com/gh0stkey/CaA");
        logging.logToOutput("[#] Version: " + version);

        // 配置文件加载
        ConfigLoader configLoader = new ConfigLoader(api);

        // 数据库
        String dbFilePath = configLoader.getDbFilePath();

        File dbPath = new File(dbFilePath).getParentFile();
        if (!dbPath.exists() && !dbPath.mkdirs()) {
            api.logging().logToError("[Error] Failed to create the CaA database directory, please check!");
        }

        Database db = new Database(api, dbFilePath);
        Connection con = db.getConnection();

        if (con != null) {
            api.logging().logToOutput("[Info] CaA database successfully connected.");

            // 注册扫描器（用于收集数据）
            api.scanner().registerScanCheck(new Collector(api, db, configLoader));

            // 先绘制生成器UI便于后续调用
            Generator generator = new Generator(api, configLoader);

            // 注册消息编辑框（用于展示数据）
            api.userInterface().registerHttpResponseEditorProvider(new ResponseEditor(api, db, configLoader, generator));

            // 注册Tab页（用于查询数据）
            api.userInterface().registerSuiteTab("CaA", new Main(api, db, configLoader, generator));

            api.intruder().registerPayloadGeneratorProvider(new CaAPayloadGeneratorProvider());
        } else {
            api.logging().logToOutput("[Error] Failed to connect to the CaA database!");
            api.extension().unload();
        }

        api.extension().registerUnloadingHandler(new ExtensionUnloadingHandler() {
            @Override
            public void extensionUnloaded() {
                try {
                    if (con != null) {
                        con.close();
                    }
                } catch (Exception ignored) {
                }

            }
        });

    }
}
