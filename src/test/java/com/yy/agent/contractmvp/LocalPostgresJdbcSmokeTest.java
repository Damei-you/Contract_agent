package com.yy.agent.contractmvp;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 连本机/Compose 已启动的 Postgres（与 {@code application.yml} 中 PG* 环境变量约定一致）。
 * 运行前：启动数据库（例如 {@code docker compose up -d}），并设置环境变量 {@code RUN_LOCAL_PG_IT=true}。
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_PG_IT", matches = "true")
class LocalPostgresJdbcSmokeTest {

	@DynamicPropertySource
	static void localDatasource(DynamicPropertyRegistry registry) {
		String host = Optional.ofNullable(System.getenv("PGHOST")).orElse("localhost");
		String port = Optional.ofNullable(System.getenv("PGPORT")).orElse("5432");
		String db = Optional.ofNullable(System.getenv("PGDATABASE")).orElse("contract_agent");
		registry.add("spring.datasource.url",
				() -> "jdbc:postgresql://" + host + ":" + port + "/" + db);
		registry.add("spring.datasource.username",
				() -> Optional.ofNullable(System.getenv("PGUSER")).orElse("postgres"));
		registry.add("spring.datasource.password",
				() -> Optional.ofNullable(System.getenv("PGPASSWORD")).orElse("123456"));
		registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
	}

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void connectSelectOne() {
		assertThat(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
	}

	@Test
	void vectorExtensionPresentOrCreatable() {
		jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
		Boolean ok = jdbcTemplate.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector')",
				Boolean.class);
		assertThat(ok).isTrue();
	}
}
