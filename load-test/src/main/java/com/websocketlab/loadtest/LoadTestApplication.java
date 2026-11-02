package com.websocketlab.loadtest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LoadTestApplication {
    public static void main(String[] args) {
        // Run as CLI application
        LoadTestCLI.main(args);
    }
}
