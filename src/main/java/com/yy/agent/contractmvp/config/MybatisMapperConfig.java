package com.yy.agent.contractmvp.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
@MapperScan("com.yy.agent.contractmvp.mapper")
public class MybatisMapperConfig {
}
