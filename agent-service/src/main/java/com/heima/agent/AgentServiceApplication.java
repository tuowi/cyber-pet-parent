package com.heima.agent;

import com.heima.api.config.FeignConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

// 启动openfeign简化负载均衡代码-----最佳实践后无法扫描
@EnableFeignClients(value = "com.heima.api.client", defaultConfiguration = FeignConfig.class)
// 在启动类上添加包扫描
@SpringBootApplication(scanBasePackages = {"com.heima.agent", "com.heima.common", "com.heima.api"})
public class AgentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentServiceApplication.class);
    }
}
