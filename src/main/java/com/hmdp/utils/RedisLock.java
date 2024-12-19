package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import com.sun.org.apache.xpath.internal.operations.Bool;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

public class RedisLock implements ILock {

    private String name;
    private StringRedisTemplate stringRedisTemplate;
    private static final String keyPrefix = "lock:";
    private static final String IDPrefix = UUID.randomUUID().toString(true) + "-";

    public RedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        String threadId = IDPrefix + Thread.currentThread().getId();     //获取线程标识存入redis当中
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(
                keyPrefix + name,
                threadId,
                timeoutSec,                                 // 设置过期时间以防redis宕机永远无法释放锁的问题
                TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);                // 解决自动拆箱可能会导致的空指针问题
    }

    @Override
    public void unlock() {
        String threadId = IDPrefix + Thread.currentThread().getId();
        String id = stringRedisTemplate.opsForValue().get(keyPrefix + name);
        if (id.equals(threadId)) {
            stringRedisTemplate.delete(keyPrefix + name);   // 与redis中存的结果对比后释放锁
        }
    }
}
