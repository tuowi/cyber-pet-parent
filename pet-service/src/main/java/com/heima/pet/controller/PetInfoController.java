package com.heima.pet.controller;


import com.heima.api.domain.dto.Result;
import com.heima.api.enums.Species;
import com.heima.pet.domain.po.PetInfo;
import com.heima.pet.domain.vo.PetDetailVO;
import com.heima.pet.domain.vo.PetRecoverVO;
import com.heima.pet.domain.vo.PetVO;
import com.heima.pet.service.IPetInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;

/**
 * <p>
 * 宠物基础信息表 前端控制器
 * </p>
 *
 * @author lucy
 * @since 2026-07-09
 */
@Slf4j
@Tag(name = "宠物基础信息管理接口")
@RestController
@RequestMapping("/pet/pet-info")
@RequiredArgsConstructor
public class PetInfoController {

    private final IPetInfoService petInfoService;

    @Operation(summary = "宠物领养")
    @PostMapping
    public Result<PetVO> fosterPet(String petName, Species species) {
        log.info("宠物领养，宠物名：{}，宠物种类：{}", petName, species);
        return petInfoService.foster(petName, species);
    }

    @Operation(summary = "获取宠物详情")
    @GetMapping("/{petId}")
    public Result<PetDetailVO> PetDetail(@PathVariable Long petId) {
        log.info("获取宠物详情，宠物ID：{}", petId);
        return petInfoService.getPetDetail(petId);
    }

    @Operation(summary = "获取我的宠物列表")
    @GetMapping("/list")
    public Result<List<PetVO>> petList() {
        log.info("获取我的宠物列表");
        return petInfoService.getPetList();
    }

    @Operation(summary = "寻找离家出走的宠物")
    @PostMapping("/{petId}/recover")
    public Result<PetRecoverVO> recover(@PathVariable Long petId) {
        log.info("寻找离家出走的宠物:{}", petId);
        return petInfoService.recover(petId);
    }

    @Operation(summary = "解除领养")
    @DeleteMapping("/{petId}")
    public Result<Void> deletePet(@PathVariable Long petId) {
        log.info("解除领养:{}", petId);
        return petInfoService.deletePet(petId);
    }

    // 内部接口-根据用户id列表批量查询宠物信息
    @GetMapping
    public List<PetVO> queryPetByUserIds(@RequestParam("userIds") List<Long> userIds) {
        log.info("根据用户id列表批量查询宠物信息:{}", userIds);
        return petInfoService.queryPetByUserIds(userIds);
    }

    // 内部接口-根据用户id获取宠物信息
    @GetMapping("/user/{userId}")
    public List<PetVO> queryPetByUserId(@PathVariable Long userId) {
        log.info("根据用户id获取宠物信息:{}", userId);
        return petInfoService.queryPetByUserId(userId);
    }

    // 内部接口-根据id获取宠物基本信息
    @GetMapping("/basic/{petId}")
    public PetInfo getBasicPetInfo(@PathVariable Long petId) {
        log.info("根据id获取宠物基本信息：{}", petId);
        return petInfoService.getById(petId);
    }

    // 内部接口-根据id按照顺序批量返回信息
    @GetMapping("/petIds/list")
    public List<PetInfo> queryPetByIds(@RequestParam List<Long> petIds){
        log.info("内部接口-根据id按照顺序批量返回信息");
        return petInfoService.queryPetByIds(petIds);
    }

    // 内部接口-用户注销删除宠物
    @DeleteMapping("/cancelAccount")
    public Integer deletePetByUserId(){
        log.info("内部接口-用户注销删除宠物");
        return petInfoService.deletePetByUserId();
    }
}
