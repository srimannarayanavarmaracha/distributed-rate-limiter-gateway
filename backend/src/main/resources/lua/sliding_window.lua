-- Atomic Sliding Window Log rate limiter in Redis
-- KEYS[1] = sorted set key
-- ARGV[1] = max requests in window
-- ARGV[2] = window size in milliseconds
-- ARGV[3] = current epoch milliseconds
-- ARGV[4] = unique member id

local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window_ms = tonumber(ARGV[2])
local now_ms = tonumber(ARGV[3])
local member = ARGV[4]

local window_start = now_ms - window_ms
redis.call("ZREMRANGEBYSCORE", key, 0, window_start)

local count = redis.call("ZCARD", key)
local allowed = 0
local remaining = 0
local retry_after_sec = 0

if count < limit then
    redis.call("ZADD", key, now_ms, member)
    allowed = 1
    remaining = limit - (count + 1)
else
    local oldest = redis.call("ZRANGE", key, 0, 0, "WITHSCORES")
    if #oldest >= 2 then
        local oldest_ms = tonumber(oldest[2])
        local wait_ms = math.max(1000, (oldest_ms + window_ms) - now_ms)
        retry_after_sec = math.ceil(wait_ms / 1000.0)
    else
        retry_after_sec = math.ceil(window_ms / 1000.0)
    end
end

redis.call("PEXPIRE", key, window_ms + 5000)
local reset_sec = math.ceil((now_ms + window_ms) / 1000.0)
return { allowed, remaining, reset_sec, retry_after_sec }
