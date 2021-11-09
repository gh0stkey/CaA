package burp.json;

import burp.Config;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author EvilChen
 */

public class ProcessJson {

    public List<String> jsonKeys = new ArrayList<>();

    /**
     * 遍历JSON的下键给到jsonKeys
     */
    public void foreachJsonKey(JsonElement e) {
        if (e.isJsonArray()) {
            JsonArray ja = e.getAsJsonArray();
            if (null != ja) {
                for (JsonElement ae : ja) {
                    foreachJsonKey(ae);
                }
            }
        }

        if (e.isJsonObject()) {
            Set<Map.Entry<String, JsonElement>> es = e.getAsJsonObject().entrySet();
            for (Map.Entry<String, JsonElement> en : es) {
                jsonKeys.add(en.getKey());
                foreachJsonKey(en.getValue());
            }
        }

    }

    /**
     * 解析配置文件中的JSON
     */
    public static Map<String, String> parseJson() {
        BufferedReader configReader = null;
        try {
            configReader = new BufferedReader(new FileReader(Config.CaAConfig));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        assert configReader != null;
        return new Gson().fromJson(
                configReader, new TypeToken<Map<String, String>>() {
                }.getType()
        );
    }

    /**
     * 写入JSON到配置文件
     */
    public static void writeJson(Map<String, String> map) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(Config.CaAConfig));
            writer.write(gson.toJson(map));
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
