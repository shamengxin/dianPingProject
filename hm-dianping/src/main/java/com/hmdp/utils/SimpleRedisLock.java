package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import com.hmdp.interceptor.LoginInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.net.StandardSocketOptions;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {


    private StringRedisTemplate stringRedisTemplate;
    private String name;

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true);
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPR;
    static {
        UNLOCK_SCRIPR = new DefaultRedisScript<>();
        UNLOCK_SCRIPR.setLocation(new ClassPathResource("unclock.lua"));
        UNLOCK_SCRIPR.setResultType(Long.class);
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        // 获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 获取锁
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }



    @Override
    public void unlock() {

        // 调用lua脚本
        stringRedisTemplate.execute(
                UNLOCK_SCRIPR,
                Collections.singletonList(KEY_PREFIX+name),
                ID_PREFIX + Thread.currentThread().getId());

    }

    /*@Override
    public void unlock() {
        //获取线程标识
        String threadID = ID_PREFIX + Thread.currentThread().getId();
        //获取锁中的标识
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);

        //判断标识是否一致
        if (threadID.equals(id)){
            // 释放锁
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }

    }*/
}
