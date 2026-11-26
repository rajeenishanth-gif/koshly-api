package com.koshly;

import org.springframework.boot.SpringApplication;

import com.koshly.testsupport.TestcontainersConfiguration;

/**
 * The test version of KoshlyApplication: starts the API with a throwaway
 * database that is gone when you stop it.
 *
 *   .\gradlew.bat bootTestRun
 */
public class TestKoshlyApplication {

    public static void main(String[] args) {
        SpringApplication.from(KoshlyApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
