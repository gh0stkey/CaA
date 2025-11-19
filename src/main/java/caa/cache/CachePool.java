package caa.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class CachePool {

    private static final Cache<String, Map<String, Object>> cache = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();

    public static void addToCache(String key, Map<String, Object> value) {
        if (key == null || value == null) {
            return;
        }

        try {
            Map<String, Object> cachMap = cache.getIfPresent(key);
            if (cachMap == null) {
                cache.put(key, value);
                return;
            }
            // 合并Map
            Map<String, Object> mergedMap = new HashMap<>(cachMap);
            value.forEach((k, v) -> {
                Object existingValue = cachMap.get(k);
                if (existingValue == null) {
                    mergedMap.put(k, v);
                } else if ((existingValue instanceof List) && (v instanceof List)) {
                    List<String> mergedList = new ArrayList<>((List) existingValue);
                    mergedList.addAll((List) v);
                    mergedMap.put(k, new ArrayList<>(new HashSet<>(mergedList)));
                } else if ((existingValue instanceof Multimap) && (v instanceof Multimap)) {
                    SetMultimap<String, String> create = LinkedHashMultimap.create((SetMultimap) existingValue);
                    create.putAll((SetMultimap) v);
                    mergedMap.put(k, create);
                }
            });

            // 存放新Map
            cache.put(key, mergedMap);
        } catch (Exception e) {
            // 发生异常时仍然尝试存储原始值
            System.err.println("Cache merge failed: " + e.getMessage());
            cache.put(key, value);
        }
    }

    public static Map<String, Object> getFromCache(String key) {
        try {
            return cache.getIfPresent(key);
        } catch (Exception e) {
            return null;
        }
    }

    public static void removeFromCache(String key) {
        cache.invalidate(key);
    }

}