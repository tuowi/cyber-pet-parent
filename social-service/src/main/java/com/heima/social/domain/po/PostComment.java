package com.heima.social.domain.po;

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
 * 帖子评论表
 * </p>
 *
 * @author lucy
 * @since 2026-08-03
 */
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("post_comment")
public class PostComment implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 雪花ID
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 帖子ID
     */
    private Long postId;

    /**
     * 评论用户ID
     */
    private Long userId;

    /**
     * 评论内容
     */
    private String content;

    private LocalDateTime createTime;

    /**
     * 0=正常 1=已删除
     */
    private Integer deleted;


}
