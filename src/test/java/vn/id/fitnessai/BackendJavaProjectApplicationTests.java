package vn.id.fitnessai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mail.javamail.JavaMailSender;
import vn.id.fitnessai.service.GeminiAiClient;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:boot_smoke;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
        "gemini.api.key=", "spring.mail.host=localhost", "spring.mail.username=sender@example.test", "spring.mail.password="
})
class BackendJavaProjectApplicationTests {
    @MockitoBean GeminiAiClient gemini;
    @MockitoBean JavaMailSender mail;

    @Test
    void contextLoads() {
    }

}
