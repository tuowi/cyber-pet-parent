package com.heima.pet.controller;


import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 宠物属性表（MySQL 持久化副本） 前端控制器
 * </p>
 *
 * @author lucy
 * @since 2026-07-09
 */
@Slf4j
@Tag(name = "宠物属性管理接口")
@RestController
@RequestMapping("/pet/pet-attribute")
public class PetAttributeController {

}
