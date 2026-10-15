package com.heima.social.controller;


import com.heima.api.domain.dto.PageDTO;
import com.heima.api.domain.dto.Result;
import com.heima.social.domain.dto.PostCreateDTO;
import com.heima.social.domain.vo.PostCreateVO;
import com.heima.social.domain.vo.PostDetailVO;
import com.heima.social.domain.vo.PostVO;
import com.heima.social.service.IPostLikeService;
import com.heima.social.service.IPostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 朋友圈帖子表 前端控制器
 * </p>
 *
 * @author lucy
 * @since 2026-07-21
 */
@Slf4j
@Tag(name = "朋友圈帖子接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/social/post")
public class PostController {

    private final IPostService postService;

    @Operation(summary = "发布朋友圈")
    @PostMapping
    public Result<PostCreateVO> create(@RequestBody PostCreateDTO postCreateDTO) {
        log.info("发布朋友圈：{}", postCreateDTO);
        return postService.create(postCreateDTO);
    }

    // 热度分 = likeCount×2 + commentCount×5 - 小时数×0.5
    @Operation(summary = "热门帖子列表")
    @GetMapping("/hot")
    public Result<PageDTO<PostVO>> hotPageList(@RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size) {
        log.info("热门帖子列表，第{}页，每页{}个帖子", page, size);
        return postService.hotPageList(page, size);
    }

    @Operation(summary = "最新帖子列表")
    @GetMapping("/timeline")
    public Result<PageDTO<PostVO>> timelinePageList(@RequestParam(required = false) Integer page,
                                                    @RequestParam(required = false) Integer size) {
        log.info("最新帖子列表：page:{},size:{}", page, size);
        return postService.timelinePageList(page, size);
    }

    @Operation(summary = "帖子详情")
    @GetMapping("/{postId}")
    public Result<PostDetailVO> detail(@PathVariable Long postId,
                                       @RequestParam(defaultValue = "1") Integer commentPage,
                                       @RequestParam(defaultValue = "10") Integer commentSize) {
        log.info("帖子详细，帖子ID：{}，评论页码：{}，评论页大小：{}", postId, commentPage, commentSize);
        return postService.detail(postId, commentPage, commentSize);
    }

    @Operation(summary = "删除帖子")
    @DeleteMapping("/{postId}")
    public Result<Void> delete(@PathVariable Long postId) {
        log.info("删除帖子：{}", postId);
        return postService.deletePost(postId);
    }

    @Operation(summary = "用户帖子列表")
    @GetMapping("/user/{userId}")
    public Result<PageDTO<PostVO>> userPost(@PathVariable Long userId,
                                          @RequestParam(defaultValue = "1") Integer page,
                                          @RequestParam(defaultValue = "10") Integer size) {
        log.info("我的帖子：{}，{},{}", userId, page, size);
        return postService.userPost(userId, page, size);
    }
}
