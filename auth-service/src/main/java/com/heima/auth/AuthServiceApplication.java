package com.heima.auth;

import com.heima.api.config.FeignConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

// 启动openfeign简化负载均衡代码-----最佳实践后无法扫描
@EnableFeignClients(value = "com.heima.api.client", defaultConfiguration = FeignConfig.class)
@MapperScan("com.heima.auth.mapper")
// 在启动类上添加包扫描
@SpringBootApplication(scanBasePackages = {"com.heima.auth", "com.heima.common", "com.heima.api"})
public class AuthServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class);
    }
}
