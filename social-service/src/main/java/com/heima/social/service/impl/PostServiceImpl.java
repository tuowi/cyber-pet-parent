package com.heima.social.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.heima.api.client.AuthClient;
import com.heima.api.client.PetClient;
import com.heima.api.constant.RedisKeyPrefix;
import com.heima.api.domain.dto.PageDTO;
import com.heima.api.domain.dto.Result;
import com.heima.api.domain.po.PetInfo;
import com.heima.api.domain.po.UserInfo;
import com.heima.api.domain.vo.PetVO;
import com.heima.api.enums.BizErrorCode;
import com.heima.api.enums.Species;
import com.heima.common.util.CollUtils;
import com.heima.common.util.SnowflakeIdGenerator;
import com.heima.common.util.UserContextHolder;
import com.heima.social.domain.dto.PostCreateDTO;
import com.heima.social.domain.po.Post;
import com.heima.social.domain.po.PostComment;
import com.heima.social.domain.po.PostLike;
import com.heima.social.domain.query.PageQuery;
import com.heima.social.domain.vo.*;
import com.heima.social.mapper.PostMapper;
import com.heima.social.service.IPostCommentService;
import com.heima.social.service.IPostLikeService;
import com.heima.social.service.IPostService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.social.utils.PostScoreUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringEscapeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 * 朋友圈帖子表 服务实现类
 * </p>
 *
 * @author lucy
 * @since 2026-07-21
 */
@Service
@RequiredArgsConstructor
public class PostServiceImpl extends ServiceImpl<PostMapper, Post> implements IPostService {


    private final PetClient petClient;

    private final SnowflakeIdGenerator snowflakeIdGenerator;

    private final StringRedisTemplate stringRedisTemplate;

    private final PostScoreUtil postScoreUtil;

    private final AuthClient authClient;

    @Autowired
    @Lazy
    private IPostLikeService postLikeService;

    @Autowired
    @Lazy
    private IPostCommentService postCommentService;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    /**
     * 发布朋友圈
     *
     * @param postCreateDTO
     * @return
     */
    @Override
    public Result<PostCreateVO> create(PostCreateDTO postCreateDTO) {
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), BizErrorCode.FORBIDDEN.getMessage());
        }
        // 1.校验 petId 属于当前用户：查询 pet_info 确认 user_id 匹配（可通过 Feign 调用 pet-service 或直接查 DB）
        PetVO myPet = petClient.queryPetByUserId(userId)
                .stream()
                .filter(petVO -> petVO.getPetId().equals(postCreateDTO.getPetId()))
                .findFirst()
                .orElse(null);

        if (myPet == null) {
            return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "这不是你的宠物");
        }
        // 2.校验 content 长度（1-500 字符），对内容做 XSS 过滤（HTML 转义）
        // 2.1.长度校验
        String content = postCreateDTO.getContent();
        if (content == null || content.isEmpty() || content.length() > 500) {
            return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "帖子内容长度不符合要求");
        }
        // 2.2.XSS过滤（HTML转义）
//        String safeContent = HtmlUtils.htmlEscape(content);
        String safeContent = StringEscapeUtils.escapeHtml4(content); //更全面
        // 3.校验 imageUrl 若不为空则为合法 URL 格式
        if (postCreateDTO.getImageUrl() == null || postCreateDTO.getImageUrl().trim().isEmpty()) {
            return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "图像不规范");
        }
        // 4.生成 Snowflake post ID
        long postId = snowflakeIdGenerator.nextId();
        // 5.INSERT INTO social_post (id, user_id, pet_id, content, image_url, like_count=0, comment_count=0)
        LocalDateTime now = LocalDateTime.now();
        // 5.1.构建条件
        Post post = Post.builder()
                .id(postId)
                .userId(userId)
                .petId(postCreateDTO.getPetId())
                .content(safeContent)
                .imageUrl(postCreateDTO.getImageUrl())
                .likeCount(0)
                .commentCount(0)
                .createTime(now)
                .updateTime(now)
                .deleted(0)
                .build();
        // 5.2.存入数据库
        save(post);
        // 6.帖子发布后可加入 Redis ZSet social:post:hot 初始化热度分
        double score = postScoreUtil.queryScore(0, 0, now);
        stringRedisTemplate.opsForZSet().add(RedisKeyPrefix.SOCIAL_FEED_HOT_KEY, String.valueOf(postId), score);
        // TODO 7.可选：Publish 到 stream:social:new-post 通知在线用户有新帖子
        // 8.返回 PostVO
        PostCreateVO createVO = PostCreateVO.builder()
                .postId(postId)
                .petId(postCreateDTO.getPetId())
                .petName(myPet.getPetName())
                .content(safeContent)
                .imageUrl(postCreateDTO.getImageUrl())
                .likeCount(0)
                .commentCount(0)
                .createTime(now)
                .build();
        return Result.ok(createVO);
    }

    /**
     * 热门帖子列表
     *
     * @param page
     * @param size
     * @return
     */
    @Override
    public Result<PageDTO<PostVO>> hotPageList(Integer page, Integer size) {
        // 检验用户是否登录
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "用户未登录");
        }
        // 1.解析分页参数（page, size）
        PageQuery pageQuery = new PageQuery();
        pageQuery.setPageNo(page == null ? 1 : page);
        pageQuery.setPageSize(size == null ? 10 : size);
        // 2.从 Redis ZSet social:post:hot 中获取排行：ZREVRANGE social:post:hot {start} {end} WITHSCORES，按热度分降序
        // 2.1.得到起始和结尾索引
        long start = (pageQuery.getPageNo() - 1) * pageQuery.getPageSize().longValue();
        long end = pageQuery.getPageNo() * pageQuery.getPageSize().longValue() - 1;
        // 2.2.开始检索
        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet().reverseRangeWithScores(RedisKeyPrefix.SOCIAL_FEED_HOT_KEY, start, end);
        // 全局定义
        List<Post> postList;
        long total = 0;
        Map<Long, Double> scoreMap = new HashMap<>();
        // 2.3.非空校验
        if (tuples == null || tuples.isEmpty()) {
            // 3.若 Redis ZSet 为空或未命中，回退查询 DB：SELECT * FROM social_post WHERE deleted=0 ORDER BY (like_count*2 + comment_count*5 - TIMESTAMPDIFF(HOUR, create_time, NOW())*0.5) DESC LIMIT ? OFFSET ?
            Page<Post> postPage = queryHotPostFromDB(pageQuery.getPageNo(), pageQuery.getPageSize());
            total = postPage.getTotal();
            postList = postPage.getRecords();

            for (Post p : postList) {
                double score = postScoreUtil.queryScore(p.getLikeCount(), p.getCommentCount(), p.getCreateTime());
                scoreMap.put(p.getId(), score);
            }
        } else {
            // 2.4.redis命中，获取热门帖子id列表
            List<Long> ids = new ArrayList<>(tuples.size());
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                ids.add(Long.valueOf(tuple.getValue()));

                Double score = tuple.getScore();
                scoreMap.put(Long.valueOf(tuple.getValue()), score);
            }

            // 获取帖子总数目
            total = stringRedisTemplate.opsForZSet().size(RedisKeyPrefix.SOCIAL_FEED_HOT_KEY);

            // 2.5.根据id顺序查询数据库
            String idStr = StrUtil.join(",", ids);
            postList = query()
                    .in("id", ids)
                    .last("ORDER BY FIELD(id," + idStr + ")")
                    .list();
        }

        // 再次验空，检验数据库是否有帖子
        if (CollUtils.isEmpty(postList)) {
            return Result.ok(PageDTO.empty(0L, pageQuery.getPageNo().longValue(), pageQuery.getPageSize().longValue()));
        }

        // 4.遍历每篇帖子，查询当前用户是否已点赞：SISMEMBER post:likes:{postId} {userId}
        // 类型转换
        List<PostVO> vos = postList.stream().map(post -> {
            // 1.获取发帖宠物信息
            PetInfo basicPetInfo = petClient.getBasicPetInfo(post.getPetId());
            // 2.获取发帖用户昵称
            String nickname = authClient.queryUserById(basicPetInfo.getUserId()).getNickname();
            // 3.获取登录者是否点赞
            Boolean liked = isPostLiked(post.getId());
            // 构建返回单体
            PostVO build = PostVO.builder()
                    .postId(post.getId())
                    .userId(post.getUserId())
                    .nickname(nickname)
                    .petId(post.getPetId())
                    .petName(basicPetInfo.getPetName())
                    .species(basicPetInfo.getSpecies())
                    .content(post.getContent())
                    .imageUrl(post.getImageUrl())
                    .likeCount(post.getLikeCount())
                    .commentCount(post.getCommentCount())
                    .liked(liked)
                    .heatScore(scoreMap.get(post.getId()))
                    .createTime(post.getCreateTime())
                    .build();

            return build;
        }).toList();
        // 组装
        PageDTO<PostVO> pageDTO = new PageDTO<>(total, Long.valueOf(pageQuery.getPageNo()), Long.valueOf(pageQuery.getPageSize()), vos);
        return Result.ok(pageDTO);
    }

    /**
     * 从 DB 查询分页数据（按热度公式排序）
     */
    private Page<Post> queryHotPostFromDB(Integer page, Integer size) {
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Post::getDeleted, 0)
                .ge(Post::getCreateTime, LocalDateTime.now().minusDays(7))
                .last("ORDER BY like_count*2 + comment_count*5 - TIMESTAMPDIFF(HOUR, create_time, NOW())*0.5 DESC");
        // 分页
        Page<Post> pageParam = new Page<>(page + 1, size); // MyBatis-Plus 页码从1开始
        return page(pageParam, wrapper);
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
        if (!exist) {
            // 查询数据库
            like = postLikeService.lambdaQuery()
                    .eq(PostLike::getPostId, postId)
                    .eq(PostLike::getUserId, userId)
                    .one();
        }
        return !exist && like == null ? Boolean.FALSE : Boolean.TRUE;
    }

    /**
     * 最新帖子列表
     *
     * @param page
     * @param size
     * @return
     */
    @Override
    public Result<PageDTO<PostVO>> timelinePageList(Integer page, Integer size) {
        // 检验用户是否登录
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "用户未登录");
        }
        // 1.解析分页参数（page, size）
        PageQuery pageQuery = new PageQuery();
        pageQuery.setPageNo(page == null ? 1 : page);
        pageQuery.setPageSize(size == null ? 10 : size);

        //  检查redis缓存是否存在
        String timelineKey = RedisKeyPrefix.SOCIAL_TIMELINE_KEY + pageQuery.getPageNo() + ":" + pageQuery.getPageSize();
        String cachedJson = stringRedisTemplate.opsForValue().get(timelineKey);

        if (StringUtils.hasText(cachedJson)) {
            // 缓存命中！直接反序列化返回，完全跳过下面的 DB 查询
            try {
                PageDTO<PostVO> cachedResult = objectMapper.readValue(cachedJson, new TypeReference<PageDTO<PostVO>>() {
                });
                return Result.ok(cachedResult);
            } catch (JsonProcessingException e) {
                // 如果 JSON 解析失败，就删掉脏缓存，继续走 DB 查询
                stringRedisTemplate.delete(timelineKey);
            }
        }

        // 2.查询 DB：SELECT * FROM social_post WHERE deleted=0 ORDER BY create_time DESC LIMIT ? OFFSET ?
        // 2.1.构造分页条件
        Page<Post> sort = pageQuery.toMpPageDefaultSortByCreateTimeDesc();
        // 2.2.开始分页查询
        lambdaQuery().page(sort);

        List<PostVO> postVOS = new ArrayList<>(sort.getRecords().size());
        // 3.遍历每篇帖子，批量查询当前用户是否已点赞：SISMEMBER post:likes:{postId} {userId}
        for (Post record : sort.getRecords()) {
            // 获取发布者宠物信息
            PetInfo basicPetInfo = petClient.getBasicPetInfo(record.getPetId());
            // 获取发布者昵称
            String nickname = authClient.queryUserById(basicPetInfo.getUserId()).getNickname();
            // 获取该用户是否点赞
            Boolean liked = isPostLiked(record.getId());
            // 构建返回单体
            PostVO build = PostVO.builder()
                    .postId(record.getId())
                    .userId(record.getUserId())
                    .nickname(nickname)
                    .petId(record.getPetId())
                    .petName(basicPetInfo.getPetName())
                    .species(basicPetInfo.getSpecies())
                    .content(record.getContent())
                    .imageUrl(record.getImageUrl())
                    .likeCount(record.getLikeCount())
                    .commentCount(record.getCommentCount())
                    .liked(liked)
                    .heatScore(null)
                    .createTime(record.getCreateTime())
                    .build();

            postVOS.add(build);
        }
        // 4.组装 PageDTO 并返回
        PageDTO<PostVO> pageDTO = PageDTO.of(sort, postVOS);

        // 5.缓存 key：social:timeline:{page}:{size} 30s
        // 评论数和点赞数变化有点误差没事
        try {
            String jsonStr = objectMapper.writeValueAsString(pageDTO);
            stringRedisTemplate.opsForValue().set(timelineKey, jsonStr, RedisKeyPrefix.SOCIAL_TIMELINE_TTL, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        return Result.ok(pageDTO);
    }

    /**
     * 帖子详细
     * likeCount <= 5 时展示全部点赞人头像和名字；likeCount > 5 时展示前 5 个头像 + "等 N 人赞了"
     * 5.7 点赞 / 5.8 取消点赞 → DEL social:post:detail:{postId}
     * 5.11 发表评论 / 5.12 删除评论 → DEL social:post:detail:{postId}
     * 5.15 删除帖子 → DEL social:post:detail:{postId}
     *
     * @param postId
     * @param commentPage
     * @param commentSize
     * @return
     */
    @Override
    public Result<PostDetailVO> detail(Long postId, Integer commentPage, Integer commentSize) {
        // 检验用户是否登录
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "用户未登录");
        }

        // 查询缓存是否存在
        String detailKey = RedisKeyPrefix.SOCIAL_POST_DETAIL_KEY + postId;
        String cachedJson = stringRedisTemplate.opsForValue().get(detailKey);

        if (StringUtils.hasText(cachedJson)) {
            // 缓存命中！直接反序列化返回，完全跳过下面的 DB 查询
            try {
                PostDetailVO cachedResult = objectMapper.readValue(cachedJson, new TypeReference<PostDetailVO>() {
                });
                return Result.ok(cachedResult);
            } catch (JsonProcessingException e) {
                // 如果 JSON 解析失败，就删掉脏缓存，继续走 DB 查询
                stringRedisTemplate.delete(detailKey);
            }
        }
        // 1.查询 social_post by id，若为 null 或 deleted=1 返回 404
        Post post = getById(postId);
        if (post == null) {
            return Result.fail(BizErrorCode.NOT_FOUND.getCode(), "帖子不存在");
        }
        // 2.查询帖子作者信息（nickname, avatar）和宠物信息（petName, species）—— JOIN user_info 和 pet_info 表，或通过 Feign 调用
        // 2.1.查询作者信息
        Long authorUserId = post.getUserId();
        UserInfo userInfo = authClient.queryUserById(authorUserId);
        // 2.2.帖子作者若已注销（user 表 soft-delete），nickname 显示 "已注销用户"，avatar 显示默认头像
        String authorNickname = "";
        String authorAvatar = "";
        // TODO 默认头像需上传
        if (userInfo == null) {
            authorNickname = "已注销用户";
            authorAvatar = "默认头像";
        } else {
            authorNickname = userInfo.getNickname();
            authorAvatar = userInfo.getAvatar();
        }
        // 2.3.查询宠物信息
        PetInfo petInfo = petClient.getBasicPetInfo(post.getPetId());
        // 2.4.若发帖宠物已解除领养（pet_info.deleted=1），petName 显示 "已离开的宠物"
        String authorPetName = "";
        Species authorPetSpecies = null;
        if (petInfo == null) {
            authorPetName = "已离开的宠物";
            authorPetSpecies = null;
        } else {
            authorPetName = petInfo.getPetName();
            authorPetSpecies = petInfo.getSpecies();
        }
        // 3.查询当前用户是否已点赞：SISMEMBER post:likes:{postId} {userId}；若 Redis 未命中则回退查 post_like 表
        Boolean liked = isPostLiked(postId);
        // 4.查询最早的 5 个点赞人
        // 如果帖子没有点赞（likeCount=0），recentLikers 返回空数组 []
        List<RecentLiker> recentLikers;
        // 4.1.提取点赞人id
        List<Long> ids = postLikeService.lambdaQuery()
                .eq(PostLike::getPostId, postId)
                .orderByAsc(PostLike::getCreateTime)
                .last("limit 5")  // 替换 .limit(5)
                .list()
                .stream()
                .map(PostLike::getUserId)
                .toList();
        // 验空
        if (ids.isEmpty()) {
            recentLikers = Collections.emptyList();
        } else {
            // 4.2.得到点赞人昵称
            List<UserInfo> userInfos = authClient.queryUserByIds(ids);
            Map<Long, String> nicknameMap = userInfos.stream()
                    .collect(Collectors.toMap(UserInfo::getId, UserInfo::getNickname));
            // 4.3.组合列表
            recentLikers = new ArrayList<>(ids.size());
            for (Long id : ids) {
                recentLikers.add(new RecentLiker(id, nicknameMap.get(id)));
            }
        }
        // 5.分页查询评论（默认 page=1, size=10）：SELECT * FROM post_comment WHERE post_id=? AND deleted=0 ORDER BY create_time ASC LIMIT ? OFFSET ?，统计总数：SELECT COUNT(*) FROM post_comment WHERE post_id=? AND deleted=0，批量查 user 表获取 nickname
        // 如果帖子没有评论（commentCount=0），comments.records 返回空数组 []，comments.total 返回 0
        PageDTO<CommentVO> dto = postCommentService.listPageComment(postId, commentPage == null ? 1 : commentPage, commentSize == null ? 10 : commentSize).getData();
        if (dto == null) {
            return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "评论列表获取失败");
        }
        // 6.组装 PostDetailVO（heatScore 固定为 null），写入 Redis 缓存：SETEX social:post:detail:{postId} 60 {json}
        // 6.1.组装返回
        PostDetailVO build = PostDetailVO.builder()
                .postId(postId)
                .userId(authorUserId)
                .nickname(authorNickname)
                .avatar(authorAvatar)
                .petId(post.getPetId())
                .petName(authorPetName)
                .species(authorPetSpecies)
                .speciesName(authorPetSpecies == null ? null : authorPetSpecies.getDesc())
                .content(post.getContent())
                .imageUrl(post.getImageUrl())
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .liked(liked)
                .heatScore(null)
                .createTime(post.getCreateTime())
                .recentLikers(recentLikers)
                .comments(dto)
                .build();
        // 6.2.存入缓存
        try {
            String jsonStr = objectMapper.writeValueAsString(build);
            stringRedisTemplate.opsForValue().set(detailKey, jsonStr, RedisKeyPrefix.SOCIAL_POST_DETAIL_TTL, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        // 6.3.返回
        return Result.ok(build);
    }

    /**
     * 删除帖子
     *
     * @param postId
     * @return
     */
    @Override
    public Result<Void> deletePost(Long postId) {
        // 检验用户是否登录
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "用户未登录");
        }
        // 1.查询 social_post by id，若为 null 或 deleted=1 返回 404
        Post post = getById(postId);
        if (post == null) {
            return Result.fail(BizErrorCode.NOT_FOUND.getCode(), "帖子不存在");
        }
        // 2.校验 post.user_id == 当前用户，不匹配返回 403 "只能删除自己的帖子"
        if (!post.getUserId().equals(userId)) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "只能删除自己的帖子");
        }
        // 3.开启事务 @Transactional
        deletePostAndLikeAndComment(postId);
        // 4.事务提交后，清理 Redis（best-effort，失败不阻塞，日志记录即可）
        // 4.1.DEL post:likes:{postId} — 清理点赞 Set
        String likeKey = RedisKeyPrefix.POST_LIKES_KEY + postId;
        stringRedisTemplate.delete(likeKey);
        // 4.2.ZREM social:post:hot {postId} — 从热度榜移除
        stringRedisTemplate.opsForZSet().remove(RedisKeyPrefix.SOCIAL_FEED_HOT_KEY, String.valueOf(postId));
        // 4.3.DEL social:post:detail:{postId} — 清理详情缓存
        String detailKey = RedisKeyPrefix.SOCIAL_POST_DETAIL_KEY + postId;
        stringRedisTemplate.delete(detailKey);
        // 4.4.DEL social:timeline:* — 清理时间线缓存（使用 SCAN + DEL 或维护 cache key set）
        // 方法一：使用 keys（仅限测试环境或数据量极小的情况）
        String timelineKey = RedisKeyPrefix.SOCIAL_TIMELINE_KEY + "*";
        Set<String> keys = stringRedisTemplate.keys(timelineKey);
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
        // TODO 方法二：使用 scan 迭代（生产环境推荐）
        // TODO 5.可选：通过 WebSocket /topic/feed/hot 推送帖子删除通知，告知在线用户该帖已不可见
        // TODO 6.可选：通过 WebSocket /queue/post-delete 推送给曾评论该帖的用户，通知 "你评论的帖子已被作者删除"（见下方推送消息格式）
        // 7.返回成功
        return Result.ok();
    }

    // 删除帖子、评论、点赞
    @Transactional
    public void deletePostAndLikeAndComment(Long postId) {
        // 1.软删帖子：UPDATE social_post SET deleted=1, delete_time=NOW() WHERE id=? AND deleted=0
        lambdaUpdate()
                .set(Post::getDeleted, 1)
                .set(Post::getUpdateTime, LocalDateTime.now())
                .eq(Post::getId, postId)
                .eq(Post::getDeleted, 0)
                .update();
        // 2.软删所有评论：UPDATE post_comment SET deleted=1, delete_time=NOW() WHERE post_id=? AND deleted=0
        // 没有更新时间，配置内已经设置软删除
        postCommentService.removeById(postId);
        // 3.物理删除所有点赞：DELETE FROM post_like WHERE post_id=?
        postLikeService.removeById(postId);
    }

    /**
     * 我的帖子
     *
     * @param page
     * @param size
     * @return
     */
    @Override
    public Result<PageDTO<PostVO>> myPost(Integer page, Integer size) {
        // 1.从 UserContextHolder 获取当前 userId
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "用户未登录");
        }
        // 2.分页查询：SELECT * FROM social_post WHERE user_id=? AND deleted=0 ORDER BY create_time DESC LIMIT ? OFFSET ?
        // 2.1.构建分页条件
        PageQuery pageQuery = new PageQuery();
        pageQuery.setPageNo(page == null ? 1 : page);
        pageQuery.setPageSize(size == null ? 10 : size);
        Page<Post> sort = pageQuery.toMpPageDefaultSortByCreateTimeDesc();
        // 2.2.开始分页
        lambdaQuery()
                .eq(Post::getUserId, userId)
                .page(sort);
        // 3.遍历每篇帖子，查询当前用户是否已点赞：SISMEMBER post:likes:{postId} {userId}
        List<Post> records = sort.getRecords();
        // 帖子id
        List<Long> ids = records.stream().map(Post::getId).toList();
        // 宠物id
        List<Long> petIds = records.stream().map(Post::getPetId).toList();
        List<PetInfo> petInfos = petClient.queryPetByIds(petIds);
        Map<Long, PetInfo> petMap = petInfos.stream()
                .collect(Collectors.toMap(PetInfo::getId, Function.identity()));
        // 用户Id
        List<Long> userIds = records.stream().map(Post::getUserId).toList();
        List<UserInfo> userInfos = authClient.queryUserByIds(userIds);
        Map<Long, UserInfo> userMap = userInfos.stream()
                .collect(Collectors.toMap(UserInfo::getId, Function.identity()));
        // 全局定义
        List<PostVO> postVOS = new ArrayList<>(ids.size());

        for (Post p : records) {
            Boolean liked = isPostLiked(p.getId());

            PostVO build = PostVO.builder()
                    .postId(p.getId())
                    .userId(p.getUserId())
                    .nickname(userMap.get(p.getUserId()).getNickname())
                    .petId(p.getPetId())
                    .petName(petMap.get(p.getPetId()).getPetName())
                    .species(petMap.get(p.getPetId()).getSpecies())
                    .content(p.getContent())
                    .imageUrl(p.getImageUrl())
                    .likeCount(p.getLikeCount())
                    .commentCount(p.getCommentCount())
                    .liked(liked)
                    .heatScore(null)
                    .createTime(p.getCreateTime())
                    .build();

            postVOS.add(build);
        }
        // 4.返回 PageDTO
        PageDTO<PostVO> pageDTO = PageDTO.of(sort, postVOS);
        return Result.ok(pageDTO);
    }

    /**
     * 用户帖子列表
     * @param userId
     * @param page
     * @param size
     * @return
     */
    @Override
    public Result<PageDTO<PostVO>> userPost(Long userId, Integer page, Integer size) {
        // 从 UserContextHolder 获取当前 userId
        Long currentUserId = UserContextHolder.getUserId();
        if (currentUserId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "用户未登录");
        }
        // 1.查询目标用户是否存在且未注销：SELECT id, nickname, status FROM user_info WHERE id=?，若不存在或 status='DELETED' 返回 404 "用户不存在"
        UserInfo userInfo = authClient.queryUserById(userId);
        if (userInfo==null){
            return Result.fail(BizErrorCode.NOT_FOUND.getCode(),  "用户不存在");
        }
        // 2.分页查询：SELECT * FROM social_post WHERE user_id=? AND deleted=0 ORDER BY create_time DESC LIMIT ? OFFSET ?
        // 2.1.构建分页条件
        PageQuery pageQuery = new PageQuery();
        pageQuery.setPageNo(page == null ? 1 : page);
        pageQuery.setPageSize(size == null ? 10 : size);
        Page<Post> sort = pageQuery.toMpPageDefaultSortByCreateTimeDesc();
        // 2.2.开始分页
        lambdaQuery()
                .eq(Post::getUserId, userId)
                .page(sort);
        // 3.遍历每篇帖子，查询当前登录用户是否已点赞：SISMEMBER post:likes:{postId} {currentUserId}
        List<Post> records = sort.getRecords();
        // 帖子id
        List<Long> ids = records.stream().map(Post::getId).toList();
        // 宠物id
        List<Long> petIds = records.stream().map(Post::getPetId).toList();
        List<PetInfo> petInfos = petClient.queryPetByIds(petIds);
        Map<Long, PetInfo> petMap = petInfos.stream()
                .collect(Collectors.toMap(PetInfo::getId, Function.identity()));
        // 用户Id
        List<Long> userIds = records.stream().map(Post::getUserId).toList();
        List<UserInfo> userInfos = authClient.queryUserByIds(userIds);
        Map<Long, UserInfo> userMap = userInfos.stream()
                .collect(Collectors.toMap(UserInfo::getId, Function.identity()));
        // 全局定义
        List<PostVO> postVOS = new ArrayList<>(ids.size());

        for (Post p : records) {
            Boolean liked = isPostLiked(p.getId());

            PostVO build = PostVO.builder()
                    .postId(p.getId())
                    .userId(p.getUserId())
                    .nickname(userMap.get(p.getUserId()).getNickname())
                    .petId(p.getPetId())
                    .petName(petMap.get(p.getPetId()).getPetName())
                    .species(petMap.get(p.getPetId()).getSpecies())
                    .content(p.getContent())
                    .imageUrl(p.getImageUrl())
                    .likeCount(p.getLikeCount())
                    .commentCount(p.getCommentCount())
                    .liked(liked)
                    .heatScore(null)
                    .createTime(p.getCreateTime())
                    .build();

            postVOS.add(build);
        }
        // 4.返回 PageDTO
        PageDTO<PostVO> pageDTO = PageDTO.of(sort, postVOS);
        return Result.ok(pageDTO);
    }
}
