package com.heima.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class SecurityConfigTest {

    @Test
    public void test1() {
        // 构建加密器，参数10=工作因子，数字越大加密越慢，安全性越高，推荐10~12
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

        String rawPwd = "123456"; // 用户原始明文密码
        // 加密
        String encode = passwordEncoder.encode(rawPwd);
        // 加密后密文：$2a$10$3.35twC4dk6x0BccDgzItu4YEqAXCszmRyQfEj.fxNbX.EXEX.ADG
        // 加密后密文：$2a$10$qX33nBnFAOf2xBI0ic.fr.Fe/mSov1PtRUEmFNn.I1/al2Q/INcKu
        System.out.println("加密后密文：" + encode);
    }

    @Test
    public void test2(){
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

        String rawPwd="123456";
        String dbEncrypt = "$2a$10$3.35twC4dk6x0BccDgzItu4YEqAXCszmRyQfEj.fxNbX.EXEX.ADG"; // 数据库存的密文

        // 校验
        boolean matches = passwordEncoder.matches(rawPwd, dbEncrypt);
        if (matches){
            System.out.println("密码正确");
        }else {
            System.out.println("密码错误");
        }
    }
}
