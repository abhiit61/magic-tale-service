package com.fusion.psb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class PersonalisedStorybookCoreApplication {

	public static void main(String[] args) {
		SpringApplication.run(PersonalisedStorybookCoreApplication.class, args);
	}

}
