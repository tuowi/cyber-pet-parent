package com.heima.social.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heima.api.client.AuthClient;
import com.heima.api.constant.RedisKeyPrefix;
import com.heima.api.domain.dto.PageDTO;
import com.heima.api.domain.dto.Result;
import com.heima.api.domain.po.PetInfo;
import com.heima.api.domain.po.UserInfo;
import com.heima.api.enums.BizErrorCode;
import com.heima.common.util.SnowflakeIdGenerator;
import com.heima.common.util.UserContextHolder;
import com.heima.social.domain.po.Post;
import com.heima.social.domain.po.PostComment;
import com.heima.social.domain.po.PostLike;
import com.heima.social.domain.query.PageQuery;
import com.heima.social.domain.vo.CommentVO;
import com.heima.social.mapper.PostCommentMapper;
import com.heima.social.service.IPostCommentService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.social.service.IPostLikeService;
import com.heima.social.service.IPostService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringEscapeUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.reactive.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 * 帖子评论表 服务实现类
 * </p>
 *
 * @author lucy
 * @since 2026-08-03
 */
@Service
@RequiredArgsConstructor
public class PostCommentServiceImpl extends ServiceImpl<PostCommentMapper, PostComment> implements IPostCommentService {

    private final SnowflakeIdGenerator snowflakeIdGenerator;

    private final IPostService postService;

    private final StringRedisTemplate stringRedisTemplate;

    private final AuthClient authClient;

    /**
     * 发表评论
     *
     * @param postId
     * @param content
     * @return
     */
    @Transactional
    @Override
    public Result<CommentVO> comment(Long postId, String content) {
        // 校验用户是否登录
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "用户未登录");
        }
        // 1.校验 postId 对应的帖子存在且 deleted=0，不存在返回 404
        Post post = postService.getById(postId);
        if (post == null) {
            return Result.fail(BizErrorCode.NOT_FOUND.getCode(), BizErrorCode.NOT_FOUND.getMessage());
        }
        // 2.校验 content 长度（1-300 字符），对内容做 XSS 过滤（HTML 转义）
        // 2.1.长度校验
        if (content == null || content.isEmpty() || content.length() > 300) {
            return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "帖子内容长度不符合要求");
        }
        // 2.2.XSS过滤（HTML转义）
//        String safeContent = HtmlUtils.htmlEscape(content);
        String safeContent = StringEscapeUtils.escapeHtml4(content); //更全面
        // 3.生成 Snowflake 评论 ID
        long commentId = snowflakeIdGenerator.nextId();
        // 4.INSERT INTO post_comment (id, post_id, user_id, content) VALUES (?, ?, ?, ?)
        LocalDateTime now = LocalDateTime.now();
        PostComment postComment = PostComment.builder()
                .id(commentId)
                .postId(postId)
                .userId(userId)
                .content(safeContent)
                .createTime(now)
                .deleted(0)
                .build();

        save(postComment);
        // 5.更新 DB：UPDATE post SET comment_count = comment_count + 1 WHERE id=?
        postService.lambdaUpdate()
                .setSql("comment_count = comment_count + 1")
                .set(Post::getUpdateTime, now)
                .eq(Post::getId, postId)
                .eq(Post::getCommentCount, post.getCommentCount())  // 乐观锁
                .update();
        // 6.更新热度 ZSet：ZINCRBY social:post:hot 5 {postId}（comment 权重为 5）
        // 事务提交后执行 Redis 和 RPC（使用 TransactionSynchronizationManager）
        stringRedisTemplate.opsForZSet()
                .incrementScore(RedisKeyPrefix.SOCIAL_FEED_HOT_KEY, String.valueOf(postId), 5);
        // TODO 7.可选：Publish 到 stream:social:new-comment 通知帖主有新评论
        // 删除评论详细缓存
        String detailKey = RedisKeyPrefix.SOCIAL_POST_DETAIL_KEY + postId;
        stringRedisTemplate.delete(detailKey);
        // 8.返回 CommentVO
        UserInfo userInfo = authClient.queryUserById(userId);

        CommentVO build = CommentVO.builder()
                .commentId(commentId)
                .postId(postId)
                .userId(userId)
                .nickname(userInfo.getNickname())
                .content(safeContent)
                .createTime(now)
                .build();
        return Result.ok(build);
    }

    /**
     * 删除评论
     *
     * @param postId
     * @param commentId
     * @return
     */
    @Override
    public Result<Void> deleteComment(Long postId, Long commentId) {
        // 校验用户是否登录
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "用户未登录");
        }
        // 1.查询 post_comment by commentId，不存在或 deleted=1 返回 404
        PostComment comment = getById(commentId);
        if (comment == null) {
            return Result.fail(BizErrorCode.NOT_FOUND.getCode(), "评论不存在或者已经删除");
        }
        // 越级检查
        if (!comment.getPostId().equals(postId)) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "不可越级删除");
        }
        // 2.查询帖子作者：SELECT user_id FROM post WHERE id=?
        // 路径中的 postId 用于 RESTful 语义，实际定位评论靠 commentId（评论和帖子的从属关系通过 DB 已有 post_id 字段保证）
        Post post = postService.getById(postId);
        Long authorUserId = post.getUserId();
        // 3.校验删除权限：comment.user_id == 当前用户 或 post.user_id == 当前用户（评论作者或帖主均可删）；不满足返回 403
        if (!(authorUserId.equals(userId) || comment.getUserId().equals(userId))) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "没有权限删除评论");
        }
        // 4.软删除：UPDATE post_comment SET deleted=1 WHERE id=? AND deleted=0
        removeById(commentId);
        // 5.更新 DB：UPDATE post SET comment_count = GREATEST(comment_count - 1, 0) WHERE id=?
        postService.lambdaUpdate()
                .setSql("comment_count = GREATEST(comment_count - 1, 0)")
                .set(Post::getUpdateTime, LocalDateTime.now())
                .eq(Post::getId, postId)
                .eq(Post::getCommentCount, post.getCommentCount())  // 乐观锁
                .update();
        // 6.更新热度 ZSet：ZINCRBY social:post:hot -5 {postId}
        Double score = stringRedisTemplate.opsForZSet()
                .incrementScore(RedisKeyPrefix.SOCIAL_FEED_HOT_KEY, postId.toString(), -5);
        // ZSet score 减少后若 <= 0 可考虑 ZREM（但保留也无妨，后续点赞/评论还会加回来）
        if (score <= 0) {
            stringRedisTemplate.opsForZSet().remove(RedisKeyPrefix.SOCIAL_FEED_HOT_KEY, postId.toString());
        }
        // 删除评论详细缓存
        String detailKey = RedisKeyPrefix.SOCIAL_POST_DETAIL_KEY + postId;
        stringRedisTemplate.delete(detailKey);
        // 7.返回成功
        return Result.ok();
    }

    /**
     * 评论列表
     *
     * @param postId
     * @param page
     * @param size
     * @return
     */
    @Override
    public Result<PageDTO<CommentVO>> listPageComment(Long postId, Integer page, Integer size) {
        // 校验用户是否登录
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "用户未登录");
        }
        // 1.校验 postId 对应的帖子存在且 deleted=0，不存在返回 404
        Post post = postService.getById(postId);
        if (post == null) {
            return Result.fail(BizErrorCode.NOT_FOUND.getCode(), "帖子不存在");
        }
        // 2.分页查询：SELECT * FROM post_comment WHERE post_id=? AND deleted=0 ORDER BY create_time ASC LIMIT ? OFFSET ?
        // 2.1.构建分页查询条件
        PageQuery pageQuery = new PageQuery();
        pageQuery.setPageNo(page == null ? 1 : page);
        pageQuery.setPageSize(size == null ? 10 : size);
        Page<PostComment> sort = pageQuery.toMpPage("create_time", true);
        // 2.2.分页查询
        lambdaQuery()
                .eq(PostComment::getPostId, postId)
                .eq(PostComment::getDeleted, 0)
                .page(sort);
        // 2.3.类型转换，提取用户id
        List<PostComment> records = sort.getRecords();
        // 若没有评论
        if (records==null){
            return Result.ok(PageDTO.empty(sort.getTotal(),sort.getCurrent(),sort.getSize()));
        }
        List<Long> ids = records.stream().map(PostComment::getUserId).toList();
        // 3.批量查询 user 表获取 nickname（IN 查询）
        List<UserInfo> userInfos = authClient.queryUserByIds(ids);
        Map<Long, String> nikenameMap = userInfos.stream()
                .collect(Collectors.toMap(UserInfo::getId, UserInfo::getNickname));
        // 4.返回 PageDTO
        List<CommentVO> list = new ArrayList<>(records.size());
        for (PostComment pc : records) {
            CommentVO vo = new CommentVO();
            // 类型转换
            BeanUtil.copyProperties(pc, vo);

            vo.setCommentId(pc.getId());
            vo.setNickname(nikenameMap.get(pc.getUserId()));

            list.add(vo);
        }

        PageDTO<CommentVO> dto = PageDTO.of(sort, list);
        return Result.ok(dto);
    }

}
