package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    Result queryShopById(Long id);

    Result updateShop(Shop shop);

    /**
     * 将商铺数据封装逻辑过期时间后写入 Redis（用于缓存预热与异步重建）
     */
    void saveShopToRedis(Long id, Long expireSeconds);
}
