package SD_Tech.LeetAI.Controller;

import SD_Tech.LeetAI.DTO.GeminiRequest;
import SD_Tech.LeetAI.DTO.GeminiResponse;
import SD_Tech.LeetAI.Service.GeminiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/gemini")
public class GeminiController {

    private final GeminiService geminiService;

    @Autowired
    public GeminiController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    @PostMapping("/ask")
    public ResponseEntity<GeminiResponse> askGemini(@RequestBody GeminiRequest request) {
        String response = geminiService.callGemini(request.getPrompt());
        return ResponseEntity.ok(new GeminiResponse(response));
    }

    @PostMapping("/ask-with-system")
    public ResponseEntity<GeminiResponse> askGeminiWithSystem(
            @RequestParam String systemPrompt,
            @RequestBody GeminiRequest request) {
        String response = geminiService.callGeminiWithSystemPrompt(systemPrompt, request.getPrompt());
        return ResponseEntity.ok(new GeminiResponse(response));
    }
    
    @PostMapping("/explain")
    public ResponseEntity<GeminiResponse> explainTopic(@RequestBody GeminiRequest request) {
        String response = geminiService.getExplanation(request.getPrompt());
        return ResponseEntity.ok(new GeminiResponse(response));
    }
    
    @PostMapping("/guide")
    public ResponseEntity<GeminiResponse> getStepByStepGuide(@RequestBody GeminiRequest request) {
        String response = geminiService.getStepByStepGuide(request.getPrompt());
        return ResponseEntity.ok(new GeminiResponse(response));
    }
}