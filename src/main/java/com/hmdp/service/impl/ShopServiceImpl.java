package com.hmdp.service.impl;

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
        if(id == null){
            return Result.fail("店铺id不存在");
        }
        String key = RedisConstants.CACHE_SHOP_KEY +id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isNotBlank(shopJson)){
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        //在 ShopServiceImpl.java 内部：随时随地都能直接写 getById(id)
        //因为它继承自父类 ServiceImpl<ShopMapper, Shop>
        Shop shop = this.getById(id);
        if (shop == null){
            return Result.fail("店铺不存在");
        }
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

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
}
