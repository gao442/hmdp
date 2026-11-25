package com.hmdp;

import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private IShopService shopService;

    @Resource
    private CacheClient cacheClient;

    /**
     * 缓存预热测试：将 1 号商铺写入 Redis 并设置 10 秒逻辑过期时间
     */
    @Test
    void testSaveShop() {
        shopService.saveShopToRedis(1L, 10L);
    }
}
