package com.heima.analytics.service;

import com.heima.analytics.domain.po.IntimacyEvent;
import com.baomidou.mybatisplus.extension.service.IService;
import com.heima.analytics.domain.vo.IntimacyDetailVO;
import com.heima.analytics.domain.vo.IntimacyTopVO;
import com.heima.analytics.domain.vo.RankVO;
import com.heima.api.domain.dto.Result;

import java.util.List;

/**
 * <p>
 * 亲密度事件流水表 服务类
 * </p>
 *
 * @author lucy
 * @since 2026-07-25
 */
public interface IIntimacyEventService extends IService<IntimacyEvent> {

    /**
     * 亲密度排行榜
     * @param limit
     * @return
     */
    Result<List<IntimacyTopVO>> top(Integer limit);

    /**
     * 我的宠物排名
     * @param petId
     * @return
     */
    Result<RankVO> rank(Long petId);

    /**
     * 宠物亲密度详情
     * @param petId
     * @return
     */
    Result<IntimacyDetailVO> detail(Long petId);
}
