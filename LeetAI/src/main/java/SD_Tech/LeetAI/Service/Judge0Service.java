package SD_Tech.LeetAI.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import SD_Tech.LeetAI.DTO.Judge0BatchResponse;
import SD_Tech.LeetAI.DTO.Judge0BatchSubmissionRequest;
import SD_Tech.LeetAI.DTO.Judge0Response;

@Service
public class Judge0Service {

    @Value("${judge0.api.url}")
    private String judge0BaseUrl;

    @Value("${sulu.api.key}")
    private String suluApiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Counter for tracking API calls
    private static int apiCallCount = 0;
    private static long lastResetTime = System.currentTimeMillis();
    
    // Maximum number of API calls allowed per minute
    private static final int MAX_CALLS_PER_MINUTE = 30;
    
    // Minimum delay between API calls in milliseconds
    private static final int MIN_DELAY_BETWEEN_CALLS = 1000;

    // Language ID mapping (matching the JavaScript implementation)
    private static final int PYTHON_ID = 71;
    private static final int JAVASCRIPT_ID = 63;
    private static final int JAVA_ID = 62;
    private static final int CPP_ID = 54;
    private static final int GO_ID = 60;
    private static final int TYPESCRIPT_ID = 74;

    public Judge0Response executeCode(String sourceCode, String language, String input) throws Exception {
        try {
            checkRateLimit();

            int languageId = getLanguageId(language);

            // ✅ Prepare proper JSON payload
            Map<String, Object> body = new HashMap<>();
            body.put("language_id", languageId);
            body.put("source_code", sourceCode);
            body.put("stdin", input);
            body.put("redirect_stderr_to_stdout", true); // optional, helpful for debug

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // ✅ FIX 1: Use correct header for Judge0 CE
            if (suluApiKey != null && !suluApiKey.trim().isEmpty()) {
                headers.set("X-Auth-Token", suluApiKey);
            }

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            // ✅ FIX 2: Use correct URL with /api prefix
            String submitUrl = judge0BaseUrl + "?base64_encoded=false&wait=true";

            // ✅ FIX 3: Parse as String first to handle response structure correctly
            ResponseEntity<String> response = restTemplate.exchange(
                    submitUrl,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RuntimeException("Failed to create submission: " + response.getStatusCode());
            }

            // ✅ FIX 4: Safely map JSON to our DTO
            Judge0Response result = objectMapper.readValue(response.getBody(), Judge0Response.class);

            // ✅ Log for debugging
            System.out.println("Judge0 response: " + result);

            return result;

        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception("Failed to execute code: " + e.getMessage());
        }
    }

    // Submit batch of submissions to Judge0 (matching the JavaScript implementation)
    public Judge0BatchResponse submitBatch(List<Judge0BatchSubmissionRequest> submissions) throws Exception {
        try {
            checkRateLimit();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // ✅ FIX 1: Use correct header for Judge0 CE
            if (suluApiKey != null && !suluApiKey.trim().isEmpty()) {
                headers.set("X-Auth-Token", suluApiKey);
            }

            HttpEntity<List<Judge0BatchSubmissionRequest>> request = new HttpEntity<>(submissions, headers);

            apiCallCount++;

            // ✅ FIX 5: Use correct batch URL with /api prefix
            ResponseEntity<String> response = restTemplate.exchange(
                    judge0BaseUrl.replace("/submissions", "") + "/api/submissions/batch?base64_encoded=false",
                    HttpMethod.POST,
                    request,
                    String.class
            );

            // Fixed: Accept both 200 (OK) and 201 (CREATED) as valid responses
            if ((response.getStatusCode() != HttpStatus.OK && 
                 response.getStatusCode() != HttpStatus.CREATED) || 
                 response.getBody() == null) {
                throw new RuntimeException("Failed to submit batch: " + response.getStatusCode());
            }

            // ✅ FIX 4: Safely map JSON to our DTO
            return objectMapper.readValue(response.getBody(), Judge0BatchResponse.class);
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception("Failed to submit batch: " + e.getMessage());
        }
    }
    
    // Poll all tokens until they are done (matching the JavaScript implementation)
    public List<Judge0Response> pollBatchResults(List<String> tokens) throws Exception {
        while (true) {
            try {
                HttpHeaders headers = new HttpHeaders();
                
                // ✅ FIX 1: Use correct header for Judge0 CE
                if (suluApiKey != null && !suluApiKey.trim().isEmpty()) {
                    headers.set("X-Auth-Token", suluApiKey);
                }
                
                // ✅ FIX 5: Use correct batch URL with /api prefix
                String batchUrl = judge0BaseUrl.replace("/submissions", "") + "/api/submissions/batch";
                
                ResponseEntity<String> response = restTemplate.exchange(
                        batchUrl + "?tokens=" + String.join(",", tokens) + "&base64_encoded=false",
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        String.class
                );
                
                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    // ✅ FIX 4: Safely map JSON to our DTO
                    Judge0BatchResponse batchResponse = objectMapper.readValue(response.getBody(), Judge0BatchResponse.class);
                    List<Judge0Response> results = batchResponse.getSubmissions();
                    
                    // Check if all submissions are done
                    boolean isAllDone = results.stream()
                            .allMatch(r -> r.getStatus().getId() != 1 && r.getStatus().getId() != 2);

                    if (isAllDone) return results;
                }
            } catch (Exception e) {
                System.err.println("Error polling batch results: " + e.getMessage());
            }

            // ✅ FIX 6: Increased delay to avoid rate limiting
            try {
                Thread.sleep(1500); // Increased to 1.5 seconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new Exception("Batch polling interrupted", e);
            }
        }
    }
    
    // Utility: split into chunks of max 20 for Judge0 batch (matching the JavaScript implementation)
    public <T> List<List<T>> chunkList(List<T> list, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Chunk size must be positive");
        }
        
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return result;
    }
    
    private void checkRateLimit() throws Exception {
        long currentTime = System.currentTimeMillis();
        
        // Reset counter if a minute has passed
        if (currentTime - lastResetTime > 60000) {
            apiCallCount = 0;
            lastResetTime = currentTime;
        }
        
        // Check if we've exceeded the rate limit
        if (apiCallCount >= MAX_CALLS_PER_MINUTE) {
            long timeToWait = 60000 - (currentTime - lastResetTime);
            throw new Exception("Rate limit exceeded. Please wait " + (timeToWait / 1000) + " seconds before trying again.");
        }
        
        // ✅ FIX 6: Increased delay to avoid rate limiting
        if (apiCallCount > 0) {
            try {
                Thread.sleep(1500); // Increased to 1.5 seconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public int getLanguageId(String language) {
        return switch (language.toLowerCase()) {
            case "python", "py" -> PYTHON_ID;
            case "java" -> JAVA_ID;
            case "cpp", "c++" -> CPP_ID;
            case "c" -> 50;
            case "javascript", "js" -> JAVASCRIPT_ID;
            case "go" -> GO_ID;
            case "typescript", "ts" -> TYPESCRIPT_ID;
            default -> PYTHON_ID; // default to Python
        };
    }
    
    // Get language name by ID (matching the JavaScript implementation)
    public String getLanguageName(int languageId) {
        return switch (languageId) {
            case PYTHON_ID -> "Python";
            case JAVASCRIPT_ID -> "JavaScript";
            case JAVA_ID -> "Java";
            case CPP_ID -> "C++";
            case GO_ID -> "Go";
            case TYPESCRIPT_ID -> "TypeScript";
            default -> "Unknown";
        };
    }
}