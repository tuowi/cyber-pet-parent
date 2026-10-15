package com.heima.common.constant;

public class RedisKeyPrefix {
    // auth-service
    public static final String LOGOUT_TOKENS_KEY = "logout:tokens:";
    // pet-service
    public static final String PET_STATE_KEY = "pet:status:";
    public static final String PET_LOCK_INTERACT_KEY = "pet:lock:interact:";
    public static final Long PET_LOCK_INTERACT_WAIT_TIME = 3L; //获取锁的最大等待时间
    public static final Long PET_LOCK_INTERACT_LEASE_TIME = 10L; //锁的自动释放时间
    public static final String STREAM_PET_EVENTS_KEY = "stream:pet:events";
    public static final String PET_FREE_PET_KEY = "pet:free:pet:";   // 免费抚摸计数
    public static final int PET_FREE_PET_DAILY_LIMIT = 5;            // 每天免费抚摸次数
    public static final String PET_LOCK_DECAY_KEY = "pet:lock:decay:";
    public static final String PET_MOOD_DAILY_KEY = "pet:mood:daily:"; // 每日心情快照
    public static final Long PET_MOOD_DAILY_TTL = 30L; // 30天自动过期

    // shop-service
    public static final String SHOP_STOCK_KEY = "shop:stock:";
    public static final String SHOP_PRODUCT_LIST_KEY = "shop:product:list:";
    public static final Long SHOP_PRODUCT_LIST_TTL = 60L;
    public static final String SHOP_LIMIT_KEY = "shop:limit:";
    public static final String SHOP_ORDER_LIST_KEY = "shop:order:list:";
    public static final Long SHOP_ORDER_LIST_TTL = 300L;
    public static final Long SHOP_ORDER_LIST_NULL_TTL = 60L;
    public static final String USER_BALANCE_KEY = "user:balance:";
    public static final String SIGNIN_KEY = "signin:";

    // social-service
    public static final String SOCIAL_ONLINE_KEY = "social:online:";
    public static final Long SOCIAL_ONLINE_TTL = 30L;
    public static final String SOCIAL_GEO_NEARBY_KEY = "social:geo:nearby";
    public static final String SOCIAL_LOCK_STEAL_KEY = "social:lock:steal:";
    public static final Long SOCIAL_LOCK_STEAL_WAIT_TIME = 3L;
    public static final Long SOCIAL_LOCK_STEAL_LEASE_TIME = 10L;
    public static final String STREAM_SOCIAL_EVENTS_KEY = "stream:social:events";
    public static final String SOCIAL_FEED_HOT_KEY = "social:post:hot";
    public static final String POST_LIKES_KEY = "post:likes:";
    public static final String SOCIAL_TIMELINE_KEY = "social:timeline:";
    public static final Long SOCIAL_TIMELINE_TTL = 30L;
    public static final String SOCIAL_POST_DETAIL_KEY = "social:post:detail:";
    public static final Long SOCIAL_POST_DETAIL_TTL = 60L;

    // agent-service
    public static final String AGENT_MEMORY_KEY = "agent:memory:";

    // analytics-service
    public static final String INTIMACY_LEADERBOARD_KEY = "intimacy:leaderboard";
    public static final String INTIMACY_LEADERBOARD_CACHE_KEY = "intimacy:leaderboard:cache:";
    public static final Long INTIMACY_LEADERBOARD_CACHE_TTL = 60L;
}
