package com.heima.social.service;

import com.heima.api.domain.dto.PageDTO;
import com.heima.api.domain.dto.Result;
import com.heima.social.domain.dto.PostCreateDTO;
import com.heima.social.domain.po.Post;
import com.baomidou.mybatisplus.extension.service.IService;
import com.heima.social.domain.vo.PostCreateVO;
import com.heima.social.domain.vo.PostDetailVO;
import com.heima.social.domain.vo.PostVO;

/**
 * <p>
 * 朋友圈帖子表 服务类
 * </p>
 *
 * @author lucy
 * @since 2026-07-21
 */
public interface IPostService extends IService<Post> {

    /**
     * 发布朋友圈
     * @param postCreateDTO
     * @return
     */
    Result<PostCreateVO> create(PostCreateDTO postCreateDTO);

    /**
     * 热门帖子列表
     * @param page
     * @param size
     * @return
     */
    Result<PageDTO<PostVO>> hotPageList(Integer page, Integer size);

    /**
     * 最新帖子列表
     * @param page
     * @param size
     * @return
     */
    Result<PageDTO<PostVO>> timelinePageList(Integer page, Integer size);

    // 帖子详细
    Result<PostDetailVO> detail(Long postId, Integer commentPage, Integer commentSize);

    // 删除帖子
    Result<Void> deletePost(Long postId);

    // 我的帖子
    Result<PageDTO<PostVO>> myPost(Integer page, Integer size);

    // 用户帖子列表
    Result<PageDTO<PostVO>> userPost(Long userId, Integer page, Integer size);
}
