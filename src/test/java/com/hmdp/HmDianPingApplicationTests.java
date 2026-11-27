package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.Voucher;
import com.hmdp.service.IShopService;
import com.hmdp.service.IUserService;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private IShopService shopService;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Resource
    private IVoucherService voucherService;

    private ExecutorService es = Executors.newFixedThreadPool(300);

    /**
     * 缓存预热测试：将 1 号商铺写入 Redis 并设置 10 秒逻辑过期时间
     */
    @Test
    void testSaveShop() {
        shopService.saveShopToRedis(1L, 10L);
    }

    @Test
    void testIdWorker() throws InterruptedException {
        // 1️⃣ 挂上倒计时牌：总数设为 300（对应下面要提交的 300 次任务）
        CountDownLatch latch = new CountDownLatch(300);

        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            // 2️⃣ 每个子任务跑完自己的 100 次循环后，把倒计时牌 -1
            latch.countDown();
        };

        long begin = System.currentTimeMillis();
        // 把任务提交 300 次给线程池并发跑
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }

        // 3️⃣ 主线程在这儿卡住死等！直到 300 个子任务全部调完 countDown() 把牌子减到 0，才往下走
        latch.await();

        long end = System.currentTimeMillis();
        System.out.println("30000 个 ID 全部生成完毕，总耗时 = " + (end - begin) + " ms");
    }

    /**
     * 批量生成 1000 个压测用户写入数据库，并在 Redis 中写入对应登录 token，导出至 JMeter 数据文件
     */
    @Test
    void generateUsersAndTokens() throws IOException {
        // 1. 构造 1000 个测试手机号（13900000001 ~ 13900001000）
        List<String> phones = new ArrayList<>(1000);
        for (int i = 1; i <= 1000; i++) {
            phones.add(String.format("139%08d", i));
        }

        // 2. 查询已存在的手机号，避免重复运行时触发唯一索引冲突
        List<User> existingUsers = userService.lambdaQuery()
                .in(User::getPhone, phones)
                .list();
        Set<String> existingPhones = new HashSet<>(existingUsers.size());
        for (User existingUser : existingUsers) {
            existingPhones.add(existingUser.getPhone());
        }

        // 3. 批量插入缺失的测试用户
        LocalDateTime now = LocalDateTime.now();
        List<User> newUsers = new ArrayList<>();
        for (int i = 1; i <= 1000; i++) {
            String phone = phones.get(i - 1);
            if (!existingPhones.contains(phone)) {
                User user = User.builder()
                        .phone(phone)
                        .nickName("user_" + i)
                        .icon("")
                        .createTime(now)
                        .updateTime(now)
                        .build();
                newUsers.add(user);
            }
        }
        if (!newUsers.isEmpty()) {
            userService.saveBatch(newUsers, 200);
        }

        // 4. 重新查出完整 1000 个用户（确保拿到数据库自增 id）
        List<User> allUsers = userService.lambdaQuery()
                .in(User::getPhone, phones)
                .orderByAsc(User::getId)
                .list();
        Assertions.assertEquals(1000, allUsers.size(), "数据库中测试用户数量应为 1000");

        // 5. 为每个用户生成 token 并写入 Redis（与 UserServiceImpl.login 和 RefreshTokenInterceptor 保持完全一致）
        List<String> tokens = new ArrayList<>(1000);
        CopyOptions copyOptions = CopyOptions.create()
                .setIgnoreNullValue(true)
                .setFieldValueEditor((fieldName, fieldValue) ->
                        fieldValue == null ? null : fieldValue.toString());

        for (User user : allUsers) {
            String token = UUID.randomUUID().toString(true);
            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(), copyOptions);

            String key = RedisConstants.LOGIN_USER_KEY + token;
            stringRedisTemplate.opsForHash().putAll(key, userMap);
            stringRedisTemplate.expire(key, 24, TimeUnit.HOURS);
            tokens.add(token);
        }

        // 6. 写出到 JMeter 可直接导入的文本文件（UTF-8 无 BOM，每行一个 token）
        Path outputPath = Path.of("d:/Development/project/hmdp/测压数据/tokens.txt");
        Files.createDirectories(outputPath.getParent());
        Files.write(outputPath, tokens, StandardCharsets.UTF_8);

        // 7. 校验导出条数与首条 Redis 数据可正常反序列化
        Assertions.assertEquals(1000, tokens.size(), "生成 token 数量应为 1000");
        Map<Object, Object> firstUserMap = stringRedisTemplate.opsForHash()
                .entries(RedisConstants.LOGIN_USER_KEY + tokens.get(0));
        UserDTO verifiedUser = BeanUtil.fillBeanWithMap(firstUserMap, new UserDTO(), false);
        Assertions.assertNotNull(verifiedUser.getId(), "Redis 中反序列化出的 UserDTO.id 不应为空");

        System.out.println("成功准备用户数: " + allUsers.size()
                + "（新增 " + newUsers.size() + "，已存在 " + existingUsers.size() + "）"
                + "，导出 token 数: " + tokens.size()
                + "，文件路径: " + outputPath);
    }

    /**
     * 新增一张压测用秒杀券，并验证同步写入 MySQL 与 Redis 库存缓存
     */
    @Test
    void addSeckillVoucherForTest() {
        Voucher voucher = new Voucher();
        voucher.setShopId(1L);
        voucher.setTitle("100元代金券（压测专用）");
        voucher.setSubTitle("周一至周日均可使用");
        voucher.setRules("全场通用\n无需预约\n可无限叠加\n不兑现、不找零\n仅限堂食");
        voucher.setPayValue(8000L);
        voucher.setActualValue(10000L);
        voucher.setType(1);
        voucher.setStatus(1);
        voucher.setStock(200);
        voucher.setBeginTime(LocalDateTime.of(2025, 1, 1, 0, 0, 0));
        voucher.setEndTime(LocalDateTime.of(2030, 12, 31, 23, 59, 59));

        voucherService.addSeckillVoucher(voucher);

        Assertions.assertNotNull(voucher.getId(), "新增秒杀券 ID 不应为空");
        String redisKey = RedisConstants.SECKILL_STOCK_KEY + voucher.getId();
        String redisStock = stringRedisTemplate.opsForValue().get(redisKey);
        Assertions.assertEquals("200", redisStock, "Redis 库存缓存应为 200");

        System.out.println("新秒杀券创建成功！voucherId=" + voucher.getId()
                + ", redisKey=" + redisKey
                + ", redisStock=" + redisStock);
    }
}
