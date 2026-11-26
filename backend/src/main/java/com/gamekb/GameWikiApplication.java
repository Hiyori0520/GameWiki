package com.gamekb;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
@MapperScan("com.gamekb.mapper")
public class GameWikiApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(GameWikiApplication.class, args);

        // 从实际生效的配置里取端口，避免改了端口后提示还写着旧的
        String port = context.getEnvironment().getProperty("server.port", "8081");
        String base = "http://localhost:" + port;

        System.out.println("=========================================");
        System.out.println("  AI智能游戏攻略知识库系统 启动成功！");
        System.out.println("  Knife4j 接口文档: " + base + "/doc.html");
        System.out.println("  Swagger UI 文档 : " + base + "/swagger-ui/index.html");
        System.out.println("  接口清单 JSON   : " + base + "/v3/api-docs");
        System.out.println("=========================================");
    }
}
