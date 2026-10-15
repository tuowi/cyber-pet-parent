package com.heima.pet.service.impl;

import com.heima.pet.domain.po.PetAttribute;
import com.heima.pet.mapper.PetAttributeMapper;
import com.heima.pet.service.IPetAttributeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 宠物属性表（MySQL 持久化副本） 服务实现类
 * </p>
 *
 * @author lucy
 * @since 2026-07-09
 */
@Service
public class PetAttributeServiceImpl extends ServiceImpl<PetAttributeMapper, PetAttribute> implements IPetAttributeService {

}
