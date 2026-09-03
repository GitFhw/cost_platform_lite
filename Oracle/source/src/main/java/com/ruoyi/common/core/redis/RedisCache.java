package com.ruoyi.common.core.redis;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * spring redis 工具类
 *
 * @author ruoyi
 **/
@SuppressWarnings(value = {"unchecked", "rawtypes"})
@Component
public class RedisCache {
    /**
     * 轻量宿主允许不部署 Redis；未配置时由进程内缓存承接短期数据。
     */
    @Autowired(required = false)
    public RedisTemplate redisTemplate;

    private final Map<String, LocalCacheEntry> localCache = new ConcurrentHashMap<>();

    /**
     * 缓存基本的对象，Integer、String、实体类等
     *
     * @param key   缓存的键值
     * @param value 缓存的值
     */
    public <T> void setCacheObject(final String key, final T value) {
        try {
            if (redisTemplate != null) {
                redisTemplate.opsForValue().set(key, value);
                return;
            }
        } catch (RuntimeException ignored) {
            // Redis 不可用时回退到进程内缓存。
        }
        putLocal(key, value, 0L);
    }

    /**
     * 缓存基本的对象，Integer、String、实体类等
     *
     * @param key      缓存的键值
     * @param value    缓存的值
     * @param timeout  时间
     * @param timeUnit 时间颗粒度
     */
    public <T> void setCacheObject(final String key, final T value, final Integer timeout, final TimeUnit timeUnit) {
        try {
            if (redisTemplate != null) {
                redisTemplate.opsForValue().set(key, value, timeout, timeUnit);
                return;
            }
        } catch (RuntimeException ignored) {
            // Redis 不可用时回退到进程内缓存。
        }
        long expireAt = timeout == null || timeUnit == null || timeout <= 0
                ? 0L : System.currentTimeMillis() + timeUnit.toMillis(timeout);
        putLocal(key, value, expireAt);
    }

    /**
     * 设置有效时间
     *
     * @param key     Redis键
     * @param timeout 超时时间
     *
     * @return true=设置成功；false=设置失败
     */
    public boolean expire(final String key, final long timeout) {
        return expire(key, timeout, TimeUnit.SECONDS);
    }

    /**
     * 设置有效时间
     *
     * @param key     Redis键
     * @param timeout 超时时间
     * @param unit    时间单位
     *
     * @return true=设置成功；false=设置失败
     */
    public boolean expire(final String key, final long timeout, final TimeUnit unit) {
        try {
            if (redisTemplate != null) {
                return redisTemplate.expire(key, timeout, unit);
            }
        } catch (RuntimeException ignored) {
            // Redis 不可用时继续使用进程内缓存。
        }
        LocalCacheEntry entry = localCache.get(key);
        if (entry == null || isExpired(key, entry)) {
            return false;
        }
        entry.expireAt = System.currentTimeMillis() + unit.toMillis(timeout);
        return true;
    }

    /**
     * 获取有效时间
     *
     * @param key Redis键
     *
     * @return 有效时间
     */
    public long getExpire(final String key) {
        try {
            if (redisTemplate != null) {
                Long expire = redisTemplate.getExpire(key);
                return expire == null ? -1L : expire;
            }
        } catch (RuntimeException ignored) {
            // Redis 不可用时继续使用进程内缓存。
        }
        LocalCacheEntry entry = localCache.get(key);
        if (entry == null || isExpired(key, entry)) {
            return -2L;
        }
        if (entry.expireAt <= 0L) {
            return -1L;
        }
        return Math.max(0L, entry.expireAt - System.currentTimeMillis()) / 1000L;
    }

    /**
     * 判断 key是否存在
     *
     * @param key 键
     *
     * @return true 存在 false不存在
     */
    public Boolean hasKey(String key) {
        try {
            if (redisTemplate != null) {
                return redisTemplate.hasKey(key);
            }
        } catch (RuntimeException ignored) {
            // Redis 不可用时继续使用进程内缓存。
        }
        LocalCacheEntry entry = localCache.get(key);
        return entry != null && !isExpired(key, entry);
    }

    /**
     * 获得缓存的基本对象。
     *
     * @param key 缓存键值
     *
     * @return 缓存键值对应的数据
     */
    public <T> T getCacheObject(final String key) {
        try {
            if (redisTemplate != null) {
                ValueOperations<String, T> operation = redisTemplate.opsForValue();
                return operation.get(key);
            }
        } catch (RuntimeException ignored) {
            // Redis 不可用时回退到进程内缓存。
        }
        LocalCacheEntry entry = localCache.get(key);
        return entry == null || isExpired(key, entry) ? null : (T) entry.value;
    }

    /**
     * 删除单个对象
     *
     * @param key
     */
    public boolean deleteObject(final String key) {
        try {
            if (redisTemplate != null) {
                Boolean deleted = redisTemplate.delete(key);
                return Boolean.TRUE.equals(deleted);
            }
        } catch (RuntimeException ignored) {
            // Redis 不可用时继续删除进程内缓存。
        }
        return localCache.remove(key) != null;
    }

    /**
     * 删除集合对象
     *
     * @param collection 多个对象
     *
     * @return
     */
    public boolean deleteObject(final Collection collection) {
        try {
            if (redisTemplate != null) {
                Long deleted = redisTemplate.delete(collection);
                return deleted != null && deleted > 0;
            }
        } catch (RuntimeException ignored) {
            // Redis 不可用时继续删除进程内缓存。
        }
        boolean deleted = false;
        if (collection != null) {
            for (Object key : collection) {
                deleted |= localCache.remove(String.valueOf(key)) != null;
            }
        }
        return deleted;
    }

    /**
     * 缓存List数据
     *
     * @param key      缓存的键值
     * @param dataList 待缓存的List数据
     *
     * @return 缓存的对象
     */
    public <T> long setCacheList(final String key, final List<T> dataList) {
        try {
            if (redisTemplate != null) {
                Long count = redisTemplate.opsForList().rightPushAll(key, dataList);
                return count == null ? 0 : count;
            }
        } catch (RuntimeException ignored) {
            // Redis 不可用时回退到进程内缓存。
        }
        putLocal(key, dataList == null ? new ArrayList<>() : new ArrayList<>(dataList), 0L);
        return dataList == null ? 0L : dataList.size();
    }

    /**
     * 获得缓存的list对象
     *
     * @param key 缓存的键值
     *
     * @return 缓存键值对应的数据
     */
    public <T> List<T> getCacheList(final String key) {
        try {
            if (redisTemplate != null) {
                return redisTemplate.opsForList().range(key, 0, -1);
            }
        } catch (RuntimeException ignored) {
            // Redis 不可用时回退到进程内缓存。
        }
        Object value = getCacheObject(key);
        return value instanceof List ? (List<T>) value : new ArrayList<>();
    }

    /**
     * 缓存Set
     *
     * @param key     缓存键值
     * @param dataSet 缓存的数据
     *
     * @return 缓存数据的对象
     */
    public <T> BoundSetOperations<String, T> setCacheSet(final String key, final Set<T> dataSet) {
        try {
            if (redisTemplate != null) {
                BoundSetOperations<String, T> setOperation = redisTemplate.boundSetOps(key);
                Iterator<T> it = dataSet.iterator();
                while (it.hasNext()) {
                    setOperation.add(it.next());
                }
                return setOperation;
            }
        } catch (RuntimeException ignored) {
            // Redis 不可用时回退到进程内缓存。
        }
        putLocal(key, dataSet == null ? new LinkedHashSet<>() : new LinkedHashSet<>(dataSet), 0L);
        return null;
    }

    /**
     * 获得缓存的set
     *
     * @param key
     *
     * @return
     */
    public <T> Set<T> getCacheSet(final String key) {
        try {
            if (redisTemplate != null) {
                return redisTemplate.opsForSet().members(key);
            }
        } catch (RuntimeException ignored) {
            // Redis 不可用时回退到进程内缓存。
        }
        Object value = getCacheObject(key);
        return value instanceof Set ? (Set<T>) value : new LinkedHashSet<>();
    }

    /**
     * 缓存Map
     *
     * @param key
     * @param dataMap
     */
    public <T> void setCacheMap(final String key, final Map<String, T> dataMap) {
        if (dataMap != null) {
            try {
                if (redisTemplate != null) {
                    redisTemplate.opsForHash().putAll(key, dataMap);
                    return;
                }
            } catch (RuntimeException ignored) {
                // Redis 不可用时回退到进程内缓存。
            }
            putLocal(key, new LinkedHashMap<>(dataMap), 0L);
        }
    }

    /**
     * 获得缓存的Map
     *
     * @param key
     *
     * @return
     */
    public <T> Map<String, T> getCacheMap(final String key) {
        try {
            if (redisTemplate != null) {
                return redisTemplate.opsForHash().entries(key);
            }
        } catch (RuntimeException ignored) {
            // Redis 不可用时回退到进程内缓存。
        }
        Object value = getCacheObject(key);
        return value instanceof Map ? (Map<String, T>) value : new LinkedHashMap<>();
    }

    /**
     * 往Hash中存入数据
     *
     * @param key   Redis键
     * @param hKey  Hash键
     * @param value 值
     */
    public <T> void setCacheMapValue(final String key, final String hKey, final T value) {
        try {
            if (redisTemplate != null) {
                redisTemplate.opsForHash().put(key, hKey, value);
                return;
            }
        } catch (RuntimeException ignored) {
            // Redis 不可用时回退到进程内缓存。
        }
        Map<String, Object> values = getCacheMap(key);
        values.put(hKey, value);
        putLocal(key, values, 0L);
    }

    /**
     * 获取Hash中的数据
     *
     * @param key  Redis键
     * @param hKey Hash键
     *
     * @return Hash中的对象
     */
    public <T> T getCacheMapValue(final String key, final String hKey) {
        try {
            if (redisTemplate != null) {
                HashOperations<String, String, T> opsForHash = redisTemplate.opsForHash();
                return opsForHash.get(key, hKey);
            }
        } catch (RuntimeException ignored) {
            // Redis 不可用时回退到进程内缓存。
        }
        Map<String, T> values = getCacheMap(key);
        return values.get(hKey);
    }

    /**
     * 获取多个Hash中的数据
     *
     * @param key   Redis键
     * @param hKeys Hash键集合
     *
     * @return Hash对象集合
     */
    public <T> List<T> getMultiCacheMapValue(final String key, final Collection<Object> hKeys) {
        try {
            if (redisTemplate != null) {
                return redisTemplate.opsForHash().multiGet(key, hKeys);
            }
        } catch (RuntimeException ignored) {
            // Redis 不可用时回退到进程内缓存。
        }
        Map<String, T> values = getCacheMap(key);
        List<T> result = new ArrayList<>();
        if (hKeys != null) {
            for (Object hKey : hKeys) {
                result.add(values.get(String.valueOf(hKey)));
            }
        }
        return result;
    }

    /**
     * 删除Hash中的某条数据
     *
     * @param key  Redis键
     * @param hKey Hash键
     *
     * @return 是否成功
     */
    public boolean deleteCacheMapValue(final String key, final String hKey) {
        try {
            if (redisTemplate != null) {
                Long deleted = redisTemplate.opsForHash().delete(key, hKey);
                return deleted != null && deleted > 0;
            }
        } catch (RuntimeException ignored) {
            // Redis 不可用时回退到进程内缓存。
        }
        Map<String, Object> values = getCacheMap(key);
        boolean deleted = values.remove(hKey) != null;
        putLocal(key, values, 0L);
        return deleted;
    }

    /**
     * 获得缓存的基本对象列表
     *
     * @param pattern 字符串前缀
     *
     * @return 对象列表
     */
    public Collection<String> keys(final String pattern) {
        try {
            if (redisTemplate != null) {
                return redisTemplate.keys(pattern);
            }
        } catch (RuntimeException ignored) {
            // Redis 不可用时回退到进程内缓存。
        }
        String prefix = pattern == null ? "" : pattern.replace("*", "");
        List<String> keys = new ArrayList<>();
        for (String key : new ArrayList<>(localCache.keySet())) {
            LocalCacheEntry entry = localCache.get(key);
            if (entry != null && !isExpired(key, entry) && key.startsWith(prefix)) {
                keys.add(key);
            }
        }
        return keys;
    }

    private void putLocal(String key, Object value, long expireAt) {
        if (key != null) {
            localCache.put(key, new LocalCacheEntry(value, expireAt));
        }
    }

    private boolean isExpired(String key, LocalCacheEntry entry) {
        if (entry.expireAt <= 0L || entry.expireAt > System.currentTimeMillis()) {
            return false;
        }
        localCache.remove(key, entry);
        return true;
    }

    private static final class LocalCacheEntry {
        private final Object value;
        private volatile long expireAt;

        private LocalCacheEntry(Object value, long expireAt) {
            this.value = value;
            this.expireAt = expireAt;
        }
    }
}
