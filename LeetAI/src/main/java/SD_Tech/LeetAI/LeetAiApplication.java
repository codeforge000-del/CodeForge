package SD_Tech.LeetAI;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

import io.github.cdimascio.dotenv.Dotenv;

@SpringBootApplication
@EnableAsync
public class LeetAiApplication {

    public static void main(String[] args) {
        // Manually load .env in case spring-dotenv fails
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing() // avoid crash if .env missing
                .load();

        // Force-set properties (Spring Boot will pick these up)
        System.setProperty("DB_URL", dotenv.get("DB_URL", ""));
        System.setProperty("DB_USERNAME", dotenv.get("DB_USERNAME", ""));
        System.setProperty("DB_PASSWORD", dotenv.get("DB_PASSWORD", ""));

        SpringApplication.run(LeetAiApplication.class, args);
    }
}
