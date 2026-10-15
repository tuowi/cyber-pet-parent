package com.heima.shop;

import com.heima.api.config.FeignConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

// 启动openfeign简化负载均衡代码-----最佳实践后无法扫描
@EnableFeignClients(value = "com.heima.api.client", defaultConfiguration = FeignConfig.class)
@MapperScan("com.heima.shop.mapper")
@SpringBootApplication(scanBasePackages = {"com.heima.shop", "com.heima.common", "com.heima.api"})
public class ShopServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShopServiceApplication.class);
    }
}
