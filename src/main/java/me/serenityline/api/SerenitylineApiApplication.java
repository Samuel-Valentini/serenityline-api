// Copyright (c) 2026 Samuel Valentini. All rights reserved.

package me.serenityline.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

@ConfigurationPropertiesScan
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class SerenitylineApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SerenitylineApiApplication.class, args);
    }

}
