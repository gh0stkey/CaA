package caa.cache;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;

import java.util.*;

/**
 * @author EvilChen
 */

public class CachePool {

    private static final Map<String, Map<String, Object>> cache = new HashMap<>();

    public static void addToCache(String key, Map<String, Object> value) {
        try {
            Map<String, Object> cachMap = cache.get(key);
            // 合并Map
            Map<String, Object> mergedMap = new HashMap<>(cachMap);
            value.forEach((k, v) -> {
                Object existingValue = cachMap.get(k);
                if (existingValue == null) {
                    mergedMap.put(k, v);
                } else if ((existingValue instanceof List) && (v instanceof List)) {
                    List<String> mergedList = new ArrayList<>((List) existingValue);
                    mergedList.addAll((List) v);
                    mergedMap.put(k, new ArrayList(new HashSet(mergedList)));
                } else if ((existingValue instanceof Multimap) && (v instanceof Multimap)) {
                    SetMultimap create = LinkedHashMultimap.create((SetMultimap) existingValue);
                    create.putAll((SetMultimap) v);
                    mergedMap.put(k, create);
                }
            });

            // 删除原Map
            removeFromCache(key);
            // 存放新Map
            cache.put(key, mergedMap);
        } catch (Exception e) {
            cache.put(key, value);
        }
    }

    public static Map<String, Object> getFromCache(String key) {
        try {
            return cache.get(key);
        } catch (Exception e) {
            return null;
        }
    }

    public static void removeFromCache(String key) {
        cache.remove(key);
    }

}