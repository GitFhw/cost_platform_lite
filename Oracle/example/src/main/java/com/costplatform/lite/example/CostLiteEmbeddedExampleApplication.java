package com.costplatform.lite.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 嵌入式集成验证应用。
 *
 * <p>这里没有复制计费 Controller，也没有启动 cost-lite-server；只依赖 cost-lite-starter-oracle，
 * 用于验证计费入口和核心逻辑是否已经进入当前 JVM。</p>
 */
@SpringBootApplication
public class CostLiteEmbeddedExampleApplication {
    public static void main(String[] args) {
        SpringApplication.run(CostLiteEmbeddedExampleApplication.class, args);
    }
}
