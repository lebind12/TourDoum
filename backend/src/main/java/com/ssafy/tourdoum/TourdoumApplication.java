package com.ssafy.tourdoum;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TourdoumApplication {

  public static void main(String[] args) {
    SpringApplication.run(TourdoumApplication.class, args);
  }
}
