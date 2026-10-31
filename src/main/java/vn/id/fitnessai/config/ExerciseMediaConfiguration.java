package vn.id.fitnessai.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;

@Configuration
public class ExerciseMediaConfiguration {
    @Bean @ConditionalOnMissingBean(MultipartConfigElement.class)
    public MultipartConfigElement exerciseMultipartConfig(
            @Value("${fitness.exercise.media-max-bytes:52428800}") long maxBytes) {
        return new MultipartConfigElement("", maxBytes, maxBytes + 1048576L, 0);
    }
}
