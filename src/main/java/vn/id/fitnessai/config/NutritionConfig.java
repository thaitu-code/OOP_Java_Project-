package vn.id.fitnessai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.id.fitnessai.entity.NutritionLog;

import java.time.Clock;

@Configuration
public class NutritionConfig {

    @Bean
    public Clock nutritionClock() {
        return Clock.system(NutritionLog.VN_ZONE);
    }
}