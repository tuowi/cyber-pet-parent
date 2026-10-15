-- 原子操作：检查余额 + 检查库存 + 检查限购 + 扣余额 + 扣库存 + 增计数
-- KEYS[1] = shop:stock:{productId}
-- KEYS[2] = shop:limit:{userId}:{productId}:{yyyyMMdd} （仅有限购时传入）
-- KEYS[3] = user:balance:{userId}
-- ARGV[1] = 购买数量
-- ARGV[2] = 每人每日限购数量（nil/"null" 表示不限购）
-- ARGV[3] = 订单总金额（从余额中扣除）
-- 返回值: 1=成功, -1=库存不足, -2=超限购, -3=余额不足

-- 1. 检查并扣除用户余额
local balance = tonumber(redis.call('GET', KEYS[3]))
local totalPrice = tonumber(ARGV[3])
if balance == nil or balance < totalPrice then
    return -3
end
local newBalance = balance - totalPrice
redis.call('SET', KEYS[3], string.format("%.2f", newBalance))

-- 2. 检查库存
local stock = tonumber(redis.call('GET', KEYS[1]))
if stock == nil or stock < tonumber(ARGV[1]) then
    -- 库存不足，回滚余额
    redis.call('SET', KEYS[3], string.format("%.2f", balance))
    return -1
end

-- 3. 仅当有限购时才检查限购计数器
local limit = tonumber(ARGV[2])
if limit ~= nil and limit > 0 then
    local bought = tonumber(redis.call('GET', KEYS[2])) or 0
    if bought + tonumber(ARGV[1]) > limit then
        -- 超限购，回滚余额
        redis.call('SET', KEYS[3], string.format("%.2f", balance))
        return -2
    end
    redis.call('INCRBY', KEYS[2], ARGV[1])
    -- 如果限购 Key 是新创建的，设过期时间到当天结束
    local now = tonumber(redis.call('TIME')[1]) + 28800  -- 转为北京时间（UTC+8）
    local expireSec = 86400 - (now % 86400)             -- 当天剩余秒数
    redis.call('EXPIRE', KEYS[2], expireSec)
end

-- 4. 扣减库存
redis.call('DECRBY', KEYS[1], ARGV[1])
return 1