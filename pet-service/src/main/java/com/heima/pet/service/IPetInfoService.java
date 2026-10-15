package com.heima.pet.service;

import com.heima.api.domain.dto.Result;
import com.heima.api.enums.Species;
import com.heima.pet.domain.po.PetInfo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.heima.pet.domain.vo.PetDetailVO;
import com.heima.pet.domain.vo.PetRecoverVO;
import com.heima.pet.domain.vo.PetVO;

import java.util.List;

/**
 * <p>
 * 宠物基础信息表 服务类
 * </p>
 *
 * @author lucy
 * @since 2026-07-09
 */
public interface IPetInfoService extends IService<PetInfo> {

    /**
     * 领养宠物
     * @param petName
     * @param species
     * @return
     */
    Result<PetVO> foster(String petName, Species species);

    /**
     * 获取宠物详情
     * @param petId
     * @return
     */
    Result<PetDetailVO> getPetDetail(Long petId);

    /**
     * 获取我的宠物列表
     * @return
     */
    Result<List<PetVO>> getPetList();

    /**
     * 寻找离家出走的宠物
     * @param petId
     * @return
     */
    Result<PetRecoverVO> recover(Long petId);

    /**
     * 解除领养
     * @param petId
     * @return
     */
    Result<Void> deletePet(Long petId);

    /**
     * 根据用户id列表批量查询宠物信息
     * @param userIds
     * @return
     */
    List<PetVO> queryPetByUserIds(List<Long> userIds);

    /**
     * 根据用户id批量查询宠物信息
     * @param userId
     * @return
     */
    List<PetVO> queryPetByUserId(Long userId);

    /**
     * 内部接口-根据id按照顺序批量返回信息
     * @param petIds
     * @return
     */
    List<PetInfo> queryPetByIds(List<Long> petIds);

    // 内部接口-用户注销删除宠物
    Integer deletePetByUserId();
}
