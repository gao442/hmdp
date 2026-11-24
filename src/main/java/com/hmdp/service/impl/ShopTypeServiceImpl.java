package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.hmdp.utils.RedisConstants;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public List<ShopType> queryTypeList() {
        String shopTypeListJson = stringRedisTemplate.opsForValue().get(RedisConstants.SHOP_TYPE_LIST_CACHE);

        if(StrUtil.isNotBlank(shopTypeListJson)){
            List<ShopType> shopTypeList = JSONUtil.toList(shopTypeListJson, ShopType.class);
            return shopTypeList;
        }
        List<ShopType> shopTypeList =query().orderByAsc("sort").list();
        if(!shopTypeList.isEmpty()){
            String shopTypeJson = JSONUtil.toJsonStr(shopTypeList);
            stringRedisTemplate.opsForValue().set(RedisConstants.SHOP_TYPE_LIST_CACHE,shopTypeJson);
        }
        return shopTypeList;
    }
}
