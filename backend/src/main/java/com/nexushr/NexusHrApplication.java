package com.nexushr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NexusHrApplication {
  public static void main(String[] args) {
    SpringApplication.run(NexusHrApplication.class, args);
  }
}
