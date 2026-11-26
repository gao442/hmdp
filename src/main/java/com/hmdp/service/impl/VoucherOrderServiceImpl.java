package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
/*
所有贴了 @Service 的类默认都是单例（Singleton）
 */
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
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
        Integer stock = voucher.getStock();
        if (stock<1){
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();
        /*
        1. 先加锁 → 2. 开启事务 → 3. 查单 + 扣库存 + 插订单 →
        4. 提交事务（真正写进 MySQL） → 5. 最后才释放锁

        必须得先加上锁再去开启事务提交事务，最后在解开锁
         */

        /*
        代码里双引号写死的 "xxx" → 自动在常量池

        但是
        底层是现场 new String(...)；
        new String("1010")：显式 new 出来的
        带变量的字符串拼接
        这三种不在字符串常量池，因此每次去字符串常量池取出来的字符串都是不同的地址
        .intern() 的唯一作用：就是把外面动态生成的字符串，强行送进字符串常量池去比对
        池子里有了就直接拿池子里唯一的那一份，保证拿到的地址是一样的
         */
        /*
        this是指serviceimp的实例化对象
        全局只有一个serviceimp的实例化对象

        写 this 或 .class：括号里的东西永远不变（全网只有 1 个），
        所以把所有不同用户全锁在了一起
         */
        /*
        利用jvm内部的锁监视器实现锁机制
         */
//        synchronized (userId.toString().intern()) {
//            //代理为什么是这个类型的对象
//            /*
//            Spring 造出来的这个代理对象 都一定会实现你的 IVoucherOrderService 接口
//            兄弟不能强转成兄弟，只能转成共同的接口 IVoucherOrderService‘
//
//              所以自己内部直接 this 调自己时，相当于绕开了外面的动态代理对象
//
//             */
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        }
        /*
        proxy是外面的壳，获取proxy是为了从里面"跳出去"，再从壳的入口进来

         */
        SimpleRedisLock lock = new SimpleRedisLock("voucherOrder"+userId,stringRedisTemplate);

        boolean flag = lock.tryLock(500L);
        if (!flag){
            return Result.fail("每个人只能下单一次");
            //拿不到锁就直接返回失败（非阻塞）
        }
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            lock.unLock();
        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {

        Long userId = UserHolder.getUser().getId();

        Long count = this.query()
                .eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count();
        if (count>0){
            return Result.fail("每个人只能抢一单");
        }

        boolean success = seckillVoucherService.update()
                //mysql会强行加锁，保证一致性
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock",0)
                .update();
        if (!success) {
            return Result.fail("库存不足");
        }

        VoucherOrder voucherOrder = new VoucherOrder();
        Long voucherOrderId = redisIdWorker.nextId("voucherOrder");
        voucherOrder.setId(voucherOrderId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        this.save(voucherOrder);

        return Result.ok(voucherOrderId);
    }
}
