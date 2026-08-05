package com.im.platform.gateway;

import com.im.platform.gateway.server.NettyGatewayServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 网关进程入口。Spring Boot 只用来托管配置中心接入(Nacos)、健康检查(Actuator)、
 * 到 im-core 的 gRPC 客户端、连接密钥的定时清扫({@code @EnableScheduling},见
 * {@link com.im.platform.gateway.crypto.ConnectionKeyStore})和优雅停机,真正的客户端连接
 * 由 {@link NettyGatewayServer}(SmartLifecycle,随 Spring 容器启动)独立处理,不走 Spring MVC。
 */
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.im.platform")
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
