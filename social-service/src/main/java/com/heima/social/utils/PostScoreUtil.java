package com.heima.social.utils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heima.api.constant.RedisKeyPrefix;
import com.heima.social.domain.po.Post;
import com.heima.social.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PostScoreUtil {
    /**
     * 超过 7 天的帖子从热榜移除
     */
    private static final int MAX_POST_DAYS = 7;

    private final PostMapper postMapper;

    // 热度公式
    public double queryScore(Integer likeCount, Integer commentCount, LocalDateTime createTime) {
        LocalDateTime now = LocalDateTime.now();
        double score = 0;

        // 查询所有未删除的帖子
        List<Post> posts = postMapper.selectList(
                new LambdaQueryWrapper<Post>()
                        .eq(Post::getDeleted, 0)
        );

        for (Post post : posts) {
            // 超过 7 天的旧帖不出现在热榜
            long daysSinceCreation = Duration.between(post.getCreateTime(), now).toDays();
            if (daysSinceCreation > MAX_POST_DAYS) {
                continue;
            }

            // 热度公式
            double hoursSinceCreation = Duration.between(post.getCreateTime(), now).toMinutes() / 60.0;
            score = post.getLikeCount() * 2.0
                    + post.getCommentCount() * 5.0
                    - hoursSinceCreation * 0.5;

            // 分数不能为负（新帖可能还没点赞，时间惩罚不应扣到负数）
            if (score < 0) {
                score = 0;
            }
        }

        return score;
    }
}
