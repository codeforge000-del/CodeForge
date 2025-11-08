package SD_Tech.LeetAI.DTO;

public class GeminiResponse {
    private String response;

    public GeminiResponse() {
        // Default constructor
    }

    public GeminiResponse(String response) {
        this.response = response;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }
}