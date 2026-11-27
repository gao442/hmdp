package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public  class SimpleRedisLock implements ILock{

    private String name;
    private StringRedisTemplate stringRedisTemplate;
    private static final String KEY_PREFIX = "lock:";
    private static final DefaultRedisScript<Long> redisScript;
    static {
        redisScript = new DefaultRedisScript<>();
        redisScript.setLocation(new ClassPathResource("unLock.lua"));
        redisScript.setResultType(Long.class);
    }
    //UUID 加了 static final，每个 JVM 只生成一次，相同jvm的uuid相同
    //相同jvm直接靠线程id区分
    //需要加锁和解锁的时候uuid相同
    private static final String THREAD_ID_PREFIX = UUID.fastUUID().toString(true)+"-";

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate= stringRedisTemplate;
    }

    @Override
    public boolean tryLock(Long timeoutSec) {
        long threadId = Thread.currentThread().getId();
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, THREAD_ID_PREFIX+threadId , timeoutSec, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    //通过lua脚本，满足原子性
    @Override
    public void unLock() {
        stringRedisTemplate.execute(
                redisScript,
                //Collections.singletonList(KEY_PREFIX + name),
                List.of(KEY_PREFIX + name),
                THREAD_ID_PREFIX + Thread.currentThread().getId()
        );
    }

//    public void unLock() {
//
//        String threadId = THREAD_ID_PREFIX+Thread.currentThread().getId();
//        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//        //此时的get和delete不是原子操作，还是有可能误删别人线程的锁
//        //判断锁标识和释放锁，必须是原子操作
//        if (threadId.equals(id)){
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//        }
//
//    }
}
