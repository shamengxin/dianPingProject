package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        synchronized (userId.toString().intern()){
            //原因：因为所调用的这个方法有事务机制，使用了spring的自动代理，要想这个事务生效，只能使用这个类
            //的代理类来调用这个方法，才会有事务的机制，所以这里要创建事务这个类的事务代理类
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
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
                .eq("voucher_id", voucherId).gt("stock",0)
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
