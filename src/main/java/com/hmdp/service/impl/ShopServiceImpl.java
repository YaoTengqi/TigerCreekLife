package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    // 线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Override
    public Result queryById(Long id) {
//        Shop shop = queryPassThrough(id);
//        Shop shop = queryWithMutex(id);
        Shop shop = queryWithLogicExpire(id);
        if (shop == null) {
            return Result.fail("Shop is not exist");
        }
        return Result.ok(shop);
    }

    /**
     * 通过逻辑过期来解决缓存击穿问题
     *
     * @param id
     * @return 查询到的shop
     */
    public Shop queryWithLogicExpire(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1. 从redis查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断是否存在
        // 3. 缓存不存在则直接返回null
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }
        // 4.  缓存存在
        // 4.1 则需要先把json进行反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 5.  判断对象是否过期
        // 5.1 未过期则直接返回店铺信息
        if (expireTime.isAfter(LocalDateTime.now())) {
            return shop;
        }
        // 5.2 过期则需要进行缓存重建

        // 6.  缓存重建
        // 6.1 尝试获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        // 6.2 锁未被占用则开启独立线程, 实现缓存重建
        if (isLock) {
            // double check
//            shopJson = stringRedisTemplate.opsForValue().get(key);
//            if (StrUtil.isNotBlank(shopJson)) {
//                return JSONUtil.toBean(shopJson, Shop.class);
//            }
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                        try {
                            // 重建缓存
                            this.saveShop2Redis(id, 30L);
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
        return shop;
    }

    /**
     * 互斥锁解决缓存击穿
     *
     * @param id
     * @return 查询到的shop信息
     */
    public Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1. 从redis查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3. 缓存存在则直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        Shop shop = null;
        // 4. 缓存不存在则查询数据库
        // 4.1 尝试获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        try {
            boolean isLock = tryLock(lockKey);
            // 4.2 未获得锁
            if (!isLock) {
                Thread.sleep(10);   // 休眠10ms后重新请求锁
                return queryPassThrough(id);
            }
            shop = getById(id);
            // 模拟重建延时
            Thread.sleep(100);
            if (shop == null) {
                // 4.3 数据库中不存在则返回未找到
                return null;
            }
            // 4.4 数据库中存在则返回并写入redis缓存中
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unlock(lockKey);    // 释放锁
        }
        return shop;
    }

    /**
     * 缓存穿透
     *
     * @param id
     * @return 查询到的shop信息
     */
    public Shop queryPassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1. 从redis查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3. 缓存存在则直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        // 4. 缓存不存在则查询数据库
        Shop shop = getById(id);
        if (shop == null) {
            // 4.1 数据库中不存在则返回未找到
            return null;
        }
        // 4.2 数据库中存在则返回并写入redis缓存中
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
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

    // 将shop数据写入redis当中
    public void saveShop2Redis(Long id, Long expireSeconds) {
        // 1. 查询店铺数据
        Shop shop = getById(id);
        // 2. 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 3. 写入Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));  // 不用设置TTL 因为使用的是逻辑过期时间expireTime来管理
    }

    @Override
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("Shop is not exist!");
        }
        // 1. 更新数据库
        updateById(shop);
        // 2. 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
