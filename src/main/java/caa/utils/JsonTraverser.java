package caa.utils;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.*;

public class JsonTraverser {
    private final Set<String> jsonKeys;
    private final SetMultimap<String, String> jsonKeyValues;

    public JsonTraverser() {
        this.jsonKeys = new LinkedHashSet<>();
        this.jsonKeyValues = LinkedHashMultimap.create();
    }

    public void foreachJsonKey(JsonElement e) {
        Deque<JsonElement> stack = new ArrayDeque<>();
        stack.push(e);

        while (!stack.isEmpty()) {
            JsonElement element = stack.pop();

            if (element.isJsonArray()) {
                for (JsonElement ae : element.getAsJsonArray()) {
                    stack.push(ae);
                }
            } else if (element.isJsonObject()) {
                JsonObject obj = element.getAsJsonObject();
                for (Map.Entry<String, JsonElement> en : obj.entrySet()) {
                    String key = en.getKey();
                    if (!(key.contains("/") || key.contains("@"))) {
                        jsonKeys.add(key);
                    }
                    processJsonValue(key, en.getValue(), stack);
                }
            }
        }
    }

    private void processJsonValue(String key, JsonElement value, Deque<JsonElement> stack) {
        if (value.isJsonObject() || value.isJsonArray()) {
            stack.push(value);
            if (value.isJsonArray()) {
                for (JsonElement arrayElement : value.getAsJsonArray()) {
                    if (!(arrayElement.isJsonObject() || arrayElement.isJsonArray())) {
                        jsonKeyValues.put(key, arrayElement.getAsString());
                    }
                }
            }
        } else if (!value.isJsonNull() && !containsJsonArrayOrObject(value)) {
            String stringValue = value.getAsString();
            Collection<String> jsonValue = jsonKeyValues.get(key);
            if (!stringValue.isEmpty() && !jsonValue.contains(stringValue)) {
                jsonKeyValues.put(key, stringValue);
            }
        }
    }

    private boolean containsJsonArrayOrObject(JsonElement element) {
        if (element.isJsonArray()) {
            for (JsonElement e : element.getAsJsonArray()) {
                if (e.isJsonArray() || e.isJsonObject()) {
                    return true;
                }
            }
        }
        return element.isJsonObject();
    }

    public Set<String> getJsonKeys() {
        return jsonKeys;
    }

    public SetMultimap<String, String> getJsonKeyValues() {
        return jsonKeyValues;
    }
}