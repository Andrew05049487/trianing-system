package com.example.trainingsystems;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TrainingSystemApplication {
    public static void main(String[] args) {
        // DATETIME(6) stores UTC wall clocks. Hibernate 6.3's LocalDateTime
        // JDBC Timestamp bridge also uses the JVM default zone; align it before boot.
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        SpringApplication.run(TrainingSystemApplication.class, args);
    }
}
