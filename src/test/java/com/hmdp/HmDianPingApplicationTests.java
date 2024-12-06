package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
public class HmDianPingApplicationTests {
    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private CacheClient cacheClient;

    /**
     * 等同于redis预热, 将可能的热Key先存入Redis中
     */
    @Test
    public void saveShop2Redis() {
        shopService.saveShop2Redis(1L, 10L);
    }

    /**
     * 测试cacheClient工具类 逻辑过期进行缓存重建 以防缓存击穿
     */
    @Test
    public void testSaveShop() {
        Shop shop = shopService.getById(1L);

        cacheClient.setWithLogicExpire(CACHE_SHOP_KEY + 1L, shop, 10L, TimeUnit.SECONDS);
    }
}
