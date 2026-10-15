package com.heima.social.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heima.api.constant.RedisKeyPrefix;
import com.heima.social.domain.po.Post;
import com.heima.social.domain.po.PostLike;
import com.heima.social.mapper.PostLikeMapper;
import com.heima.social.mapper.PostMapper;
import com.heima.social.utils.PostScoreUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 帖子热度分定时重算——每 5 分钟更新 Redis ZSet
 *
 * <h3>热度公式</h3>
 * <pre>
 * heatScore = likeCount × 2 + commentCount × 5 - 发布至今小时数 × 0.5
 * </pre>
 * 点赞权重 2，评论权重 5（评论互动更深），时间衰减让旧帖自然下沉。
 *
 * <h3>Redis 数据结构</h3>
 * <pre>
 * Key:   social:feed:hot     (ZSet)
 * Score: 热度分
 * Member: postId（字符串）
 * </pre>
 *
 * <h3>使用方</h3>
 * <ul>
 *   <li>5.5 热门帖子列表 → ZREVRANGE social:feed:hot</li>
 *   <li>5.7 点赞 → ZINCRBY social:feed:hot 2 postId</li>
 *   <li>5.8 取消点赞 → ZINCRBY social:feed:hot -2 postId</li>
 *   <li>本定时任务 → 全量重算（兜底修正）</li>
 * </ul>
 *
 * <h3>为什么点赞时实时更新了还要定时全量重算？</h3>
 * 时间衰减因子（-小时数×0.5）是随时间持续变化的，
 * 不可能每次点赞都重算所有帖子。定时任务补上时间衰减。
 *
 * @author lucy
 * @since 2026-07-24
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeedHotScoreScheduler {

    private final PostMapper postMapper;

    private final PostLikeMapper postLikeMapper;

    private final StringRedisTemplate stringRedisTemplate;

    private final PostScoreUtil postScoreUtil;

    /**
     * 每 5 分钟全量重算一次热度分，并清理已删除帖子的残留点赞数据
     */
    @Scheduled(fixedDelay = 300_000)
    public void recalculateHotScores() {
        try {
            // 1. 查询所有未删除的帖子
            List<Post> posts = postMapper.selectList(
                    new LambdaQueryWrapper<Post>()
                            .eq(Post::getDeleted, 0)
            );

            if (posts.isEmpty()) {
                // 没有帖子就清空 ZSet（防止旧数据残留）
                stringRedisTemplate.delete(RedisKeyPrefix.SOCIAL_FEED_HOT_KEY);
                // 清空所有点赞数据（因为没有帖子需要保留）
                cleanOrphanedLikeData(posts);
                return;
            }

            // 2. 逐条计算热度分，先暂存到临时 ZSet
            //    用临时 key 避免计算过程中用户看到中间状态
            String tempKey = RedisKeyPrefix.SOCIAL_FEED_HOT_KEY + ":temp";
            stringRedisTemplate.delete(tempKey); // 清理上次的临时数据

            for (Post post : posts) {
                double score = postScoreUtil
                        .queryScore(post.getLikeCount(), post.getCommentCount(), post.getCreateTime());

                stringRedisTemplate.opsForZSet()
                        .add(tempKey, String.valueOf(post.getId()), score);
            }

            // 3. 原子替换：删除旧 key → 重命名临时 key
            //    如果 ZSet 为空也要执行 rename（表示没有上榜帖子）
            stringRedisTemplate.delete(RedisKeyPrefix.SOCIAL_FEED_HOT_KEY);
            Long tempSize = stringRedisTemplate.opsForZSet().size(tempKey);
            if (tempSize != null && tempSize > 0) {
                stringRedisTemplate.rename(tempKey, RedisKeyPrefix.SOCIAL_FEED_HOT_KEY);
            } else {
                // rename 在 source key 不存在时会抛异常
                stringRedisTemplate.delete(tempKey);
            }

            log.info("热度分重算完成: 上榜帖子 {} 篇", tempSize != null ? tempSize : 0);

            // 4. 清理已删除帖子的点赞数据（Redis Set + DB post_like 表）
            cleanOrphanedLikeData(posts);
        } catch (Exception e) {
            log.error("热度分重算失败", e);
        }
    }

    /**
     * 清理已删除（或不存在）帖子的点赞残留数据。
     *
     * <h3>清理逻辑</h3>
     * <ol>
     *   <li>扫描 Redis 中所有 {@code post:likes:*} key，提取 postId</li>
     *   <li>与当前有效帖子集合（deleted=0）比对，找出孤儿 postId</li>
     *   <li>删除 Redis {@code post:likes:{postId}} key</li>
     *   <li>删除 DB {@code post_like} 表中对应记录</li>
     * </ol>
     *
     * <h3>触发场景</h3>
     * <ul>
     *   <li>帖子被软删除（deleted=1）后，其点赞数据不再有意义</li>
     *   <li>帖子被物理删除后，Redis 中仍残留 {@code post:likes:*} key</li>
     * </ul>
     *
     * @param validPosts 当前所有有效帖子（deleted=0）
     */
    private void cleanOrphanedLikeData(List<Post> validPosts) {
        try {
            // 当前有效帖子 ID 集合
            Set<Long> validPostIds = validPosts.stream()
                    .map(Post::getId)
                    .collect(Collectors.toSet());

            // 扫描 Redis 中所有 post:likes:* key
            Set<String> likeKeys = stringRedisTemplate.keys(RedisKeyPrefix.POST_LIKES_KEY + "*");
            if (likeKeys == null || likeKeys.isEmpty()) {
                return;
            }

            // 找出不在有效集合中的 postId（帖子已删除或不存在）
            List<Long> orphanedPostIds = likeKeys.stream()
                    .map(key -> key.replace(RedisKeyPrefix.POST_LIKES_KEY, ""))
                    .map(Long::parseLong)
                    .filter(postId -> !validPostIds.contains(postId))
                    .collect(Collectors.toList());

            if (orphanedPostIds.isEmpty()) {
                return;
            }

            // 清理 Redis Set: DEL post:likes:{postId}
            for (Long postId : orphanedPostIds) {
                stringRedisTemplate.delete(RedisKeyPrefix.POST_LIKES_KEY + postId);
            }

            // 清理 DB post_like 表: DELETE FROM post_like WHERE post_id IN (...)
            postLikeMapper.delete(
                    new LambdaQueryWrapper<PostLike>()
                            .in(PostLike::getPostId, orphanedPostIds)
            );

            log.info("清理已删除帖子的点赞数据完成: {} 篇帖子, postIds={}",
                    orphanedPostIds.size(), orphanedPostIds);
        } catch (Exception e) {
            log.error("清理孤儿点赞数据失败", e);
        }
    }
}
