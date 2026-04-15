package com.yy.agent.contractmvp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 使用与生产相同的 JDBC 驱动与 Spring {@link JdbcTemplate}，在 pgvector 镜像中验证库、扩展与简单向量列读写。
 * 需要本机 Docker可用；无 Docker 时跳过（Testcontainers 会失败并提示）。
 */
@JdbcTest
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostgresPgvectorJdbcSmokeTest {

	@SuppressWarnings("resource")
	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
			.withDatabaseName("contract_agent")
			.withUsername("postgres")
			.withPassword("postgres");

	@DynamicPropertySource
	static void registerDatasource(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
	}

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void connectSelectOne() {
		Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
		assertThat(one).isEqualTo(1);
	}

	@Test
	void vectorExtensionAndRoundTrip() {
		jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
		Boolean installed = jdbcTemplate.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector')",
				Boolean.class);
		assertThat(installed).isTrue();

		jdbcTemplate.execute("DROP TABLE IF EXISTS it_smoke_vector");
		jdbcTemplate.execute("CREATE TABLE it_smoke_vector (id serial PRIMARY KEY, embedding vector(3))");
		jdbcTemplate.update("INSERT INTO it_smoke_vector (embedding) VALUES ('[0.1,0.2,0.3]'::vector)");

		String literal = jdbcTemplate.queryForObject(
				"SELECT embedding::text FROM it_smoke_vector WHERE id = 1",
				String.class);
		assertThat(literal).isNotBlank();
	}
}
