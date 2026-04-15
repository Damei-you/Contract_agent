package com.yy.agent.contractmvp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot 入口：扫描本包及子包，装配 Web、Spring AI、领域服务与内存仓储。
 */
@SpringBootApplication
public class ContractAgentMvpApplication {

	/**
	 * 启动应用；可通过环境变量或 {@code application.properties} 配置模型端点与密钥。
	 *
	 * @param args 标准 Spring Boot 命令行参数
	 */
	public static void main(String[] args) {
		SpringApplication.run(ContractAgentMvpApplication.class, args);
	}

}
