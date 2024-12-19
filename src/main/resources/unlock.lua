-- 比较线程标识与锁中的标识是否一直
if(redis.call('get', KEYS[1]) == ARGV[1]) then
 -- 释放锁 del key
    return redis.call('del', KEYS[1])
end
return 0 -- 否则则直接返回