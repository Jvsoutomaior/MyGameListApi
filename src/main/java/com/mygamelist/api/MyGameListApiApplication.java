package com.mygamelist.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MyGameListApiApplication {

  public static void main(String[] args) {
    SpringApplication.run(MyGameListApiApplication.class, args);
  }
}
