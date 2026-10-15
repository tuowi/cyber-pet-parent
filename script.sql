create table cyber_shop.currency_transaction
(
    id            bigint                             not null comment '雪花ID'
        primary key,
    user_id       bigint                             not null comment '用户ID',
    amount        decimal(10, 2)                     not null comment '变动金额，正=收入，负=支出',
    type          varchar(10)                        not null comment 'INCOME（收入）/ EXPENSE（支出）',
    biz_type      varchar(30)                        not null comment '业务类型: DAILY_SIGNIN / REGISTER_GIFT / ORDER_PAY',
    biz_id        bigint                             null comment '关联业务ID（如 orderId）',
    balance_after decimal(10, 2)                     not null comment '变动后余额（快照）',
    create_time   datetime default CURRENT_TIMESTAMP not null
)
    comment '货币流水表';

create index idx_create_time
    on cyber_shop.currency_transaction (create_time);

create index idx_user_id
    on cyber_shop.currency_transaction (user_id);

create table cyber_social.interaction_record
(
    id            bigint                                not null comment '雪花ID'
        primary key,
    from_user_id  bigint                                not null comment '发起方用户ID',
    to_user_id    bigint                                not null comment '目标方用户ID',
    pet_id        bigint                                null comment '发起方宠物ID',
    target_pet_id bigint                                null comment '目标方宠物ID',
    type          varchar(20)                           not null comment '互动类型: STEAL_SNACK / PLAY_MATCH / VISIT',
    status        varchar(20) default 'SUCCESS'         not null comment 'SUCCESS / FAILED',
    create_time   datetime    default CURRENT_TIMESTAMP not null,
    update_time   datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '状态变更时间'
)
    comment '社交互动记录表';

create index idx_from_to_day
    on cyber_social.interaction_record (from_user_id, to_user_id, create_time);

create index idx_from_user
    on cyber_social.interaction_record (from_user_id);

create index idx_to_user
    on cyber_social.interaction_record (to_user_id);

create table cyber_analytics.intimacy_event
(
    id            bigint                             not null comment '雪花ID'
        primary key,
    event_id      varchar(64)                        not null comment '业务事件ID（来自 Stream 消息），用于幂等去重',
    user_id       bigint                             not null comment '用户ID',
    pet_id        bigint                             not null comment '宠物ID',
    event_type    varchar(30)                        not null comment '事件类型: FEED/PLAY/BATHE/STEALED',
    score_change  int                                not null comment '亲密度变化量（正或负）',
    current_score int                                not null comment '变化后的亲密度值',
    create_time   datetime default CURRENT_TIMESTAMP not null,
    constraint uk_event_id
        unique (event_id)
)
    comment '亲密度事件流水表';

create index idx_create_time
    on cyber_analytics.intimacy_event (create_time);

create index idx_user_pet
    on cyber_analytics.intimacy_event (user_id, pet_id);

create table cyber_pet.pet_attribute
(
    id              bigint                             not null comment '雪花ID'
        primary key,
    pet_id          bigint                             not null comment '关联 pet_info.id',
    hunger          int      default 100               not null comment '饥饿值 0-100，越低越饿',
    mood            int      default 100               not null comment '心情值 0-100，越低越差',
    cleanliness     int      default 100               not null comment '清洁值 0-100，越低越脏',
    last_decay_time datetime                           not null comment '上次衰减时间',
    create_time     datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time     datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    constraint pet_id
        unique (pet_id)
)
    comment '宠物属性表（MySQL 持久化副本）';

create index idx_pet_id
    on cyber_pet.pet_attribute (pet_id);

create table cyber_pet.pet_info
(
    id           bigint                                not null comment '雪花ID'
        primary key,
    user_id      bigint                                not null comment '主人用户ID',
    pet_name     varchar(50)                           not null comment '宠物昵称',
    species      varchar(20)                           not null comment '物种: CAT/DOG',
    personality  varchar(20) default '粘人'            not null comment '性格: 傲娇/粘人/贪吃/佛系',
    avatar       varchar(255)                          null comment '头像URL',
    state        varchar(20) default 'NORMAL'          not null comment '当前状态: NORMAL/HUNGRY/ANGRY/SICK/RUNAWAY',
    runaway_time datetime                              null comment '离家出走时间，NULL=未出走（兜底查询用）',
    create_time  datetime    default CURRENT_TIMESTAMP not null comment '领养时间',
    update_time  datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    deleted      tinyint     default 0                 not null comment '0=正常 1=已删除'
)
    comment '宠物基础信息表';

create index idx_state
    on cyber_pet.pet_info (state);

create index idx_user_id
    on cyber_pet.pet_info (user_id);

create table cyber_social.post
(
    id            bigint                             not null comment '雪花ID'
        primary key,
    user_id       bigint                             not null comment '发帖用户ID',
    pet_id        bigint                             not null comment '关联宠物ID',
    content       varchar(500)                       not null comment '喵言喵语内容',
    image_url     varchar(255)                       null comment '配图URL',
    like_count    int      default 0                 not null comment '点赞数（冗余，加速查询）',
    comment_count int      default 0                 not null comment '评论数（冗余）',
    create_time   datetime default CURRENT_TIMESTAMP not null,
    update_time   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    deleted       tinyint  default 0                 not null
)
    comment '朋友圈帖子表';

create index idx_create_time
    on cyber_social.post (create_time);

create index idx_user_id
    on cyber_social.post (user_id);

create table cyber_social.post_comment
(
    id          bigint                             not null comment '雪花ID'
        primary key,
    post_id     bigint                             not null comment '帖子ID',
    user_id     bigint                             not null comment '评论用户ID',
    content     varchar(300)                       not null comment '评论内容',
    create_time datetime default CURRENT_TIMESTAMP not null,
    deleted     tinyint  default 0                 not null comment '0=正常 1=已删除'
)
    comment '帖子评论表';

create index idx_create_time
    on cyber_social.post_comment (create_time);

create index idx_post_id
    on cyber_social.post_comment (post_id);

create index idx_user_id
    on cyber_social.post_comment (user_id);

create table cyber_social.post_like
(
    id          bigint                             not null comment '雪花ID'
        primary key,
    post_id     bigint                             not null comment '帖子ID',
    user_id     bigint                             not null comment '点赞用户ID',
    create_time datetime default CURRENT_TIMESTAMP not null,
    constraint uk_post_user
        unique (post_id, user_id)
)
    comment '点赞记录表';

create table cyber_shop.product
(
    id                 bigint                                not null comment '雪花ID'
        primary key,
    name               varchar(100)                          not null comment '商品名称，如"小鱼干"',
    description        varchar(500)                          null comment '商品描述',
    icon               varchar(255)                          null comment '图标URL',
    price              decimal(10, 2)                        not null comment '单价（虚拟币）',
    stock              int         default 0                 not null comment '当前库存数量',
    status             varchar(20) default 'ON_SHELF'        not null comment 'ON_SHELF=上架 / OFF_SHELF=下架',
    daily_limit        int                                   null comment '每人每天限购数量，NULL=不限',
    create_time        datetime    default CURRENT_TIMESTAMP not null,
    update_time        datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    deleted            tinyint     default 0                 not null,
    effect_type        varchar(20)                           null comment '道具分类: FEED（食物）/PET（玩具）/BATHE（洗浴），NULL=纯装饰品',
    effect_hunger      int         default 0                 not null comment '使用后增加的饥饿值',
    effect_mood        int         default 0                 not null comment '使用后增加的心情值',
    effect_cleanliness int         default 0                 not null comment '使用后增加的清洁值'
)
    comment '道具商品表';

create index idx_status
    on cyber_shop.product (status);

create table cyber_shop.shop_order
(
    id           bigint                                not null comment '雪花ID'
        primary key,
    user_id      bigint                                not null comment '购买用户ID',
    product_id   bigint                                not null comment '商品ID',
    product_name varchar(100)                          not null comment '商品名称快照',
    quantity     int                                   not null comment '购买数量',
    unit_price   decimal(10, 2)                        not null comment '单价快照',
    total_price  decimal(10, 2)                        not null comment '总价',
    status       varchar(20) default 'PAID'            not null comment 'PAID=已支付 / CANCELLED=已取消',
    create_time  datetime    default CURRENT_TIMESTAMP not null,
    update_time  datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP
)
    comment '订单表';

create index idx_create_time
    on cyber_shop.shop_order (create_time);

create index idx_user_id
    on cyber_shop.shop_order (user_id);

create table cyber_pet.state_transition_log
(
    id           bigint                             not null comment '雪花ID'
        primary key,
    pet_id       bigint                             not null comment '宠物ID',
    from_state   varchar(20)                        not null comment '原状态',
    to_state     varchar(20)                        not null comment '新状态',
    trigger_type varchar(20)                        not null comment '触发方式: DECAY/INTERACT/TIMEOUT/MANUAL',
    description  varchar(255)                       null comment '补充描述',
    create_time  datetime default CURRENT_TIMESTAMP not null
)
    comment '状态流转日志表';

create index idx_create_time
    on cyber_pet.state_transition_log (create_time);

create index idx_pet_id
    on cyber_pet.state_transition_log (pet_id);

create table cyber_shop.user_backpack
(
    id             bigint                             not null comment '雪花ID'
        primary key,
    user_id        bigint                             not null comment '用户ID',
    product_id     bigint                             not null comment '商品ID',
    quantity       int      default 0                 not null comment '当前持有数量',
    total_acquired int      default 0                 not null comment '累计获得数量',
    total_consumed int      default 0                 not null comment '累计消耗数量',
    create_time    datetime default CURRENT_TIMESTAMP not null,
    update_time    datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    constraint uk_user_product
        unique (user_id, product_id)
)
    comment '用户背包表';

create index idx_user_id
    on cyber_shop.user_backpack (user_id);

create table cyber_user.user_info
(
    id          bigint                                not null comment '雪花ID'
        primary key,
    username    varchar(50)                           not null,
    password    varchar(255)                          not null comment 'BCrypt 加密',
    nickname    varchar(50)                           null,
    avatar      varchar(255)                          null,
    create_time datetime    default CURRENT_TIMESTAMP not null,
    deleted     tinyint     default 0                 not null,
    role        varchar(20) default 'USER'            not null comment '角色: USER=普通用户 / ADMIN=管理员',
    constraint username
        unique (username)
);

create table cyber_shop.user_wallet
(
    id            bigint                                   not null comment '雪花ID'
        primary key,
    user_id       bigint                                   not null comment '用户ID，一对一',
    balance       decimal(10, 2) default 0.00              not null comment '当前余额',
    total_income  decimal(10, 2) default 0.00              not null comment '累计收入',
    total_expense decimal(10, 2) default 0.00              not null comment '累计支出',
    create_time   datetime       default CURRENT_TIMESTAMP not null,
    update_time   datetime       default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    constraint user_id
        unique (user_id)
)
    comment '用户钱包表';

create index idx_user_id
    on cyber_shop.user_wallet (user_id);

create table cyber_analytics.weekly_report
(
    id            bigint                             not null comment '雪花ID'
        primary key,
    user_id       bigint                             not null comment '用户ID',
    pet_id        bigint                             not null comment '宠物ID',
    report_week   varchar(20)                        not null comment '报告周，如 2026-W28',
    total_feeds   int      default 0                 not null comment '本周喂食次数',
    total_plays   int      default 0                 not null comment '本周玩耍次数',
    total_baths   int      default 0                 not null comment '本周洗澡次数',
    avg_mood      decimal(5, 2)                      null comment '本周平均心情值',
    intimacy_gain int      default 0                 not null comment '本周亲密度增长',
    report_json   text                               null comment '详细报告JSON',
    create_time   datetime default CURRENT_TIMESTAMP not null,
    update_time   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '最近更新时间'
)
    comment '宠物周报表';

create index idx_user_pet_week
    on cyber_analytics.weekly_report (user_id, pet_id, report_week);


