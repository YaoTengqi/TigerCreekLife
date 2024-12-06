package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    public void setWithLogicExpire(String key, Object value, Long time, TimeUnit timeUnit) {
        // 设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        // 写入 Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));  // 设置逻辑过期时间
    }


    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> clazz, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) {
        String key = keyPrefix + id;
        // 1. 从redis查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断是否存在
        if (StrUtil.isNotBlank(json)) {
            // 3. 缓存存在则直接返回
            return JSONUtil.toBean(json, clazz);
        }
        if (json != null) {
            return null;
        }
        // 4. 缓存不存在则查询数据库
        R r = dbFallback.apply(id);
        // 4.1 数据库中不存在则返回未找到
        if (r == null) {
            // 将空值写入redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 4.2 数据库中存在则返回并写入redis缓存中
        this.set(key, JSONUtil.toJsonStr(r), time, timeUnit);
        return r;
    }

    // 线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);


    public <R, ID> R queryWithLogicExpire(String keyPrefix, ID id, Class<R> clazz, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) {
        String key = keyPrefix + id;
        // 1. 从redis查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断是否存在
        // 3. 缓存不存在则直接返回null
        if (StrUtil.isBlank(json)) {
            return null;
        }
        // 4.  缓存存在
        // 4.1 则需要先把json进行反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), clazz);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 5.  判断对象是否过期
        // 5.1 未过期则直接返回信息
        if (expireTime.isAfter(LocalDateTime.now())) {
            return r;
        }
        // 5.2 过期则需要进行缓存重建

        // 6.  缓存重建
        // 6.1 尝试获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        // 6.2 锁未被占用则开启独立线程, 实现缓存重建
        if (isLock) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                        try {
                            // 从数据库中进行查询
                            R dbResult = dbFallback.apply(id);
                            // 重建缓存
                            this.setWithLogicExpire(key, dbResult, time, timeUnit);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        } finally {
                            // 释放锁
                            unlock(lockKey);
                        }
                    }
            );
        }
        // 6.3 锁被占用则直接返回现有的redis信息(低一致性, 高可用性)
        return r;
    }

    // 获取锁
    private boolean tryLock(String lockKey) {
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(result);
    }

    // 释放锁
    private void unlock(String lockKey) {
        stringRedisTemplate.delete(lockKey);
    }

}
