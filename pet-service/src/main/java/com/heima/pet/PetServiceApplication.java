package com.heima.pet;

import com.heima.api.config.FeignConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

// 启动openfeign简化负载均衡代码-----最佳实践后无法扫描
@EnableFeignClients(value = "com.heima.api.client", defaultConfiguration = FeignConfig.class)
@MapperScan("com.heima.pet.mapper")
@SpringBootApplication(scanBasePackages = {"com.heima.pet", "com.heima.common", "com.heima.api"})
@EnableScheduling
public class PetServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PetServiceApplication.class);
    }
}
