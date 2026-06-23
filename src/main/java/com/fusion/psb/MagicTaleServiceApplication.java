package com.fusion.psb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class MagicTaleServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(MagicTaleServiceApplication.class, args);
	}

}
