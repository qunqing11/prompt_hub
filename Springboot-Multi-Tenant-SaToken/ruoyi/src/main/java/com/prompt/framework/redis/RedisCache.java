package com.prompt.framework.redis;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.support.atomic.RedisAtomicLong;
import org.springframework.stereotype.Component;

/**
 * spring redis 工具类
 *
 * @author ruoyi
 **/
@SuppressWarnings(value = {"unchecked", "rawtypes"})
@Component
public class RedisCache {
    @Autowired
    public RedisTemplate redisTemplate;

    private static final ConcurrentHashMap<String, CacheEntry> LOCAL_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicLong> LOCAL_COUNTER = new ConcurrentHashMap<>();

    private static final class CacheEntry {
        private final Object value;
        private final long expireAtMillis;

        private CacheEntry(Object value, long expireAtMillis) {
            this.value = value;
            this.expireAtMillis = expireAtMillis;
        }
    }

    private static long expireAtMillis(Integer timeout, TimeUnit timeUnit) {
        if (timeout == null || timeUnit == null) {
            return 0L;
        }
        long durationMillis = timeUnit.toMillis(timeout.longValue());
        if (durationMillis <= 0L) {
            return 0L;
        }
        return System.currentTimeMillis() + durationMillis;
    }

    private static Object localGet(String key) {
        CacheEntry entry = LOCAL_CACHE.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expireAtMillis > 0L && System.currentTimeMillis() >= entry.expireAtMillis) {
            LOCAL_CACHE.remove(key);
            return null;
        }
        return entry.value;
    }

    /**
     * 缓存基本的对象，Integer、String、实体类等
     *
     * @param key   缓存的键值
     * @param value 缓存的值
     * @return 缓存的对象
     */
    public <T> ValueOperations<String, T> setCacheObject(String key, T value) {
        try {
            ValueOperations<String, T> operation = redisTemplate.opsForValue();
            operation.set(key, value);
            return operation;
        } catch (Exception ignored) {
            LOCAL_CACHE.put(key, new CacheEntry(value, 0L));
            return null;
        }
    }

    /**
     * 缓存基本的对象，Integer、String、实体类等
     *
     * @param key      缓存的键值
     * @param value    缓存的值
     * @param timeout  时间
     * @param timeUnit 时间颗粒度
     * @return 缓存的对象
     */
    public <T> ValueOperations<String, T> setCacheObject(String key, T value, Integer timeout, TimeUnit timeUnit) {
        try {
            ValueOperations<String, T> operation = redisTemplate.opsForValue();
            operation.set(key, value, timeout, timeUnit);
            return operation;
        } catch (Exception ignored) {
            LOCAL_CACHE.put(key, new CacheEntry(value, expireAtMillis(timeout, timeUnit)));
            return null;
        }
    }

    /**
     * 获得缓存的基本对象。
     *
     * @param key 缓存键值
     * @return 缓存键值对应的数据
     */
    public <T> T getCacheObject(String key) {
        try {
            ValueOperations<String, T> operation = redisTemplate.opsForValue();
            return operation.get(key);
        } catch (Exception ignored) {
            return (T) localGet(key);
        }
    }

    /**
     * 删除单个对象
     *
     * @param key
     */
    public void deleteObject(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception ignored) {
            LOCAL_CACHE.remove(key);
        }
    }

    /**
     * 删除集合对象
     *
     * @param collection
     */
    public void deleteObject(Collection collection) {
        try {
            redisTemplate.delete(collection);
        } catch (Exception ignored) {
            for (Object key : collection) {
                if (key != null) {
                    LOCAL_CACHE.remove(String.valueOf(key));
                }
            }
        }
    }

    /**
     * 缓存List数据
     *
     * @param key      缓存的键值
     * @param dataList 待缓存的List数据
     * @return 缓存的对象
     */
    public <T> ListOperations<String, T> setCacheList(String key, List<T> dataList) {
        try {
            ListOperations listOperation = redisTemplate.opsForList();
            if (null != dataList) {
                int size = dataList.size();
                for (int i = 0; i < size; i++) {
                    listOperation.leftPush(key, dataList.get(i));
                }
            }
            return listOperation;
        } catch (Exception ignored) {
            if (dataList == null) {
                LOCAL_CACHE.remove(key);
            } else {
                LOCAL_CACHE.put(key, new CacheEntry(new ArrayList<>(dataList), 0L));
            }
            return null;
        }
    }

    /**
     * 获得缓存的list对象
     *
     * @param key 缓存的键值
     * @return 缓存键值对应的数据
     */
    public <T> List<T> getCacheList(String key) {
        try {
            List<T> dataList = new ArrayList<T>();
            ListOperations<String, T> listOperation = redisTemplate.opsForList();
            Long size = listOperation.size(key);

            for (int i = 0; i < size; i++) {
                dataList.add(listOperation.index(key, i));
            }
            return dataList;
        } catch (Exception ignored) {
            Object v = localGet(key);
            if (v instanceof List) {
                return (List<T>) v;
            }
            return new ArrayList<>();
        }
    }

    /**
     * 缓存Set
     *
     * @param key     缓存键值
     * @param dataSet 缓存的数据
     * @return 缓存数据的对象
     */
    public <T> BoundSetOperations<String, T> setCacheSet(String key, Set<T> dataSet) {
        try {
            BoundSetOperations<String, T> setOperation = redisTemplate.boundSetOps(key);
            Iterator<T> it = dataSet.iterator();
            while (it.hasNext()) {
                setOperation.add(it.next());
            }
            return setOperation;
        } catch (Exception ignored) {
            if (dataSet == null) {
                LOCAL_CACHE.remove(key);
            } else {
                LOCAL_CACHE.put(key, new CacheEntry(new HashSet<>(dataSet), 0L));
            }
            return null;
        }
    }

    /**
     * 获得缓存的set
     *
     * @param key
     * @return
     */
    public <T> Set<T> getCacheSet(String key) {
        try {
            Set<T> dataSet = new HashSet<T>();
            BoundSetOperations<String, T> operation = redisTemplate.boundSetOps(key);
            dataSet = operation.members();
            return dataSet;
        } catch (Exception ignored) {
            Object v = localGet(key);
            if (v instanceof Set) {
                return (Set<T>) v;
            }
            return new HashSet<>();
        }
    }

    /**
     * 缓存Map
     *
     * @param key
     * @param dataMap
     * @return
     */
    public <T> HashOperations<String, String, T> setCacheMap(String key, Map<String, T> dataMap) {
        try {
            HashOperations hashOperations = redisTemplate.opsForHash();
            if (null != dataMap) {
                for (Map.Entry<String, T> entry : dataMap.entrySet()) {
                    hashOperations.put(key, entry.getKey(), entry.getValue());
                }
            }
            return hashOperations;
        } catch (Exception ignored) {
            if (dataMap == null) {
                LOCAL_CACHE.remove(key);
            } else {
                LOCAL_CACHE.put(key, new CacheEntry(new HashMap<>(dataMap), 0L));
            }
            return null;
        }
    }

    /**
     * 获得缓存的Map
     *
     * @param key
     * @return
     */
    public <T> Map<String, T> getCacheMap(String key) {
        try {
            Map<String, T> map = redisTemplate.opsForHash().entries(key);
            return map;
        } catch (Exception ignored) {
            Object v = localGet(key);
            if (v instanceof Map) {
                return (Map<String, T>) v;
            }
            return new HashMap<>();
        }
    }

    /**
     * 获得缓存的基本对象列表
     *
     * @param pattern 字符串前缀
     * @return 对象列表
     */
    public Collection<String> keys(String pattern) {
        try {
            return redisTemplate.keys(pattern);
        } catch (Exception ignored) {
            String regex = pattern.replace(".", "\\.").replace("*", ".*");
            List<String> keys = new ArrayList<>();
            for (String key : LOCAL_CACHE.keySet()) {
                if (key.matches(regex)) {
                    keys.add(key);
                }
            }
            return keys;
        }
    }

    /**
     * 获取自增值
     *
     * @param key
     * @return
     */
    public Long incrByKey(String key) {
        try {
            RedisAtomicLong entityIdCounter = new RedisAtomicLong(key, Objects.requireNonNull(redisTemplate.getConnectionFactory()));
            return entityIdCounter.getAndIncrement();
        } catch (Exception ignored) {
            AtomicLong counter = LOCAL_COUNTER.computeIfAbsent(key, k -> new AtomicLong(0L));
            return counter.getAndIncrement();
        }
    }

//    public Set<String> scan(String matchKey) {
//        Set<String> keys = (Set<String>) redisTemplate.execute((RedisCallback<Set<String>>) connection -> {
//            Set<String> keysTmp = new HashSet<>();
//            Cursor<byte[]> cursor = connection.scan(new ScanOptions.ScanOptionsBuilder().match("*" + matchKey + "*").count(1000).build());
//            while (cursor.hasNext()) {
//                keysTmp.add(new String(cursor.next()));
//            }
//            return keysTmp;
//        });
//
//        return keys;
//    }
}
