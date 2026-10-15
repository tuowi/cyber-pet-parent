package com.heima.social.service;

import com.heima.api.domain.dto.PageDTO;
import com.heima.api.domain.dto.Result;
import com.heima.social.domain.dto.MatchRequestDTO;
import com.heima.social.domain.dto.MatchResultDTO;
import com.heima.social.domain.dto.StealRequestDTO;
import com.heima.social.domain.po.InteractionRecord;
import com.baomidou.mybatisplus.extension.service.IService;
import com.heima.social.domain.vo.MatchResultVO;
import com.heima.social.domain.vo.StealResultVO;

import java.util.List;

/**
 * <p>
 * 社交互动记录表 服务类
 * </p>
 *
 * @author lucy
 * @since 2026-07-21
 */
public interface IInteractionRecordService extends IService<InteractionRecord> {

    /**
     * 在线心跳
     * @param matchRequestDTO
     * @return
     */
    Result<Void> heartbeat(MatchRequestDTO matchRequestDTO);

    /**
     * 查找附近在线宠物
     * @param matchResultDTO
     * @return
     */
    Result<List<MatchResultVO>> match(MatchResultDTO matchResultDTO);

    /**
     * 偷零食
     * @param stealRequestDTO
     * @return
     */
    Result<StealResultVO> steal(StealRequestDTO stealRequestDTO);

    /**
     * 上传在线位置
     * @param matchRequestDTO
     * @return
     */
    Result<Void> location(MatchRequestDTO matchRequestDTO);

    /**
     * 互动列表
     * @param page
     * @param size
     * @return
     */
    Result<PageDTO<InteractionRecord>> logPageList(Integer page, Integer size);
}
