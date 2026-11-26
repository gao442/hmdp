package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryShopById(Long id) {
        //缓存穿透的解决方案
        //Shop shop = queryWithPassThrough(id);

        //互斥锁解决缓存击穿的方案
        Shop shop = cacheClient.queryWithMutex(id,
                RedisConstants.CACHE_SHOP_KEY,
                Shop.class,
                this::getById,
                60L,
                TimeUnit.SECONDS);

        //逻辑过期方案解决缓存击穿
        //Shop shop = queryWithLogicExpire(id);

//        Shop shop = cacheClient.<Shop, Long>queryWithLogicExpire(
//                id,
//                RedisConstants.CACHE_SHOP_KEY,
//                Shop.class,
//                60L,
//                TimeUnit.SECONDS,
//                this::getById
//        );
        /*
        逻辑过期不是用来查“全库冷热随机数据”的
        它是专门服务于【提前预热的超级热点数据】的

        上线前必须先“全量预热”
        Redis 物理永不过期
         */

        if (shop == null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }




    @Override
    @Transactional(rollbackFor = Exception.class)
    //事务保证原子性
    public Result updateShop(Shop shop) {
        Long shopId = shop.getId();
        if (shopId == null){
            return Result.fail("商铺id不能为空");
        }
        this.updateById(shop);

        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY +shopId);
        return Result.ok();
        /*
        先更新数据库，再删除 Redis 缓存，符合Cache Aside 策略
         */

    }

//
//
//
//    private Shop queryWithPassThrough(Long id) {
//        if (id == null) {
//            return null;
//        }
//        String key = RedisConstants.CACHE_SHOP_KEY + id;
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//
//        if (StrUtil.isNotBlank(shopJson)) {
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return shop;
//        }
//        if (shopJson != null) {
//            return null;
//        }
//
//        //在 ShopServiceImpl.java 内部：随时随地都能直接写 getById(id)
//        //因为它继承自父类 ServiceImpl<ShopMapper, Shop>
//        Shop shop = this.getById(id);
//        if (shop == null) {
//            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return null;
//        }
//        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL + RandomUtil.randomInt(5, 20), TimeUnit.MINUTES);
//
//        return shop;
//    }
//
//    private Shop queryWithMutex(Long id) {
//        if (id == null) {
//            return null;
//        }
//        String key = RedisConstants.CACHE_SHOP_KEY + id;
//
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        if (StrUtil.isNotBlank(shopJson)) {
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        if (shopJson != null) {
//            return null;
//        }
//
//        // 按商铺 id 拼接锁 Key，缩小锁粒度
//        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
//        boolean flag = this.tryLock(lockKey);
//        if (!flag) {
//            // 未抢到锁时在 try-finally 外部休眠并递归重试，避免退栈触发释放锁
//            try {
//                Thread.sleep(50);
//            } catch (InterruptedException e) {
//                throw new RuntimeException(e);
//            }
//            return queryWithMutex(id);
//        }
//
//        Shop shop = null;
//        try {
//            // 抢锁成功后进行 Double Check（二次检查缓存），防止前一个线程已重建完成
//            shopJson = stringRedisTemplate.opsForValue().get(key);
//            if (StrUtil.isNotBlank(shopJson)) {
//                return JSONUtil.toBean(shopJson, Shop.class);
//            }
//            if (shopJson != null) {
//                return null;
//            }
//
//            shop = this.getById(id);
//            if (shop == null) {
//                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return null;
//            }
//            stringRedisTemplate.opsForValue().set(
//                    key,
//                    JSONUtil.toJsonStr(shop),
//                    RedisConstants.CACHE_SHOP_TTL + RandomUtil.randomInt(5, 20),
//                    TimeUnit.MINUTES
//            );
//        } finally {
//            this.releaseLock(lockKey);
//        }
//
//        return shop;
//    }
//
//    private Shop queryWithLogicExpire(Long id) {
//        if (id == null) {
//            return null;
//        }
//        String key = RedisConstants.CACHE_SHOP_KEY + id;
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//
//        if (StrUtil.isBlank(shopJson)) {
//            return null;
//        }
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        // 兼容防御：若缓存中混入非 RedisData 格式旧数据，返回 null 避免空指针
//        if (redisData == null || redisData.getExpireTime() == null || redisData.getData() == null) {
//            return null;
//        }
//        // 先把 getData() 强转成 JSONObject，再用 Hutool 转成 Shop
//        Shop data = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//
//        // 判断是否逻辑过期：过期时间在当前时间之后，说明未过期，直接返回
//        if (expireTime.isAfter(LocalDateTime.now())) {
//            return data;
//        }
//
//        // 已过期，尝试获取互斥锁开启异步重建
//        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
//        boolean flag = tryLock(lockKey);
//        if (flag) {
//            try {
//                CACHE_REBUILD_EXECUTOR.submit(() -> {
//                    try {
//                        // 子线程内部做 Double Check，防止前一个线程刚重建完
//                        String latestJson = stringRedisTemplate.opsForValue().get(key);
//                        if (StrUtil.isNotBlank(latestJson)) {
//                            RedisData latestData = JSONUtil.toBean(latestJson, RedisData.class);
//                            if (latestData != null
//                                    && latestData.getExpireTime() != null
//                                    && latestData.getExpireTime().isAfter(LocalDateTime.now())) {
//                                return;
//                            }
//                        }
//                        // 重建缓存
//                        this.saveShopToRedis(id, 60L);
//                    } finally {
//                        // 无论走 Double Check 返回还是重建完成，锁唯一由子线程释放
//                        releaseLock(lockKey);
//                    }
//                });
//            } catch (Exception e) {
//                // 仅当提交线程池本身失败时，主线程兜底释放锁
//                releaseLock(lockKey);
//                throw new RuntimeException(e);
//            }
//        }
//        return data;
//    }
//
    @Override
    public void saveShopToRedis(Long id, Long expireSeconds) {
        Shop shop = this.getById(id);
        if (shop == null) {
            return;
        }
        cacheClient.setWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY + id, shop, expireSeconds, TimeUnit.SECONDS);
    }
//
//
//
//
//    private boolean tryLock(String key) {
//        if (key == null) {
//            return false;
//        }
//
//        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
//        // 拆箱空指针防护
//        return BooleanUtil.isTrue(flag);
//    }
//
//    private void releaseLock(String key){
//        stringRedisTemplate.delete(key);
//    }
//
//    //开启线程池
//    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
}


