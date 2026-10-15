package com.heima.auth.service;

import com.heima.api.domain.dto.Result;
import com.heima.auth.domain.dto.CancelAccountDTO;
import com.heima.auth.domain.dto.LoginDTO;
import com.heima.auth.domain.dto.RegisterDTO;
import com.heima.auth.domain.dto.UpdateUserInfoDTO;
import com.heima.auth.domain.po.UserInfo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.heima.auth.domain.vo.CancelAccountVO;
import com.heima.auth.domain.vo.LoginVO;
import com.heima.auth.domain.vo.RegisterVO;
import com.heima.auth.domain.vo.EditAdminVO;
import com.heima.auth.domain.vo.UpdateUserInfoVO;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author lucy
 * @since 2026-07-09
 */
public interface IUserInfoService extends IService<UserInfo> {

    /**
     * 用户注册
     * @param registerDTO
     * @return
     */
    Result<RegisterVO> register(RegisterDTO registerDTO);

    /**
     * 用户登录
     * @param loginDTO
     * @return
     */
    Result<LoginVO> login(LoginDTO loginDTO);

    /**
     * 根据id批量获取用户信息
     * @param ids
     * @return
     */
    List<UserInfo> queryUserByIds(List<Long> ids);

    // 退出登录
    Result<Void> logout(String jti);

    // 注销用户
    Result<CancelAccountVO> cancel(CancelAccountDTO cancelAccountDTO);

    // 设置管理员
    Result<EditAdminVO> admin(Long userId);

    // 取消管理员
    Result<EditAdminVO> demoteAdmin(Long userId);

    /**
     * 编辑用户信息（昵称）
     * @param updateUserInfoDTO
     * @return
     */
    Result<UpdateUserInfoVO> updateUserInfo(UpdateUserInfoDTO updateUserInfoDTO);
}
