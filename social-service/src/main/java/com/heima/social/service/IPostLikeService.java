package com.heima.social.service;

import com.heima.api.domain.dto.Result;
import com.heima.social.domain.dto.PostCreateDTO;
import com.heima.social.domain.po.PostLike;
import com.baomidou.mybatisplus.extension.service.IService;
import com.heima.social.domain.vo.PostCreateVO;
import com.heima.social.domain.vo.PostLikedVO;

/**
 * <p>
 * 点赞记录表 服务类
 * </p>
 *
 * @author lucy
 * @since 2026-07-21
 */
public interface IPostLikeService extends IService<PostLike> {

    /**
     * 点赞帖子
     * @param postId
     * @return
     */
    Result<PostLikedVO> liked(Long postId);

    /**
     * 取消点赞
     * @param postId
     * @return
     */
    Result<PostLikedVO> unliked(Long postId);

    // 内部接口-查询所有 post_like WHERE user_id=? → 物理删除
    Boolean deleteLikeByUserId(Long userId);
}
