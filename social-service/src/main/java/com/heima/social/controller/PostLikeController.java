package com.heima.social.controller;


import com.heima.api.domain.dto.Result;
import com.heima.social.domain.vo.PostLikedVO;
import com.heima.social.service.IPostLikeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 点赞记录表 前端控制器
 * </p>
 *
 * @author lucy
 * @since 2026-07-21
 */
@Slf4j
@Tag(name = "点赞记录接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/social/post-like")
public class PostLikeController {

    private final IPostLikeService postLikeService;

    @Operation(summary = "点赞帖子")
    @PostMapping("/{postId}/like")
    public Result<PostLikedVO> liked(@PathVariable Long postId) {
        log.info("点赞帖子：{}", postId);
        return postLikeService.liked(postId);
    }

    @Operation(summary = "取消点赞")
    @DeleteMapping("/{postId}/like")
    public Result<PostLikedVO> unliked(@PathVariable Long postId) {
        log.info("取消点赞：{}", postId);
        return postLikeService.unliked(postId);
    }

    // 内部接口-用户注销，查询所有 post_like WHERE user_id=? → 物理删除
    @DeleteMapping("cancelAccount")
    public Boolean deleteLikeByUserId(@RequestParam Long userId) {
        log.info("用户注销取消点赞：{}", userId);
        return postLikeService.deleteLikeByUserId(userId);
    }
}
