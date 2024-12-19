package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import com.sun.org.apache.xpath.internal.operations.Bool;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class RedisLock implements ILock {

    private String name;
    private StringRedisTemplate stringRedisTemplate;
    private static final String keyPrefix = "lock:";
    private static final String IDPrefix = UUID.randomUUID().toString(true) + "-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    // 初始化加载lua脚本
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

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
        // 使用lua脚本保证释放锁操作的原子性
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(keyPrefix + name),
                IDPrefix + Thread.currentThread().getId()
        );
    }
}
