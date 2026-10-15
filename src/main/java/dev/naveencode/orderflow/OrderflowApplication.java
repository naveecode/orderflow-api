package dev.naveencode.orderflow;

import dev.naveencode.orderflow.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class OrderflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderflowApplication.class, args);
    }
}
