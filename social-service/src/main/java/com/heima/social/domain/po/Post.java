package com.heima.social.domain.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 朋友圈帖子表
 * </p>
 *
 * @author lucy
 * @since 2026-07-21
 */
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("post")
public class Post implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 雪花ID
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 发帖用户ID
     */
    private Long userId;

    /**
     * 关联宠物ID
     */
    private Long petId;

    /**
     * 喵言喵语内容
     */
    private String content;

    /**
     * 配图URL
     */
    private String imageUrl;

    /**
     * 点赞数（冗余，加速查询）
     */
    private Integer likeCount;

    /**
     * 评论数（冗余）
     */
    private Integer commentCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;

/*    @TableField(exist = false)
    private Double score;*/

}
