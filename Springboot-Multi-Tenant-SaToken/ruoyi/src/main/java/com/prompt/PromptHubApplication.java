package com.prompt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
public class PromptHubApplication
{
    public static void main(String[] args)
    {
        SpringApplication.run(PromptHubApplication.class, args);
    }
}
