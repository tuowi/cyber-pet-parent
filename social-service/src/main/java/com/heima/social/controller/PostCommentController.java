package com.heima.social.controller;


import com.heima.api.domain.dto.PageDTO;
import com.heima.api.domain.dto.Result;
import com.heima.social.domain.dto.CommentRequestDTO;
import com.heima.social.domain.vo.CommentVO;
import com.heima.social.service.IPostCommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 帖子评论表 前端控制器
 * </p>
 *
 * @author lucy
 * @since 2026-08-03
 */
@Slf4j
@Tag(name = "帖子评论接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/social/post-comment")
public class PostCommentController {

    private final IPostCommentService postCommentService;

    @Operation(summary = "发表评论")
    @PostMapping("/{postId}/comment")
    public Result<CommentVO> comment(@PathVariable Long postId,
                                     @RequestBody CommentRequestDTO commentRequestDTO) {
        log.info("发表评论：{}，{}", postId, commentRequestDTO.getContent());
        return postCommentService.comment(postId, commentRequestDTO.getContent());
    }

    @Operation(summary = "删除评论")
    @DeleteMapping("/{postId}/comment/{commentId}")
    public Result<Void> delete(@PathVariable Long postId,
                               @PathVariable Long commentId) {
        log.info("删除评论：postId:{},commentId:{}", postId, commentId);
        return postCommentService.deleteComment(postId, commentId);
    }

    // TODO 游标分页
    @Operation(summary = "评论列表")
    @GetMapping("/{postId}/comment/list")
    public Result<PageDTO<CommentVO>> list(@PathVariable Long postId,
                                           @RequestParam(defaultValue = "1") Integer page,
                                           @RequestParam(defaultValue = "10") Integer size) {
        log.info("评论列表：评论id:{},页码：{}，页大小：{}", postId, page, size);
        return postCommentService.listPageComment(postId, page, size);
    }
}
