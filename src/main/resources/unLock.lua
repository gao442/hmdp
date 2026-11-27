-- 比较线程标识与锁中的标识是否一致
if (redis.call('get', KEYS[1]) == ARGV[1]) then
    -- 释放锁 del key
    return redis.call('del', KEYS[1])
end
return 0
--[[get 不存在的 key	返回的是布尔值 false，
不是 nil	判断是否存在用 if (not val)；不过在 unlock.lua 里
 false == ARGV[1] 本身就是 false，不会出问题]]

--[[返回值映射
return redis.call('del', KEYS[1]) / return 0
Redis 返回整数，Spring Data Redis 默认映射成 Java 的 Long
（所以泛型写 DefaultRedisScript<Long>）]]

--[[
Lua 脚本执行到一半报错了，前面已经执行的命令会回滚吗？
不会回滚！
Redis 的 Lua 脚本保证的是“执行过程不被打断（原子执行/隔离性）”，
而不是 MySQL 那种带 undo log 的 ACID 事务回滚。
如果第 1 行 set 执行成功，
第 2 行代码报错，
第 1 行的修改依然生效。

写脚本的铁律：把所有的 get / 条件校验全写在前面，校验全通过后再执行 del / set / incrby 等写操作
]]

--[[ua 脚本里能写耗时逻辑吗？
--绝对不能！ 因为 Redis 是单线程，Lua 脚本跑多久，
整个 Redis 就卡死多久，其他业务请求全部排队等待。
所以 Lua 脚本必须短小精悍、无循环阻塞]]

--[[
要支持回滚就得像 MySQL 一样维护 undo log，
会极大增加复杂度并拖慢 Redis 的速度




