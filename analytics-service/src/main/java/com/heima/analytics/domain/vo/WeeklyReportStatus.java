package com.heima.analytics.domain.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class WeeklyReportStatus {
    private Integer totalFeeds;
    private Integer totalPlays;
    private Integer totalBaths;
    private BigDecimal totalEarnings;
    private BigDecimal totalExpenses;
    private BigDecimal avgMood;
    private Integer intimacyGain;
    private Integer currentIntimacy;
}
