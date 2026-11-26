package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public  class SimpleRedisLock implements ILock{

    private String name;
    private StringRedisTemplate stringRedisTemplate;
    private static final String KEY_PREFIX = "lock:";
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

    @Override
    public void unLock() {

        String threadId = THREAD_ID_PREFIX+Thread.currentThread().getId();
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);

        if (threadId.equals(id)){
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }

    }
}
