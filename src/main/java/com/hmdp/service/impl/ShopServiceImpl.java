package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
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

    @Override
    public Result queryShopById(Long id) {
        //缓存穿透的解决方案
        //Shop shop = queryWithPassThrough(id);

        //缓存击穿的解决方案
        Shop shop = queryWithMutex(id);
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




    private Shop queryWithPassThrough(Long id) {
        if (id == null) {
            return null;
        }
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        if (shopJson != null) {
            return null;
        }

        //在 ShopServiceImpl.java 内部：随时随地都能直接写 getById(id)
        //因为它继承自父类 ServiceImpl<ShopMapper, Shop>
        Shop shop = this.getById(id);
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL + RandomUtil.randomInt(5, 20), TimeUnit.MINUTES);

        return shop;
    }

    private Shop queryWithMutex(Long id) {
        if (id == null) {
            return null;
        }
        String key = RedisConstants.CACHE_SHOP_KEY + id;

        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        if (shopJson != null) {
            return null;
        }

        // 按商铺 id 拼接锁 Key，缩小锁粒度
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean flag = this.tryLock(lockKey);
        if (!flag) {
            // 未抢到锁时在 try-finally 外部休眠并递归重试，避免退栈触发释放锁
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return queryWithMutex(id);
        }

        Shop shop = null;
        try {
            // 抢锁成功后进行 Double Check（二次检查缓存），防止前一个线程已重建完成
            shopJson = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(shopJson)) {
                return JSONUtil.toBean(shopJson, Shop.class);
            }
            if (shopJson != null) {
                return null;
            }

            shop = this.getById(id);
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            stringRedisTemplate.opsForValue().set(
                    key,
                    JSONUtil.toJsonStr(shop),
                    RedisConstants.CACHE_SHOP_TTL + RandomUtil.randomInt(5, 20),
                    TimeUnit.MINUTES
            );
        } finally {
            this.releaseLock(lockKey);
        }

        return shop;
    }

    private boolean tryLock(String key) {
        if (key == null) {
            return false;
        }

        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        // 拆箱空指针防护
        return BooleanUtil.isTrue(flag);
    }

    private void releaseLock(String key){
        stringRedisTemplate.delete(key);
    }
}


