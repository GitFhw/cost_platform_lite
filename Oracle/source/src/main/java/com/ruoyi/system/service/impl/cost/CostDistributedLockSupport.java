package com.ruoyi.system.service.impl.cost;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 成本平台分布式锁支持。
 *
 * <p>当前先聚焦发布治理、运行缓存刷新和正式核算提交三类高并发入口，
 * 统一约束锁键格式、占锁时长和释放时机，避免后续逐条链路各自拼接 Redis key。</p>
 */
@Component
public class CostDistributedLockSupport {
    private static final String LOCK_PREFIX = "cost:lock:";
    private static final long SCENE_VERSIONING_LOCK_SECONDS = 60L;
    private static final long RUNTIME_CACHE_LOCK_SECONDS = 45L;
    private static final long TASK_SUBMIT_LOCK_SECONDS = 120L;
    private static final long TASK_DISPATCH_LOCK_SECONDS = 180L;
    private static final long TASK_DISPATCH_COORDINATOR_LOCK_SECONDS = 60L;

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = buildReleaseScript();

    @Autowired(required = false)
    private RedisTemplate<Object, Object> redisTemplate;

    private final Map<String, LocalLock> localLocks = new ConcurrentHashMap<>();

    private static String normalizePart(Object value) {
        String text = StringUtils.trimToEmpty(value == null ? "" : String.valueOf(value));
        return StringUtils.isBlank(text) ? "NONE" : text.replace(":", "_");
    }

    private static DefaultRedisScript<Long> buildReleaseScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptText(
                "if redis.call('get', KEYS[1]) == ARGV[1] then "
                        + "return redis.call('del', KEYS[1]) "
                        + "else return 0 end");
        return script;
    }

    public String buildSceneVersioningLockKey(Long sceneId) {
        return LOCK_PREFIX + "scene-versioning:" + normalizePart(sceneId);
    }

    public String buildRuntimeCacheLockKey(Long sceneId, Long versionId) {
        if (versionId != null) {
            return LOCK_PREFIX + "runtime-cache:version:" + versionId;
        }
        if (sceneId != null) {
            return LOCK_PREFIX + "runtime-cache:scene:" + sceneId;
        }
        return LOCK_PREFIX + "runtime-cache:all";
    }

    public String buildTaskSubmitLockKey(Long sceneId, Long versionId, String billMonth, String taskType,
                                         String requestNo, String sourceBatchNo) {
        String idempotentKey = StringUtils.defaultIfBlank(requestNo, sourceBatchNo);
        if (StringUtils.isBlank(idempotentKey)) {
            idempotentKey = "DEFAULT";
        }
        return LOCK_PREFIX + "task-submit:"
                + normalizePart(sceneId) + ":"
                + normalizePart(versionId) + ":"
                + normalizePart(StringUtils.trimToEmpty(billMonth)) + ":"
                + normalizePart(StringUtils.trimToEmpty(taskType)) + ":"
                + normalizePart(idempotentKey);
    }

    public String buildTaskDispatchLockKey(Long taskId) {
        return LOCK_PREFIX + "task-dispatch:" + normalizePart(taskId);
    }

    public String buildTaskDispatchCoordinatorLockKey() {
        return LOCK_PREFIX + "task-dispatch-coordinator";
    }

    public <T> T executeSceneVersioningLock(Long sceneId, String busyMessage, Supplier<T> supplier) {
        return executeWithLock(buildSceneVersioningLockKey(sceneId), busyMessage, SCENE_VERSIONING_LOCK_SECONDS, supplier);
    }

    public <T> T executeRuntimeCacheLock(Long sceneId, Long versionId, String busyMessage, Supplier<T> supplier) {
        return executeWithLock(buildRuntimeCacheLockKey(sceneId, versionId), busyMessage, RUNTIME_CACHE_LOCK_SECONDS, supplier);
    }

    public <T> T executeTaskSubmitLock(Long sceneId, Long versionId, String billMonth, String taskType,
                                       String requestNo, String sourceBatchNo, String busyMessage, Supplier<T> supplier) {
        return executeWithLock(buildTaskSubmitLockKey(sceneId, versionId, billMonth, taskType, requestNo, sourceBatchNo),
                busyMessage, TASK_SUBMIT_LOCK_SECONDS, supplier);
    }

    public boolean executeTaskDispatchLockOrSkip(Long taskId, Runnable runnable) {
        Boolean executed = executeWithOptionalLock(buildTaskDispatchLockKey(taskId), TASK_DISPATCH_LOCK_SECONDS, () ->
        {
            runnable.run();
            return Boolean.TRUE;
        });
        return Boolean.TRUE.equals(executed);
    }

    public boolean executeTaskDispatchCoordinatorLockOrSkip(Runnable runnable) {
        Boolean executed = executeWithOptionalLock(buildTaskDispatchCoordinatorLockKey(),
                TASK_DISPATCH_COORDINATOR_LOCK_SECONDS, () ->
                {
                    runnable.run();
                    return Boolean.TRUE;
                });
        return Boolean.TRUE.equals(executed);
    }

    public <T> T executeWithLock(String lockKey, String busyMessage, long leaseSeconds, Supplier<T> supplier) {
        T result = executeWithOptionalLock(lockKey, leaseSeconds, supplier);
        if (result == null) {
            throw new ServiceException(StringUtils.defaultIfBlank(busyMessage, "当前操作正在处理中，请稍后重试"));
        }
        return result;
    }

    private <T> T executeWithOptionalLock(String lockKey, long leaseSeconds, Supplier<T> supplier) {
        String token = UUID.randomUUID().toString();
        if (redisTemplate != null) {
            try {
                Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, token, leaseSeconds, TimeUnit.SECONDS);
                if (!Boolean.TRUE.equals(locked)) {
                    return null;
                }
                return executeWithRedisLock(lockKey, token, supplier);
            } catch (RuntimeException ignored) {
                // 轻量版没有可用 Redis 时使用进程内锁，保证单实例可直接运行。
            }
        }

        long expireAt = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(leaseSeconds);
        LocalLock previous = localLocks.putIfAbsent(lockKey, new LocalLock(token, expireAt));
        if (previous != null && previous.expireAt > System.currentTimeMillis()) {
            return null;
        }
        if (previous != null) {
            localLocks.replace(lockKey, previous, new LocalLock(token, expireAt));
        }
        try {
            return supplier.get();
        } finally {
            localLocks.remove(lockKey, localLocks.get(lockKey));
        }
    }

    private <T> T executeWithRedisLock(String lockKey, String token, Supplier<T> supplier) {
        boolean releaseAfterTransaction = false;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            try {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        releaseQuietly(lockKey, token);
                    }
                });
                releaseAfterTransaction = true;
            } catch (RuntimeException ex) {
                releaseQuietly(lockKey, token);
                throw ex;
            }
        }
        try {
            return supplier.get();
        } finally {
            if (!releaseAfterTransaction) {
                releaseQuietly(lockKey, token);
            }
        }
    }

    private void releaseQuietly(String lockKey, String token) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.execute(RELEASE_SCRIPT, Collections.singletonList(lockKey), token);
        } catch (Exception ignored) {
            // 锁释放失败不应覆盖主业务异常，依赖短租约自动兜底。
        }
    }

    private static final class LocalLock {
        private final String token;
        private final long expireAt;

        private LocalLock(String token, long expireAt) {
            this.token = token;
            this.expireAt = expireAt;
        }
    }
}
