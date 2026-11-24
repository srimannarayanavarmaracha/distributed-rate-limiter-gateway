-- Atomic Token Bucket rate limiter in Redis
-- KEYS[1] = bucket key
-- ARGV[1] = max capacity
-- ARGV[2] = refill rate (tokens per second)
-- ARGV[3] = current epoch milliseconds
-- ARGV[4] = tokens requested (usually 1)

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local now_ms = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])

local data = redis.call("HMGET", key, "tokens", "last_refill_ms")
local tokens = tonumber(data[1])
local last_refill_ms = tonumber(data[2])

if tokens == nil then
    tokens = capacity
    last_refill_ms = now_ms
else
    local elapsed_sec = math.max(0, (now_ms - last_refill_ms) / 1000.0)
    local added = elapsed_sec * refill_rate
    tokens = math.min(capacity, tokens + added)
    last_refill_ms = now_ms
end

local allowed = 0
local retry_after_sec = 0

if tokens >= requested then
    tokens = tokens - requested
    allowed = 1
else
    local deficit = requested - tokens
    if refill_rate > 0 then
        retry_after_sec = math.ceil(deficit / refill_rate)
    else
        retry_after_sec = 1
    end
end

redis.call("HSET", key, "tokens", tokens, "last_refill_ms", last_refill_ms)
local ttl_sec = math.max(60, math.ceil((capacity / math.max(refill_rate, 0.001)) * 2))
redis.call("EXPIRE", key, ttl_sec)

local reset_sec = math.ceil(now_ms / 1000.0) + retry_after_sec
return { allowed, math.floor(tokens), reset_sec, retry_after_sec }
