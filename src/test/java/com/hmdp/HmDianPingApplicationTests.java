package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
public class HmDianPingApplicationTests {
    @Resource
    private ShopServiceImpl shopService;

    /**
     * 等同于redis预热, 将可能的热Key先存入Redis中
     */
    @Test
    public void saveShop2Redis() {
        shopService.saveShop2Redis(1L, 10L);
    }
}
