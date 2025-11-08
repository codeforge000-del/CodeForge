package SD_Tech.LeetAI;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class LeetAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeetAiApplication.class, args);
    }
}