package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Redis 缓存通用工具类
 */
@Slf4j
@Component
public class CacheClient {

    @Resource
    private  StringRedisTemplate stringRedisTemplate;

    /**
     * 将任意 Java 对象序列化为 JSON 并存入 Redis，同时设置物理过期时间（TTL）
     *
     * @param key   Redis 键
     * @param value 要缓存的对象
     * @param time  过期时间数值
     * @param unit  时间单位
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 将任意 Java 对象包装为 RedisData 并存入 Redis，设置逻辑过期时间（物理上永不过期）
     *
     * @param key   Redis 键
     * @param value 要缓存的业务对象
     * @param time  逻辑过期时间数值
     * @param unit  时间单位
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 封装数据和逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 写入 Redis（不设置物理 TTL）
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public  <R,ID>R  queryWithPassThrough(ID id,
                                          String keyPrefix,
                                          Class<R> type,
                                          Function<ID,R> dbFallBack,
                                          Long time,
                                          TimeUnit unit) {
        if (id == null) {
            return null;
        }
        String key = keyPrefix + id;
        String RJson = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isNotBlank(RJson)) {
            R r = JSONUtil.toBean(RJson, type);
            return r;
        }
        if (RJson != null) {
            return null;
        }

        R r = dbFallBack.apply(id);

        if (r == null) {
            // 空对象固定缓存短时间，避免与正常业务 TTL 混淆
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 统一转为秒后增加随机过期时间（5~10 分钟），避免单位变化导致过期时间异常
        long ttlSeconds = unit.toSeconds(time) + RandomUtil.randomInt(300, 600);
        this.set(key, r, ttlSeconds, TimeUnit.SECONDS);

        return r;
    }

    public <R,ID> R  queryWithMutex(ID id,
                                    String keyPrefix,
                                    Class<R> type,
                                    Function<ID,R> dbFunction,
                                    Long time,
                                    TimeUnit unit){
        if (id == null){
            return null;
        }
        String key = keyPrefix + id ;
        String RJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(RJson)){
            return JSONUtil.toBean(RJson,type);
        }
        if (RJson != null){
            return null;
        }

        // 单独定义锁 Key，避免与缓存数据 Key 混用导致释放锁时误删业务缓存
        String lockKey = "lock:" + key;
        boolean flag = tryLock(lockKey);

        if (!flag){
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return queryWithMutex(id,keyPrefix,type,dbFunction,time,unit);
        }
        try {
            String lastRJson = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(lastRJson)){
                return JSONUtil.toBean(lastRJson,type);
            }
            if (lastRJson != null){
                return null;
            }
            R r = dbFunction.apply(id);
            if (r == null){
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            this.set(key,r,time,unit);
            return r;
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        } finally {
            releaseLock(lockKey);
        }
    }


    public  <R,ID>R queryWithLogicExpire(ID id,
                                         String keyPrefix,
                                         Class<R> type,
                                         Long logicalTime,
                                         TimeUnit unit,
                                         Function<ID,R> rebuild) {
        if (id == null) {
            return null;
        }
        String key = keyPrefix + id;
        String RJson = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isBlank(RJson)) {
            return null;
        }
        RedisData redisData = JSONUtil.toBean(RJson, RedisData.class);
        // 兼容防御：若缓存中混入非 RedisData 格式旧数据，返回 null 避免空指针
        if (redisData == null || redisData.getExpireTime() == null || redisData.getData() == null) {
            return null;
        }
        // 先把 getData() 强转成 JSONObject，再用 Hutool 转为目标类型
        R data = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 判断是否逻辑过期：过期时间在当前时间之后，说明未过期，直接返回
        if (expireTime.isAfter(LocalDateTime.now())) {
            return data;
        }

        // 已过期，尝试获取互斥锁开启异步重建（用 keyPrefix + id 隔离不同业务的锁）
        String lockKey = "lock:" + key;
        boolean flag = tryLock(lockKey);
        if (flag) {
            try {
                CACHE_REBUILD_EXECUTOR.submit(() -> {
                    try {
                        // 子线程内部做 Double Check，防止前一个线程刚重建完
                        String latestJson = stringRedisTemplate.opsForValue().get(key);
                        if (StrUtil.isNotBlank(latestJson)) {
                            RedisData latestData = JSONUtil.toBean(latestJson, RedisData.class);
                            if (latestData != null
                                    && latestData.getExpireTime() != null
                                    && latestData.getExpireTime().isAfter(LocalDateTime.now())) {
                                return;
                            }
                        }
                        // 重建缓存
                        R r = rebuild.apply(id);
                        if (r == null) {
                            return;
                        }
                        this.setWithLogicalExpire(key, r, logicalTime, unit);
                    } finally {
                        // 无论走 Double Check 返回还是重建完成，锁唯一由子线程释放
                        releaseLock(lockKey);
                    }
                });
            } catch (Exception e) {
                // 仅当提交线程池本身失败时，主线程兜底释放锁
                releaseLock(lockKey);
                throw new RuntimeException(e);
            }
        }
        return data;
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

    //开启线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

}
