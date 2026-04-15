package com.yy.agent.contractmvp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 应用上下文冒烟测试：验证主要 Bean（含 Spring AI、内存仓储）可正常装配。
 */
@SpringBootTest
@ActiveProfiles("test")
class ContractAgentMvpApplicationTests {

	/**
	 * 空测试方法：仅触发 Spring 容器启动与依赖注入。
	 */
	@Test
	void contextLoads() {
	}

}
