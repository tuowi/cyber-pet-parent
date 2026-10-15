package com.heima.social.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heima.api.client.PetClient;
import com.heima.api.constant.RedisKeyPrefix;
import com.heima.api.domain.dto.Result;
import com.heima.api.domain.vo.PetVO;
import com.heima.api.enums.BizErrorCode;
import com.heima.common.util.SnowflakeIdGenerator;
import com.heima.common.util.UserContextHolder;
import com.heima.social.domain.dto.PostCreateDTO;
import com.heima.social.domain.po.Post;
import com.heima.social.domain.po.PostLike;
import com.heima.social.domain.vo.PostCreateVO;
import com.heima.social.domain.vo.PostLikedVO;
import com.heima.social.mapper.PostLikeMapper;
import com.heima.social.service.IPostLikeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.social.service.IPostService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringEscapeUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 点赞记录表 服务实现类
 * </p>
 *
 * @author lucy
 * @since 2026-07-21
 */
@Service
@RequiredArgsConstructor
public class PostLikeServiceImpl extends ServiceImpl<PostLikeMapper, PostLike> implements IPostLikeService {

    private final IPostService postService;

    private final StringRedisTemplate stringRedisTemplate;

    private final SnowflakeIdGenerator snowflakeIdGenerator;

    /**
     * 点赞帖子
     *
     * @param postId
     * @return
     */
    @Transactional
    @Override
    public Result<PostLikedVO> liked(Long postId) {
        // 校验用户是否登录
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "用户未登录");
        }
        // 1.查询 social_post by id，不存在或 deleted=1 返回 404
        Post post = postService.getById(postId);
        if (post == null) {
            return Result.fail(BizErrorCode.NOT_FOUND.getCode(), "该帖子不存在");
        }
        // 2.检查是否已点赞：SISMEMBER post:likes:{postId} {userId}，若已存在返回 400 "已经点过赞了"；若 Redis 未命中则回退查 DB：SELECT COUNT(*) FROM post_like WHERE post_id=? AND user_id=?
        Boolean liked = isPostLiked(postId);
        if (liked) {
            return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "已经点过赞了");
        }
        // 3.生成 Snowflake ID，INSERT INTO post_like (id, post_id, user_id, create_time) VALUES (?, ?, ?, NOW())
        // INSERT post_like 应在 SADD 之前：若 Redis 写入成功但 DB 插入失败，回滚困难；折中方案是先写 DB 再写 Redis，DB 失败直接报错，Redis 失败记录日志（不影响主流程）
        long likeId = snowflakeIdGenerator.nextId();
        LocalDateTime now = LocalDateTime.now();

        PostLike postLike = new PostLike();
        postLike.setId(likeId);
        postLike.setPostId(postId);
        postLike.setUserId(userId);
        postLike.setCreateTime(now);
        // 存入数据库
        save(postLike);
        // 4.SADD post:likes:{postId} {userId}
        String likesKey = RedisKeyPrefix.POST_LIKES_KEY + postId;
        stringRedisTemplate.opsForSet().add(likesKey, userId.toString());
        // 5.更新 DB：UPDATE social_post SET like_count = like_count + 1 WHERE id=?
        postService.lambdaUpdate()
                .set(Post::getLikeCount, post.getLikeCount() + 1)
                .set(Post::getUpdateTime, now)
                .eq(Post::getId, postId)
                .eq(Post::getLikeCount, post.getLikeCount()) //乐观锁
                .update();
        // 6.更新热度 ZSet：ZINCRBY social:post:hot 2 {postId}（like 权重为 2）
        stringRedisTemplate.opsForZSet().incrementScore(RedisKeyPrefix.SOCIAL_FEED_HOT_KEY, postId.toString(), 2);
        // 删除评论详细缓存
        String detailKey = RedisKeyPrefix.SOCIAL_POST_DETAIL_KEY + postId;
        stringRedisTemplate.delete(detailKey);
        // 7.返回最新的 likeCount
        PostLikedVO vo = new PostLikedVO();
        vo.setPostId(postId);
        vo.setLikeCount(post.getLikeCount() + 1);
        return Result.ok(vo);
    }

    /**
     * 判断用户是否点赞
     *
     * @param postId
     */
    private Boolean isPostLiked(Long postId) {
        // 1.获取当前用户
        Long userId = UserContextHolder.getUserId();
        // 2.判断当前登录用户是否已经点赞
        String key = RedisKeyPrefix.POST_LIKES_KEY + postId;
        Boolean exist = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
        PostLike like = null;
        if (Boolean.FALSE.equals(exist)) {
            // 查询数据库
            like = lambdaQuery()
                    .eq(PostLike::getPostId, postId)
                    .eq(PostLike::getUserId, userId)
                    .one();
        }
        return !exist && like == null ? Boolean.FALSE : Boolean.TRUE;
    }

    /**
     * 取消点赞
     *
     * @param postId
     * @return
     */
    @Transactional
    @Override
    public Result<PostLikedVO> unliked(Long postId) {
        // 校验用户是否登录
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "用户未登录");
        }
        // 1.查询 social_post by id，不存在或 deleted=1 返回 404
        Post post = postService.getById(postId);
        if (post == null) {
            return Result.fail(BizErrorCode.NOT_FOUND.getCode(), "该帖子不存在");
        }
        // 2.检查是否已点赞：SISMEMBER post:likes:{postId} {userId}，未点赞返回 400 "还没有点赞"；若 Redis 未命中则回退查 DB：SELECT COUNT(*) FROM post_like WHERE post_id=? AND user_id=?
        Boolean liked = isPostLiked(postId);
        if (!liked) {
            return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "还没有点赞");
        }
        // 3.SREM post:likes:{postId} {userId}
        String likesKey = RedisKeyPrefix.POST_LIKES_KEY + postId;
        stringRedisTemplate.opsForSet()
                .remove(likesKey, userId.toString()); // 返回移除数目
        // 4.删除 DB 记录：DELETE FROM post_like WHERE post_id=? AND user_id=?
        LambdaQueryWrapper<PostLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostLike::getPostId, postId)
                .eq(PostLike::getUserId, userId);
        remove(wrapper);
        // 5.更新 DB：UPDATE social_post SET like_count = GREATEST(like_count - 1, 0) WHERE id=?
        postService.lambdaUpdate()
                .setSql("like_count = GREATEST(like_count - 1, 0)")
                .set(Post::getUpdateTime, LocalDateTime.now())
                .eq(Post::getId, postId)
                .eq(Post::getLikeCount, post.getLikeCount())  // 乐观锁
                .update();
        // 6.更新热度 ZSet：ZINCRBY social:post:hot -2 {postId}
        Double score = stringRedisTemplate.opsForZSet().score(RedisKeyPrefix.SOCIAL_FEED_HOT_KEY, postId.toString());
        if (score >= 2) {
            stringRedisTemplate.opsForZSet().incrementScore(RedisKeyPrefix.SOCIAL_FEED_HOT_KEY, postId.toString(), -2);
        } else {
            stringRedisTemplate.opsForZSet().add(RedisKeyPrefix.SOCIAL_FEED_HOT_KEY, postId.toString(), 0);
        }
        // 删除评论详细缓存
        String detailKey = RedisKeyPrefix.SOCIAL_POST_DETAIL_KEY + postId;
        stringRedisTemplate.delete(detailKey);
        // 7.返回最新的 likeCount
        PostLikedVO postLikedVO = new PostLikedVO();
        postLikedVO.setPostId(postId);
        postLikedVO.setLikeCount(Math.max(post.getLikeCount() - 1, 0));
        return Result.ok(postLikedVO);
    }

    /**
     * 用户注销取消点赞
     *
     * @param userId
     */
    @Transactional
    @Override
    public Boolean deleteLikeByUserId(Long userId) {
        // 1.查询所有 post_like WHERE user_id=? → 物理删除
        // 1.1. 构建删除条件
        LambdaQueryWrapper<PostLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostLike::getUserId, userId);
        List<PostLike> list = list(wrapper);
        // 若无点赞记录，无需删除，直接返回成功
        if (list.isEmpty()) {
            return Boolean.TRUE;
        }
        // 1.2. 执行删除（返回 boolean 表示是否成功）
        boolean isRemoved = remove(wrapper);
        if (!isRemoved) {
            return Boolean.FALSE;
        }
        // 2.对应帖子 UPDATE like_count = GREATEST(like_count-1,0)
        for (PostLike item : list) {
            Long postId = item.getPostId();
            postService.lambdaUpdate()
                    .setSql("like_count = GREATEST(like_count - 1, 0)")
                    .set(Post::getUpdateTime, LocalDateTime.now())
                    .eq(Post::getId, postId)
                    .update();

            // 3.SREM post:likes:{postId} {userId}（逐个帖子）
            String likeKey = RedisKeyPrefix.POST_LIKES_KEY + postId;
            stringRedisTemplate.opsForSet().remove(likeKey, userId.toString());
            // 4.查询用户帖子 → ZREM social:post:hot {postId}（逐个）
            stringRedisTemplate.opsForZSet()
                    .remove(RedisKeyPrefix.SOCIAL_FEED_HOT_KEY, postId.toString());
            // 5.DEL social:post:detail:{postId}（清理帖子缓存）
            String detailKey = RedisKeyPrefix.SOCIAL_POST_DETAIL_KEY + postId;
            stringRedisTemplate.delete(detailKey);
        }

        return Boolean.TRUE;
    }
}
