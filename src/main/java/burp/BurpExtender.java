package burp;

import burp.db.ProcessDB;
import burp.json.ProcessJson;
import burp.ui.MainUI;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Component;
import java.io.PrintWriter;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * @author EvilChen
 */

public class BurpExtender implements IBurpExtender, IHttpListener, ITab {

    private final MainUI main = new MainUI();
    private static PrintWriter stdout;
    private IBurpExtenderCallbacks callbacks;
    private static IExtensionHelpers helpers;

    @Override
    public void registerExtenderCallbacks(IBurpExtenderCallbacks callbacks) {
        this.callbacks = callbacks;
        BurpExtender.helpers = callbacks.getHelpers();
        String version = "0.1";
        callbacks.setExtensionName(String.format("CaA (%s) - Collector and Analyzer", version));

        // 定义输出
        stdout = new PrintWriter(callbacks.getStdout(), true);
        stdout.println("@Core Author: EvilChen");
        stdout.println("@UI Author: 0chencc");
        stdout.println("@Github: https://github.com/gh0stkey/CaA");

        // UI
        SwingUtilities.invokeLater(this::initialize);
        callbacks.registerHttpListener(BurpExtender.this);
    }

    private void initialize() {
        callbacks.customizeUiComponent(main);
        callbacks.addSuiteTab(BurpExtender.this);
    }

    @Override
    public String getTabCaption() {
        return "CaA";
    }

    @Override
    public Component getUiComponent() {
        return main;
    }

    /*
     * 使用processHttpMessage来提取各个参数
     */
    @Override
    public void processHttpMessage(int toolFlag, boolean messageIsRequest,
            IHttpRequestResponse messageInfo) {
        // 判断数据库连接状态
        if (Config.isConnect) {
            // 收集信息仅作用于Proxy模块
            if (toolFlag == callbacks.TOOL_PROXY) {
                IHttpService iHttpService = null;
                try {
                    iHttpService = messageInfo.getHttpService();
                } catch (Exception ignored) {
                }

                // Func: 获取请求主机信息
                assert iHttpService != null;
                String host = iHttpService.getHost();

                // 分别处理请求和响应报文
                if (messageIsRequest) {

                    IRequestInfo analyzeRequest = helpers.analyzeRequest(messageInfo);
                    // Func: 获取路径和文件
                    String fullUri = analyzeRequest.getUrl().getPath(); // 获取uri
                    int lastIndex = fullUri.lastIndexOf("/"); // 获取最后一个/的索引
                    String fullPath = fullUri.substring(0, lastIndex + 1);
                    // 获取完整路径
                    ProcessDB.insertData(4, host, fullPath);

                    // 如果"."符号的索引存在则表示这是一个文件，如果不是则表示是一个路由/接口
                    // 这里可能会存在一些错误收集的情况，例如出现这种接口/xxx/Query.Info也会收录到文件列表中，不过由于在实际场景中很少遇到，暂时不考虑解决该问题
                    String fileName = fullUri.substring(lastIndex);

                    int isFile = fileName.indexOf("."); // 在字符串中寻找"."符号的索引
                    if (isFile > 0) {
                        // 写入到文件表
                        ProcessDB.insertData(2, host, fileName);
                    } else {
                        // 写入到端点表
                        ProcessDB.insertData(1, host, fileName);
                    }

                    // Func: 获取请求参数，包含URL参数、请求主体参数、Cookie参数、XML参数、Multipart参数、JSON Key
                    List<IParameter> paramList = analyzeRequest.getParameters();
                    // 遍历参数名
                    for (IParameter param : paramList) {
                        String key = param.getName(); // 获取参数的名称
                        String value = param.getValue(); // 获取参数的值
                        ProcessDB.insertData(0, host, key);
                        // 判断参数值是否为空
                        if (!value.isEmpty()) {
                            // 尝试以JSON格式解析参数值
                            try {
                                JsonObject obj = JsonParser.parseString(helpers.urlDecode(value))
                                        .getAsJsonObject();
                                // 遍历JSON Keys
                                ProcessJson pj = new ProcessJson();
                                pj.foreachJsonKey(obj);
                                for (String jKey : pj.jsonKeys) {
                                    ProcessDB.insertData(0, host, jKey);
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    }
                } else {
                    IResponseInfo analyzedResponse = helpers.analyzeResponse(
                            messageInfo.getResponse());
                    // 获取响应主体
                    String responseMessage = new String(messageInfo.getResponse());
                    int bodyOffset = analyzedResponse.getBodyOffset();
                    String responseBody = responseMessage.substring(bodyOffset);
                    // 尝试以JSON格式解析参数值
                    try {
                        JsonObject obj = JsonParser.parseString(responseBody).getAsJsonObject();
                        // 遍历JSON Keys
                        ProcessJson pj = new ProcessJson();
                        pj.foreachJsonKey(obj);
                        for (String jKey : pj.jsonKeys) {
                            ProcessDB.insertData(0, host, jKey);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }

    }
}
