package com.heima.analytics;

import com.heima.api.config.FeignConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

// 启动openfeign简化负载均衡代码-----最佳实践后无法扫描
@EnableFeignClients(value = "com.heima.api.client", defaultConfiguration = FeignConfig.class)
@MapperScan("com.heima.analytics.mapper")
@EnableScheduling
// 在启动类上添加包扫描
@SpringBootApplication(scanBasePackages = {"com.heima.analytics", "com.heima.common", "com.heima.api"})
public class AnalyticsServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AnalyticsServiceApplication.class);
    }
}
