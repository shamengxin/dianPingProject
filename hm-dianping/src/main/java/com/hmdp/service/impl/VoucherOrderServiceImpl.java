package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Override
    public Result seckillVoucher(Long voucherId) {

        // 1.查询优惠卷信息
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 2.判断秒杀是否开始，是否结束
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            // 3.未开始，返回异常信息
            return Result.fail("活动尚未开始！");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("活动已结束！");
        }
        // 4.开始，判断库存是否充足
        if (voucher.getStock() < 1) {
            // 5.不足，返回异常信息
            return Result.fail("库存不足！");
        }
        Long userId = UserHolder.getUser().getId();
        // 创建锁对象
        // SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate, "order:" + userId);
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // 获取锁
        boolean isLock = lock.tryLock();
        // 判断是否获取锁成功
        if (!isLock) {
            //获取锁失败，返回错误或重试
            return Result.fail("每人限购一单");
        }
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }finally {
            //释放锁
            lock.unlock();
        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId){
        // 6.一人一单
        Long userId = UserHolder.getUser().getId();
        // 6.1 查询订单
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        // 6.2 判断是否存在
        if (count > 0){
            return Result.fail("每人限购一单！");
        }
        // 7.充足，扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock",0)
                .update();
        if (!success) {
            return Result.fail("库存不足！");
        }

        // 8.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 8.1 订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 8.2 用户id
        voucherOrder.setUserId(userId);
        // 7.3 代金卷id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);

        // 8.返回订单id
        return Result.ok(orderId);
    }
}
