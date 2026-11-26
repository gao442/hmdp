package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;

    @Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {
        if (voucherId == null){
            return Result.fail("订单id不存在");
        }
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if (voucher == null){
            return Result.fail("活动不存在");
        }
        if (LocalDateTime.now().isAfter(voucher.getEndTime())){
            return Result.fail("活动已经结束");
        }
        if (LocalDateTime.now().isBefore(voucher.getBeginTime())){
            return Result.fail("活动还没开始");
        }
        if (voucher.getStock()<1){
            return Result.fail("库存不足");
        }

        boolean success = seckillVoucherService.update()
                //mysql会强行加锁，保证一致性
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .update();
        if (!success) {
            return Result.fail("库存不足");
        }

        VoucherOrder voucherOrder = new VoucherOrder();
        Long voucherOrderId = redisIdWorker.nextId("voucherOrder");
        voucherOrder.setId(voucherOrderId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        this.save(voucherOrder);

        return Result.ok(voucherOrderId);

    }
}
