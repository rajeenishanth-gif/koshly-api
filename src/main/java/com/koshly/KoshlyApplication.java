package com.koshly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan // registers every @ConfigurationProperties class, e.g. KoshlyProperties
@EnableScheduling // runs @Scheduled jobs, e.g. RemovedAccountCleanup
public class KoshlyApplication {

	public static void main(String[] args) {
		SpringApplication.run(KoshlyApplication.class, args);
	}

}
