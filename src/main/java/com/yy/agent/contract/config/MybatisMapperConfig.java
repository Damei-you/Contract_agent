package com.yy.agent.contract.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
@MapperScan("com.yy.agent.contract.mapper")
public class MybatisMapperConfig {
}
