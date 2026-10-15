package com.heima.pet.service;

import com.heima.api.domain.dto.PageDTO;
import com.heima.api.domain.dto.Result;
import com.heima.api.enums.InteractionType;
import com.heima.pet.domain.po.StateTransitionLog;
import com.baomidou.mybatisplus.extension.service.IService;
import com.heima.pet.domain.vo.InteractResultVO;
import com.heima.pet.domain.vo.StateTransitionVO;
import jakarta.validation.constraints.NotNull;

/**
 * <p>
 * 状态流转日志表 服务类
 * </p>
 *
 * @author lucy
 * @since 2026-07-09
 */
public interface IStateTransitionLogService extends IService<StateTransitionLog> {

    /**
     * 宠物互动
     * @param petId
     * @param type
     * @param productId 道具ID（FEED/BATHE 必传，PET 可选）
     * @return
     */
    Result<InteractResultVO> interact(Long petId, InteractionType type, Long productId);

    /**
     * 获取状态变更历史
     * @param petId
     * @param page
     * @param size
     * @return
     */
    Result<PageDTO<StateTransitionVO>> logList(Long petId, Long page, Long size);

    /**
     * 扣减目标宠物心情值 -5
     * @param petId
     * @return
     */
    boolean deductMoodByPetId(@NotNull Long petId);
}
