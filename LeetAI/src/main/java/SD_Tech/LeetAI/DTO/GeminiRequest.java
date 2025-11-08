package SD_Tech.LeetAI.DTO;

public class GeminiRequest {
    private String prompt;

    public GeminiRequest() {
        // Default constructor
    }

    public GeminiRequest(String prompt) {
        this.prompt = prompt;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }
}