package caa;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.logging.Logging;
import caa.component.CaAMain;
import caa.component.member.taskboard.MessageTableModel;
import caa.instances.Collector;
import caa.instances.editor.RequestEditor;
import caa.instances.editor.ResponseEditor;
import caa.instances.Database;

import java.io.File;
import java.sql.Connection;

public class CaA implements BurpExtension {
    @Override
    public void initialize(MontoyaApi api) {
        // 设置扩展名称
        String version = "1.0";
        Database db = null;
        MessageTableModel messageTableModel = new MessageTableModel(api);
        api.extension().setName(String.format("CaA (%s) - Collector and Analyzer", version));

        // 加载扩展后输出的项目信息
        Logging logging = api.logging();
        logging.logToOutput("[ HACK THE WORLD - TO DO IT ]");
        logging.logToOutput("[#] Author: EvilChen(Zfinfo) && 0chencc");
        logging.logToOutput("[#] Github: https://github.com/gh0stkey/CaA");

        // 数据库
        String jarPath = api.extension().filename();
        String jarDirectory = new File(jarPath).getParent();
        String jarConfigPath = String.format("%s/Data", jarDirectory);
        File jarConfigPathFile = new File(jarConfigPath);
        if (!(jarConfigPathFile.exists() && jarConfigPathFile.isDirectory())) {
            if (!jarConfigPathFile.mkdirs()) {
                api.logging().logToError("[Error] Failed to create the CaA database directory, please check!");
            }
        }

        String dbFileName = String.format("%s/CaA.db", jarConfigPath);
        db = new Database(api, dbFileName);
        Connection con = db.getConnection();

        if (con != null) {
            api.logging().logToOutput("[Info] CaA database successfully connected.");

            // 注册扫描器（用于收集数据）
            api.scanner().registerScanCheck(new Collector(api, db));

            // 注册消息编辑框（用于展示数据）
            api.userInterface().registerHttpRequestEditorProvider(new RequestEditor(api, db, messageTableModel));
            api.userInterface().registerHttpResponseEditorProvider(new ResponseEditor(api, db, messageTableModel));

            // 注册Tab页（用于查询数据）
            api.userInterface().registerSuiteTab("CaA", new CaAMain(api, db, messageTableModel));
        } else {
            api.logging().logToOutput("[Error] Failed to connect to the CaA database!");
            api.extension().unload();
        }
    }
}
