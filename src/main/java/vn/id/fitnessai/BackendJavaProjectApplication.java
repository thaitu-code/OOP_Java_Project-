package vn.id.fitnessai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class BackendJavaProjectApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendJavaProjectApplication.class, args);
    }

}
