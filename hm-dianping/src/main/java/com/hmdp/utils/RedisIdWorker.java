package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 生成全局唯一id
 */
@Component
public class RedisIdWorker {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 开始时间戳
     */
    private static final Long BEGIN_TIMESTAMP = 1005436800L;

    /**
     * 序列号为位数
     */
    private static final int COUNT_BITS = 32;


    public long nextId(String keyPrefix) {
        // 1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowSecond - BEGIN_TIMESTAMP;
        // 2.生成序列号
        // 2.1 获取当天日期，精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        // 2.2 自增长
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        // 3.拼接并返回

        return timeStamp << COUNT_BITS | count;
    }
}
