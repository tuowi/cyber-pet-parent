package com.heima.analytics.domain.po;

import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 宠物周报表
 * </p>
 *
 * @author lucy
 * @since 2026-07-25
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("weekly_report")
public class WeeklyReport implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 雪花ID
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 宠物ID
     */
    private Long petId;

    /**
     * 报告周，如 2026-W28
     */
    private String reportWeek;

    /**
     * 本周喂食次数
     */
    private Integer totalFeeds;

    /**
     * 本周玩耍次数
     */
    private Integer totalPlays;

    /**
     * 本周洗澡次数
     */
    private Integer totalBaths;

    /**
     * 本周平均心情值
     */
    private BigDecimal avgMood;

    /**
     * 本周亲密度增长
     */
    private Integer intimacyGain;

    /**
     * 详细报告JSON
     */
    private String reportJson;

    private LocalDateTime createTime;

    /**
     * 最近更新时间
     */
    private LocalDateTime updateTime;


}
