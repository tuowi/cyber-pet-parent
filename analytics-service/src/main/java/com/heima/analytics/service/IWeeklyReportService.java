package com.heima.analytics.service;

import com.heima.analytics.domain.po.WeeklyReport;
import com.baomidou.mybatisplus.extension.service.IService;
import com.heima.analytics.domain.vo.WeeklyReportVO;
import com.heima.api.domain.dto.PageDTO;
import com.heima.api.domain.dto.Result;

/**
 * <p>
 * 宠物周报表 服务类
 * </p>
 *
 * @author lucy
 * @since 2026-07-25
 */
public interface IWeeklyReportService extends IService<WeeklyReport> {

    /**
     * 获取最新周报
     * @param petId
     * @return
     */
    Result<WeeklyReportVO> getNewWeeklyReport(Long petId);

    /**
     * 手动生成周报
     * @param petId
     * @return
     */
    Result<Long> editWeeklyReport(Long petId);

    /**
     * 获取历史周报列表
     * @param petId
     * @param page
     * @param size
     * @return
     */
    Result<PageDTO<WeeklyReport>> historyList(Long petId, Integer page, Integer size);
}
