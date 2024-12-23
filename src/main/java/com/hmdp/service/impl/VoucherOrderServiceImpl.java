package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.RedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;

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

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    // 初始化加载lua脚本
    static {
        SECKILL_SCRIPT = new DefaultRedisScript();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 获取用户信息
        Long userId = UserHolder.getUser().getId();
        // 1. 执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );
        // 2. 根据返回值进行判断
        // 2.1 不为 0则返回异常信息
        if (result != 0) {
            return result == 1 ? Result.fail("库存不足!") : Result.fail("您已购买过此优惠券!");
        }
        // 2.2 为 0则正常下单
        long orderId = redisIdWorker.nextId("order");

        // 3. 将订单信息添加到消息队列
        // 4. 返回订单id
        return Result.ok(orderId);
    }

    @Transactional  // 由于是两张表的操作(优惠券表和订单表) 因此需要增加事务机制
    public Result createVoucherOrder(Long voucherId) {
        // 一人一单
        Long userId = UserHolder.getUser().getId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            // 该用户已经买过一次秒杀券
            return Result.fail("您已买过此优惠券!");
        }
        // 5. 扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .gt("stock", 0) // CAS乐观锁, 当货品数量大于0时扣除库存, 否则在操作数据库前进行检查
                .eq("voucher_id", voucherId)
                .update();
        if (!success) {
            return Result.fail("库存不足!");
        }
        // 6. 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 6.1 订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 6.2 用户id
        voucherOrder.setUserId(userId);
        // 6.3 代金券id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        // 7. 返回订单信息
        return Result.ok(orderId);
    }
}
