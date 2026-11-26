package com.hmdp;

import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private IShopService shopService;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private ExecutorService es = Executors.newFixedThreadPool(300);

    /**
     * 缓存预热测试：将 1 号商铺写入 Redis 并设置 10 秒逻辑过期时间
     */
    @Test
    void testSaveShop() {
        shopService.saveShopToRedis(1L, 10L);
    }

    @Test
    void testIdWorker() throws InterruptedException {
        // 1️⃣ 挂上倒计时牌：总数设为 300（对应下面要提交的 300 次任务）
        CountDownLatch latch = new CountDownLatch(300);

        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            // 2️⃣ 每个子任务跑完自己的 100 次循环后，把倒计时牌 -1
            latch.countDown();
        };

        long begin = System.currentTimeMillis();
        // 把任务提交 300 次给线程池并发跑
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }

        // 3️⃣ 主线程在这儿卡住死等！直到 300 个子任务全部调完 countDown() 把牌子减到 0，才往下走
        latch.await();

        long end = System.currentTimeMillis();
        System.out.println("30000 个 ID 全部生成完毕，总耗时 = " + (end - begin) + " ms");
    }
}
