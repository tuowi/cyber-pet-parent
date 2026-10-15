package com.heima.social.service;

import com.heima.api.domain.dto.PageDTO;
import com.heima.api.domain.dto.Result;
import com.heima.social.domain.po.PostComment;
import com.baomidou.mybatisplus.extension.service.IService;
import com.heima.social.domain.vo.CommentVO;

/**
 * <p>
 * 帖子评论表 服务类
 * </p>
 *
 * @author lucy
 * @since 2026-08-03
 */
public interface IPostCommentService extends IService<PostComment> {

    // 发表评论
    Result<CommentVO> comment(Long postId, String content);

    // 删除评论
    Result<Void> deleteComment(Long postId, Long commentId);

    // 评论列表
    Result<PageDTO<CommentVO>> listPageComment(Long postId, Integer page, Integer size);
}
