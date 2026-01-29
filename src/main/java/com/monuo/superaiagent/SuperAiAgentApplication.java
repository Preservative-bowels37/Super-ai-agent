package com.monuo.superaiagent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.monuo.superaiagent.mapper")
public class SuperAiAgentApplication {

	public static void main(String[] args) {
		SpringApplication.run(SuperAiAgentApplication.class, args);
	}

}
