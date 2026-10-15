package com.heima.analytics.domain.po.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyReportJson {
    @JsonProperty("reportWeek")
    private String reportWeek;

    @JsonProperty("weekRange")
    private String weekRange;

    @JsonProperty("totalFeeds")
    private Integer totalFeeds;

    @JsonProperty("totalPlays")
    private Integer totalPlays;

    @JsonProperty("totalBaths")
    private Integer totalBaths;

    @JsonProperty("intimacyGain")
    private Integer intimacyGain;

    @JsonProperty("summary")
    private String summary;
}
