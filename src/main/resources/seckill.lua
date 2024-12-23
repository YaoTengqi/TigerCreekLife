-- 参数列表
local voucherId = ARGV[1]
local userId = ARGV[2]

-- 数据key
-- 库存key
local stockKey = 'seckill:stock:' .. voucherId
-- 订单key
local orderKey = 'seckill:order:' .. voucherId

-- 业务脚本
-- 判断库存是否充足
if(tonumber(redis.call('get', stockKey)) <= 0) then
    return 1    -- 库存不足
end
if(redis.call('sismember', orderKey, userId) == 1) then
    return 2    -- 已经买过
end

-- 扣库存
redis.call('incrby', stockKey, -1)
-- 添加用户订单
redis.call('sadd',orderKey, userId)
-- 正常结束返回0
return 0