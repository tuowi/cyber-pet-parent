package com.heima.api.client;

import com.heima.api.domain.dto.Result;
import com.heima.api.domain.po.PetInfo;
import com.heima.api.domain.query.InteractRequest;
import com.heima.api.domain.vo.InteractResultVO;
import com.heima.api.domain.vo.PetDetailVO;
import com.heima.api.domain.vo.PetVO;
import jakarta.validation.constraints.NotNull;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@FeignClient("pet-service")
public interface PetClient {

    /**
     * 根据用户id列表批量查询宠物信息
     */
    @GetMapping("/pet/pet-info")
    List<PetVO> queryPetByUserIds(@RequestParam("userIds") Collection<Long> userIds);

    /**
     * 根据用户id获取宠物信息
     */
    @GetMapping("/pet/pet-info/user/{userId}")
    List<PetVO> queryPetByUserId(@PathVariable Long userId);

    /**
     * 扣减目标宠物心情值 -5
     *
     * @param petId
     * @return
     */
    @PostMapping("/pet/state-transition-log/{petId}/deduct-mood")
    boolean deductMoodById(@PathVariable("petId") @NotNull Long petId);

    /**
     * 根据宠物id获取宠物信息
     */
    @GetMapping("/pet/pet-info/basic/{petId}")
    PetInfo getBasicPetInfo(@PathVariable Long petId);

    // 根据id按照顺序批量返回信息
    @GetMapping("/pet/pet-info/petIds/list")
    List<PetInfo> queryPetByIds(@RequestParam Collection<Long> petIds);

    // 根据id获取宠物详细信息
    @GetMapping("/pet/pet-info/{petId}")
    Result<PetDetailVO> PetDetail(@PathVariable Long petId);

    // 与宠物互动
    @PostMapping("/pet/state-transition-log/{petId}/interact")
    Result<InteractResultVO> interact(@PathVariable Long petId,
                                      @RequestBody InteractRequest request);

    // 用户注销删除宠物
    @DeleteMapping("/pet/pet-info/cancelAccount")
    Integer deletePetByUserId();
}
