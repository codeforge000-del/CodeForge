package SD_Tech.LeetAI.Controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/admin")
public class AdminLoginController {

    private static final Logger logger = LoggerFactory.getLogger(AdminLoginController.class);

    @Value("${admin.username}")
    private String username;

    @Value("${admin.password}")
    private String password;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AdminLoginRequest request) {
        logger.info("Admin login attempt for username: {}", request.getUsername());
        
        if (username == null || password == null) {
            logger.error("Admin credentials not configured properly");
            return ResponseEntity.status(500).body("Server configuration error");
        }
        
        if (username.equals(request.getUsername()) && password.equals(request.getPassword())) {
            logger.info("Admin login successful for username: {}", request.getUsername());
            return ResponseEntity.ok().body(new AdminLoginResponse("Admin login successful", true));
        }
        
        logger.warn("Admin login failed for username: {}", request.getUsername());
        return ResponseEntity.status(401).body(new AdminLoginResponse("Invalid credentials", false));
    }

    // DTO for login request
    public static class AdminLoginRequest {
        private String username;
        private String password;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
    
    // DTO for login response
    public static class AdminLoginResponse {
        private String message;
        private boolean success;
        
        public AdminLoginResponse(String message, boolean success) {
            this.message = message;
            this.success = success;
        }
        
        public String getMessage() { return message; }
        public boolean isSuccess() { return success; }
    }
}