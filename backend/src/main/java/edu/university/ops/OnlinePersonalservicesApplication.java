package edu.university.ops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OnlinePersonalservicesApplication {

    public static void main(String[] args) {
        SpringApplication.run(OnlinePersonalservicesApplication.class, args);
    }
}
