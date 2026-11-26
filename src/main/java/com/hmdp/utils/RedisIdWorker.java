package com.hmdp.utils;

import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final long BEGIN_TIMESTAMP = 1155945600;

    private static final Integer COUNT_BITS = 32;

    //数据库用数字做主键索引比字符串快得多

    /*
    全局唯一id第 1 个格子（1 位）：符号位，固定是 0（代表正数）。
    中间 31 个格子（31 位）：用来放 timeStamp（距离 2006-08-19 过去了多少秒）。
    右边 32 个格子（32 位）：用来放 count（今天 Redis 取号机取到的第几号
     */
    public Long nextId(String keyPrefix){
        //1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowSecond - BEGIN_TIMESTAMP;
        //2.生成序列
//      //防止序列号溢出：序列号只分配了 32 位（最大约 42.9 亿），
//      如果永远共用一个 Key，时间久了会超限；每天换一个新 Key，
//      每天都能重新发 42.9 亿个号
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //Redis 执行单条 INCR 命令是单线程原子操作
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        //拼接返回
        //通过二进制位运算拼接整数
        return timeStamp << COUNT_BITS | count;
    }

/*
泄露商业机密 + 容易被扒数据（规律太明显）
以后“分库分表”时直接撞车（无法全局唯一
没法在“写数据库之前”提前拿到订单号（后面异步秒杀的关键！）
而秒杀为了极致速度，后面我们会做“异步下单”——用户一点抢购，还没写进 MySQL 呢，就要先把订单号生成好返回给前端去付款！这时候就必须有一个独立于 MySQL 的发号器。

完全乱序，会把 MySQL 的 B+ 树索引搞崩溃！
太长太胖（占空间、比对慢


 */
}
