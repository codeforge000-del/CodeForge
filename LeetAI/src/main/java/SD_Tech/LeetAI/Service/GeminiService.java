package SD_Tech.LeetAI.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import SD_Tech.LeetAI.DTO.Judge0Response;
import SD_Tech.LeetAI.Entity.CodeTemplate;
import SD_Tech.LeetAI.Entity.Problem;
import SD_Tech.LeetAI.Entity.TestCase;
import io.netty.resolver.DefaultAddressResolverGroup;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;
import reactor.util.retry.Retry;

@Service
public class GeminiService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiService.class);
    
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    
    @Autowired
    private Judge0Service judge0Service;
    
    public static final String[] SUPPORTED_LANGUAGES = {
        "Java", "Python", "C++", "JavaScript"
    };
    
    public static final List<String> SUPPORTED_LANGUAGES_LIST = Arrays.asList(SUPPORTED_LANGUAGES);
    
    private static final String DEFAULT_SYSTEM_PROMPT = 
            "You are a helpful AI assistant. Provide detailed, accurate answers with sufficient context. " +
            "Use clear formatting to enhance readability. Be thorough in your explanations while staying focused on the topic. " +
            "Include relevant examples when appropriate, but avoid unnecessary tangents. " +
            "Aim for responses that are comprehensive yet concise.";

    private static final String HACKERRANK_SYSTEM_PROMPT =
            "You are an expert problem setter for competitive programming platforms like HackerRank or LeetCode. " +
            "Generate a complete DSA problem in JSON format with the following fields: " +
            "title, description, difficulty, tags (array), example, constraints, explanation, codeTemplates. " +
            "The 'description' MUST be detailed and include: problem statement, input format, output format, and examples. " +
            "The 'example' must include at least 3 detailed examples with input, output, and explanation. " +
            "The 'constraints' must clearly specify input size limits, value ranges, and time complexity requirements. " +
            "The 'codeTemplates' field must be an array of objects, each with 'language' and 'code' fields. " +
            "Each code template must include only an incomplete function (with detailed comments) written in the specified language. " +
            "The function should be compilable but missing the actual logic. " +
            "Do not include the full solution. " +
            "CRITICAL: Ensure all fields are properly filled with meaningful content. Empty or null values are unacceptable. " +
            "You must generate code templates for ALL these languages: Java, Python, C++, JavaScript. " +
            "Do not omit any language. If you can't generate a valid template for a language, " +
            "include a placeholder comment instead of omitting it entirely. " +
            "IMPORTANT: Your response must be ONLY a valid JSON object. Do not include any additional text, explanations, or formatting.";

    private static final String TEST_CASE_SYSTEM_PROMPT = 
            "Generate exactly the requested number of challenging test cases as a JSON array. " +
            "Each object must have 'input' and 'expectedOutput' fields with non-empty string values. " +
            "Analyze the problem description, constraints, and examples to generate highly relevant test cases. " +
            "Include a variety of test cases: " +
            "1. Edge cases (minimum/maximum values, empty inputs, special characters) " +
            "2. Boundary conditions (values at the limits of constraints) " +
            "3. Complex scenarios (nested structures, large inputs, time complexity tests) " +
            "4. Algorithm-specific corner cases (e.g., for sorting: already sorted, reverse sorted, duplicates) " +
            "5. Performance test cases (large inputs that require optimal solutions) " +
            "Ensure test cases are logically correct and match the problem requirements. " +
            "Use standardized representations for data structures:\n" +
            "- Arrays: First line is size, second line is space-separated values\n" +
            "- Trees: Level-order traversal with 'null' for missing nodes\n" +
            "- Graphs: First line has vertices and edges count, subsequent lines have edges\n" +
            "- Strings: Enclosed in double quotes\n" +
            "- Numbers: Space-separated values\n" +
            "CRITICAL: Your response must be ONLY a valid JSON array. Do not include any additional text, explanations, or formatting. " +
            "Do NOT use mathematical notation like [A(1), B(2)]. Use proper JSON format only.";

    private static final String EXPLANATION_SYSTEM_PROMPT = 
            "You are an expert computer science educator. Provide a clear, detailed explanation of the requested topic. " +
            "Cover the key concepts thoroughly, including important details and context. " +
            "Use simple language and include a relevant example to illustrate the concept. " +
            "Ensure the explanation is comprehensive but not overly verbose.";

    private static final String GUIDE_SYSTEM_PROMPT = 
            "You are an expert technical instructor. Provide a detailed step-by-step guide for the requested task. " +
            "Each step should be clear, actionable, and include necessary context. " +
            "Explain the purpose of each step and provide tips for successful implementation. " +
            "Ensure the guide is thorough yet concise.";

    @Value("${spring.ai.vertex.ai.gemini.api-key}")
    private String apiKey;

    @Value("${spring.ai.vertex.ai.gemini.chat.options.model}")
    private String model;

    @Value("${app.proxy.host:}")
    private String proxyHost;

    @Value("${app.proxy.port:0}")
    private int proxyPort;
    
    @Value("${app.fallback.mode:false}")
    private boolean fallbackMode;

    public GeminiService() {
        try {
            // Create HttpClient without explicit timeouts
            HttpClient httpClient = HttpClient.create()
                    .resolver(DefaultAddressResolverGroup.INSTANCE);

            // Configure proxy if provided
            if (proxyHost != null && !proxyHost.isEmpty() && proxyPort > 0) {
                httpClient = httpClient.proxy(proxySpec -> proxySpec
                        .type(ProxyProvider.Proxy.HTTP)
                        .host(proxyHost)
                        .port(proxyPort));
                logger.info("Configured HTTP proxy: {}:{}", proxyHost, proxyPort);
            }

            // Create WebClient
            this.webClient = WebClient.builder()
                    .baseUrl("https://generativelanguage.googleapis.com")
                    .clientConnector(new ReactorClientHttpConnector(httpClient))
                    .build();

            this.objectMapper = new ObjectMapper();
            logger.info("GeminiService initialized successfully (fallback mode: {})", fallbackMode);

        } catch (Exception e) {
            logger.error("Error initializing GeminiService: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize GeminiService", e);
        }
    }

    public String callGemini(String promptText) {
        return callGeminiWithSystemPrompt(DEFAULT_SYSTEM_PROMPT, promptText);
    }

    public String callGeminiWithSystemPrompt(String systemPrompt, String userPrompt) {
        // If in fallback mode, return a predefined response
        if (fallbackMode) {
            logger.warn("Application is in fallback mode. Returning predefined response.");
            return "Service is currently unavailable. Please try again later.";
        }
        
        try {
            String requestBody = String.format(
                    "{\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":\"%s\"}]},{\"role\":\"user\",\"parts\":[{\"text\":\"%s\"}]}]}",
                    systemPrompt.replace("\"", "\\\""),
                    userPrompt.replace("\"", "\\\"")
            );

            Mono<String> responseMono = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1beta/models/" + model + ":generateContent")
                            .queryParam("key", apiKey)
                            .build())
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), 
                            response -> response.bodyToMono(String.class)
                                    .flatMap(errorBody -> Mono.error(new RuntimeException("API error: " + errorBody))))
                    .bodyToMono(String.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(3))
                            .maxBackoff(Duration.ofSeconds(15))
                            .jitter(0.75)
                            .filter(throwable -> isRecoverableError(throwable))
                            .doBeforeRetry(retrySignal -> 
                                logger.info("Retrying request to Gemini API: attempt {}", retrySignal.totalRetries() + 1))
                            .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> {
                                logger.error("Failed to connect to Gemini API after {} attempts", retrySignal.totalRetries() + 1);
                                return new RuntimeException("Failed to connect to Gemini API after " + (retrySignal.totalRetries() + 1) + " attempts");
                            }));

            String responseJson = responseMono.block();

            if (responseJson == null || responseJson.isEmpty()) {
                logger.error("Empty response from Gemini API");
                return "Error: Empty response from Gemini API";
            }

            JsonNode rootNode = objectMapper.readTree(responseJson);
            JsonNode candidatesNode = rootNode.path("candidates");
            if (candidatesNode.isArray() && candidatesNode.size() > 0) {
                JsonNode firstCandidate = candidatesNode.get(0);
                JsonNode contentNode = firstCandidate.path("content");
                JsonNode partsNode = contentNode.path("parts");
                if (partsNode.isArray() && partsNode.size() > 0) {
                    JsonNode firstPart = partsNode.get(0);
                    String responseText = firstPart.path("text").asText();
                    return formatResponse(responseText);
                }
            }

            return "No response from Gemini";
        } catch (WebClientRequestException e) {
            logger.error("Network error calling Gemini API: {}", e.getMessage(), e);
            return "Error: Network connection issue. Please check your internet connection.";
        } catch (WebClientResponseException e) {
            logger.error("HTTP error calling Gemini API: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return "Error: API returned error " + e.getStatusCode();
        } catch (Exception e) {
            logger.error("Error calling Gemini API: {}", e.getMessage(), e);
            return "Error: Could not get response from Gemini. " + e.getMessage();
        }
    }
    
    /**
     * Check if an exception is recoverable (should trigger a retry)
     */
    private boolean isRecoverableError(Throwable throwable) {
        // Retry on DNS resolution errors, connection timeouts, and 5xx server errors
        return throwable instanceof WebClientRequestException ||
               (throwable instanceof WebClientResponseException && 
                ((WebClientResponseException) throwable).getStatusCode().is5xxServerError());
    }
    
    private String formatResponse(String response) {
        // Preserve meaningful formatting while cleaning up excessive whitespace
        response = response.replaceAll("\\n{3,}", "\n\n");
        response = response.trim();
        // Remove markdown headers but preserve other formatting
        response = response.replaceAll("(^#+\\s+.+\\n\\n)", "");
        return response;
    }
    
    public String getExplanation(String topic) {
        return callGeminiWithSystemPrompt(EXPLANATION_SYSTEM_PROMPT, 
            "Provide a detailed explanation of: " + topic);
    }
    
    public String getStepByStepGuide(String task) {
        return callGeminiWithSystemPrompt(GUIDE_SYSTEM_PROMPT, 
            "Provide a step-by-step guide for: " + task);
    }
    
    public String generateDSAProblem(String topic, String difficulty) {
        String userPrompt = String.format(
            "Generate a complete DSA problem about %s with %s difficulty. " +
            "The problem should be suitable for a competitive programming platform. " +
            "Ensure the problem has a detailed description with input/output format, clear examples, and specific constraints. " +
            "CRITICAL: The description must be comprehensive and include: " +
            "1. A clear problem statement with sufficient context " +
            "2. Detailed input format specifications with examples " +
            "3. Detailed output format specifications with examples " +
            "4. At least 3 examples with input/output and explanations " +
            "5. Constraints section with time/space complexity requirements " +
            "6. Do not leave any fields empty or null. " +
            "You must generate code templates for ALL these languages: Java, Python, C++, JavaScript. " +
            "Each template should include detailed comments explaining the function signature and parameters. " +
            "Do not omit any language. If you can't generate a valid template for a language, " +
            "include a placeholder comment instead of omitting it entirely. " +
            "IMPORTANT: Your response must be ONLY a valid JSON object. Do not include any additional text, explanations, or formatting.",
            topic, difficulty
        );

        return callGeminiWithSystemPrompt(HACKERRANK_SYSTEM_PROMPT, userPrompt);
    }

    public GeneratedProblem parseGeneratedProblem(String generatedText, String difficulty) {
        try {
            logger.debug("Generated text from Gemini: {}", generatedText);

            // Check for DNS resolution errors
            if (generatedText.contains("Failed to resolve") || generatedText.contains("[A(")) {
                logger.error("Detected DNS resolution error in response: {}", generatedText);
                throw new RuntimeException("DNS resolution error: " + generatedText);
            }

            // Check if we're in fallback mode or got an error response
            if (generatedText.startsWith("Error:") || fallbackMode) {
                logger.warn("API error or fallback mode detected, generating default problem");
                return generateDefaultProblem(difficulty);
            }

            String jsonPart = extractJsonFromText(generatedText);
            
            if (jsonPart == null || jsonPart.trim().isEmpty()) {
                logger.error("Failed to extract JSON from generated text. Generated text length: {}", generatedText.length());
                return generateDefaultProblem(difficulty);
            }

            logger.debug("Extracted JSON: {}", jsonPart);

            jsonPart = fixJsonStringValues(jsonPart);

            JsonNode rootNode = objectMapper.readTree(jsonPart);
            GeneratedProblem problem = new GeneratedProblem();

            String title = rootNode.path("title").asText();
            if (title == null || title.trim().isEmpty()) {
                title = generateDefaultTitle(difficulty);
                logger.warn("Empty title detected, using default: {}", title);
            }
            problem.setTitle(title);

            String description = rootNode.path("description").asText();
            if (description == null || description.trim().isEmpty() || description.length() < 100) {
                logger.warn("Invalid description detected, generating comprehensive default");
                description = generateComprehensiveDefaultDescription(title, difficulty);
            }
            problem.setDescription(description);

            String parsedDifficulty = rootNode.path("difficulty").asText();
            if (parsedDifficulty == null || parsedDifficulty.trim().isEmpty()) {
                parsedDifficulty = difficulty;
            }
            problem.setDifficulty(parsedDifficulty);

            String example = rootNode.path("example").asText();
            if (example == null || example.trim().isEmpty()) {
                example = generateDetailedExamples(title, parsedDifficulty);
                logger.warn("Empty example detected, using default: {}", example);
            }
            problem.setExample(example);

            String constraints = rootNode.path("constraints").asText();
            if (constraints == null || constraints.trim().isEmpty()) {
                constraints = generateDetailedConstraints(parsedDifficulty);
                logger.warn("Empty constraints detected, using default: {}", constraints);
            }
            problem.setConstraints(constraints);

            problem.setExplanation(rootNode.path("explanation").asText());

            // Parse code templates with enhanced validation
            JsonNode codeTemplatesNode = rootNode.path("codeTemplates");
            Set<String> foundLanguages = new HashSet<>();
            
            if (codeTemplatesNode.isArray() && codeTemplatesNode.size() > 0) {
                logger.info("Processing {} code templates from AI response", codeTemplatesNode.size());
                for (JsonNode templateNode : codeTemplatesNode) {
                    String language = normalizeLanguageName(templateNode.path("language").asText());
                    String code = templateNode.path("code").asText();
                    
                    if (language != null && SUPPORTED_LANGUAGES_LIST.contains(language)) {
                        GeneratedCodeTemplate template = new GeneratedCodeTemplate();
                        template.setLanguage(language);
                        // If code is empty or null, generate a default one
                        if (code == null || code.trim().isEmpty()) {
                            logger.warn("Empty code for language: {}, generating default", language);
                            code = generateDetailedCodeTemplate(title, parsedDifficulty, language);
                        }
                        template.setCode(code);
                        problem.getCodeTemplates().add(template);
                        foundLanguages.add(language);
                    } else {
                        logger.warn("Invalid or unsupported language in template: {}", language);
                    }
                }
            } else {
                logger.warn("No code templates found in AI response");
            }
            
            // Ensure we have templates for ALL supported languages
            ensureAllLanguagesHaveTemplates(problem, title, parsedDifficulty);

            // Log final templates
            logger.info("Final code templates count: {}", problem.getCodeTemplates().size());
            problem.getCodeTemplates().forEach(t -> 
                logger.debug("Final template - Language: {}, Code length: {}", 
                    t.getLanguage(), t.getCode().length()));

            JsonNode tagsNode = rootNode.path("tags");
            if (tagsNode.isArray()) {
                for (JsonNode tagNode : tagsNode) {
                    String tag = tagNode.asText().trim();
                    if (!tag.isEmpty()) {
                        problem.getTags().add(tag);
                    }
                }
            } else if (tagsNode.isTextual()) {
                String tagsText = tagsNode.asText();
                String[] tagArray = tagsText.split(",");
                for (String tag : tagArray) {
                    tag = tag.trim();
                    if (!tag.isEmpty()) {
                        problem.getTags().add(tag);
                    }
                }
            }

            if (problem.getTags().isEmpty()) {
                generateDetailedTags(problem, parsedDifficulty);
            }

            logger.info("✅ Successfully parsed generated problem: {} [{}]", 
                         problem.getTitle(), parsedDifficulty);

            return problem;

        } catch (Exception e) {
            logger.error("❌ Failed to parse generated problem: {}", e.getMessage(), e);
            return generateDefaultProblem(difficulty);
        }
    }
    
    /**
     * Generate a default problem when API is unavailable
     */
    private GeneratedProblem generateDefaultProblem(String difficulty) {
        logger.warn("Generating default problem for difficulty: {}", difficulty);
        GeneratedProblem problem = new GeneratedProblem();
        
        problem.setTitle(generateDefaultTitle(difficulty));
        problem.setDescription(generateComprehensiveDefaultDescription(problem.getTitle(), difficulty));
        problem.setDifficulty(difficulty);
        problem.setExample(generateDetailedExamples(problem.getTitle(), difficulty));
        problem.setConstraints(generateDetailedConstraints(difficulty));
        problem.setExplanation("This is a default problem generated because the AI service is currently unavailable.");
        
        // Generate default code templates for all languages
        for (String language : SUPPORTED_LANGUAGES) {
            GeneratedCodeTemplate template = new GeneratedCodeTemplate();
            template.setLanguage(language);
            template.setCode(generateDetailedCodeTemplate(problem.getTitle(), difficulty, language));
            problem.getCodeTemplates().add(template);
        }
        
        generateDetailedTags(problem, difficulty);
        
        return problem;
    }

    // Helper method to ensure all languages have templates
    private void ensureAllLanguagesHaveTemplates(GeneratedProblem problem, String title, String difficulty) {
        Set<String> foundLanguages = problem.getCodeTemplates().stream()
                .map(GeneratedCodeTemplate::getLanguage)
                .collect(Collectors.toSet());
        
        for (String language : SUPPORTED_LANGUAGES) {
            if (!foundLanguages.contains(language)) {
                logger.warn("Missing template for language: {}, generating default", language);
                GeneratedCodeTemplate template = new GeneratedCodeTemplate();
                template.setLanguage(language);
                template.setCode(generateDetailedCodeTemplate(title, difficulty, language));
                problem.getCodeTemplates().add(template);
            }
        }
    }

    // Helper method to normalize language names
    private String normalizeLanguageName(String language) {
        if (language == null) return null;
        
        String normalized = language.trim().toLowerCase();
        switch (normalized) {
            case "java": return "Java";
            case "python": return "Python";
            case "c++":
            case "cpp": return "C++";
            case "javascript":
            case "js": return "JavaScript";
            default: return language;
        }
    }

    private String extractJsonFromText(String text) {
        Pattern jsonCodeBlockPattern = Pattern.compile("```json\\s*(\\{.*?\\})\\s*```", Pattern.DOTALL);
        Matcher matcher = jsonCodeBlockPattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        Pattern codeBlockPattern = Pattern.compile("```\\s*(\\{.*?\\})\\s*```", Pattern.DOTALL);
        matcher = codeBlockPattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return extractBalancedJson(text);
    }
    
    private String extractBalancedJson(String text) {
        int startIndex = -1;
        int endIndex = -1;
        int braceCount = 0;
        boolean inString = false;
        boolean escapeNext = false;
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            
            if (escapeNext) {
                escapeNext = false;
                continue;
            }
            
            if (c == '\\') {
                escapeNext = true;
                continue;
            }
            
            if (c == '"' && !escapeNext) {
                inString = !inString;
                continue;
            }
            
            if (!inString) {
                if (c == '{') {
                    if (braceCount == 0) {
                        startIndex = i;
                    }
                    braceCount++;
                } else if (c == '}') {
                    braceCount--;
                    if (braceCount == 0) {
                        endIndex = i;
                        break;
                    }
                }
            }
        }
        
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return text.substring(startIndex, endIndex + 1);
        }
        
        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');
        
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            return text.substring(firstBrace, lastBrace + 1);
        }
        
        return null;
    }

    public List<GeneratedTestCase> generateTestCasesForProblem(Problem problem, List<TestCase> existingTestCases, int count) {
        try {
            // If in fallback mode, generate default test cases
            if (fallbackMode) {
                logger.warn("Application is in fallback mode. Generating default test cases.");
                return generateDefaultTestCases(count);
            }
            
            // Analyze the problem to understand its nature
            ProblemAnalysis analysis = analyzeProblem(problem);
            
            // Generate test cases based on the problem analysis
            String generatedText = generateProblemSpecificTestCases(problem, analysis, count);
            logger.debug("Generated test cases text: {}", generatedText);
            
            List<GeneratedTestCase> generatedTestCases = parseGeneratedTestCases(generatedText, count);
            
            if (generatedTestCases.size() < count) {
                logger.warn("Only generated {} test cases out of requested {}, trying again", generatedTestCases.size(), count);
                String retryText = generateProblemSpecificTestCasesWithMoreDetails(problem, analysis, count - generatedTestCases.size());
                List<GeneratedTestCase> additionalTestCases = parseGeneratedTestCases(retryText, count - generatedTestCases.size());
                generatedTestCases.addAll(additionalTestCases);
            }
            
            // Validate test cases using Judge0 if reference solution is available
            List<GeneratedTestCase> validatedTestCases = new ArrayList<>();
            if (problem.getReferenceSolution() != null && problem.getReferenceLanguage() != null) {
                logger.info("Validating test cases using reference solution");
                
                for (GeneratedTestCase testCase : generatedTestCases) {
                    if (validatedTestCases.size() >= count) {
                        break;
                    }
                    
                    try {
                        // Validate the test case using Judge0
                        Judge0Response response = judge0Service.executeCode(
                            problem.getReferenceSolution(),
                            problem.getReferenceLanguage(),
                            testCase.getInput()
                        );
                        
                        // Check if the execution was successful and output matches
                        if (response != null && response.getStatus() != null && 
                            response.getStatus().getId() == 3 && response.getStdout() != null) {
                            String actualOutput = response.getStdout().trim();
                            String expectedOutput = testCase.getExpectedOutput().trim();
                            
                            if (actualOutput.equals(expectedOutput)) {
                                validatedTestCases.add(testCase);
                                logger.debug("Test case validated successfully");
                            } else {
                                logger.warn("Test case validation failed. Expected: {}, Actual: {}", expectedOutput, actualOutput);
                            }
                        } else {
                            logger.warn("Test case execution failed with status: {}", 
                                response != null ? response.getStatus() : "null");
                        }
                    } catch (Exception e) {
                        logger.error("Error validating test case: {}", e.getMessage());
                    }
                }
            }
            
            // If we don't have enough validated test cases, add the remaining without validation
            if (validatedTestCases.size() < count) {
                logger.warn("Only {} test cases validated out of requested {}, adding remaining without validation", 
                    validatedTestCases.size(), count);
                
                for (GeneratedTestCase testCase : generatedTestCases) {
                    if (validatedTestCases.size() >= count) {
                        break;
                    }
                    
                    // Check if this test case is already in validated list
                    if (!validatedTestCases.contains(testCase)) {
                        validatedTestCases.add(testCase);
                    }
                }
            }
            
            // If we still don't have enough, generate default ones
            if (validatedTestCases.size() < count) {
                logger.warn("Still only have {} test cases after validation, generating additional", validatedTestCases.size());
                int needed = count - validatedTestCases.size();
                List<GeneratedTestCase> additionalCases = generateDefaultTestCases(needed);
                
                for (GeneratedTestCase testCase : additionalCases) {
                    if (validatedTestCases.size() >= count) {
                        break;
                    }
                    
                    validatedTestCases.add(testCase);
                }
            }
            
            // Mark the first 2 test cases as visible, rest as hidden
            for (int i = 0; i < validatedTestCases.size(); i++) {
                validatedTestCases.get(i).setHidden(i >= 2);
            }
            
            logger.info("Final test case count: {}", validatedTestCases.size());
            return validatedTestCases;
        } catch (Exception e) {
            logger.error("Failed to generate test cases: {}", e.getMessage(), e);
            logger.warn("Falling back to default test cases generation");
            return generateDefaultTestCases(count);
        }
    }
    
    // Analyze the problem to understand its nature
    private ProblemAnalysis analyzeProblem(Problem problem) {
        ProblemAnalysis analysis = new ProblemAnalysis();
        
        String description = problem.getDescription().toLowerCase();
        String title = problem.getTitle().toLowerCase();
        String constraints = problem.getConstraints().toLowerCase();
        
        // Determine problem type
        if (description.contains("tree") || title.contains("tree")) {
            analysis.setProblemType(ProblemType.TREE);
        } else if (description.contains("graph") || title.contains("graph")) {
            analysis.setProblemType(ProblemType.GRAPH);
        } else if (description.contains("string") || title.contains("string")) {
            analysis.setProblemType(ProblemType.STRING);
        } else if (description.contains("array") || title.contains("array") || description.contains("list")) {
            analysis.setProblemType(ProblemType.ARRAY);
        } else if (description.contains("sort") || title.contains("sort")) {
            analysis.setProblemType(ProblemType.SORTING);
        } else if (description.contains("dynamic") || description.contains("dp") || title.contains("dp")) {
            analysis.setProblemType(ProblemType.DYNAMIC_PROGRAMMING);
        } else if (description.contains("search") || title.contains("search")) {
            analysis.setProblemType(ProblemType.SEARCH);
        } else {
            analysis.setProblemType(ProblemType.GENERIC);
        }
        
        // Extract constraints
        if (constraints.contains("10^6") || constraints.contains("1000000")) {
            analysis.setInputSize(InputSize.LARGE);
        } else if (constraints.contains("10^5") || constraints.contains("100000")) {
            analysis.setInputSize(InputSize.MEDIUM);
        } else {
            analysis.setInputSize(InputSize.SMALL);
        }
        
        // Determine difficulty
        analysis.setDifficulty(problem.getDifficulty());
        
        // Extract operation type
        if (description.contains("sum") || description.contains("add") || title.contains("sum")) {
            analysis.setOperationType(OperationType.SUM);
        } else if (description.contains("max") || description.contains("maximum") || title.contains("max")) {
            analysis.setOperationType(OperationType.MAX);
        } else if (description.contains("min") || description.contains("minimum") || title.contains("min")) {
            analysis.setOperationType(OperationType.MIN);
        } else if (description.contains("count") || title.contains("count")) {
            analysis.setOperationType(OperationType.COUNT);
        } else if (description.contains("find") || title.contains("find")) {
            analysis.setOperationType(OperationType.FIND);
        } else {
            analysis.setOperationType(OperationType.GENERIC);
        }
        
        return analysis;
    }
    
    // Generate problem-specific test cases
    private String generateProblemSpecificTestCases(Problem problem, ProblemAnalysis analysis, int count) {
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Generate ").append(count).append(" unique and challenging test cases for this programming problem:\n\n");
        userPrompt.append("Title: ").append(problem.getTitle()).append("\n\n");
        userPrompt.append("Description: ").append(problem.getDescription()).append("\n\n");
        userPrompt.append("Constraints: ").append(problem.getConstraints()).append("\n\n");
        userPrompt.append("Example: ").append(problem.getExample()).append("\n\n");
        
        // Add problem analysis context
        userPrompt.append("Problem Analysis:\n");
        userPrompt.append("- Type: ").append(analysis.getProblemType()).append("\n");
        userPrompt.append("- Difficulty: ").append(analysis.getDifficulty()).append("\n");
        userPrompt.append("- Input Size: ").append(analysis.getInputSize()).append("\n");
        userPrompt.append("- Operation: ").append(analysis.getOperationType()).append("\n\n");
        
        // Include code template for reference
        userPrompt.append("Code Template (Java):\n");
        String javaTemplate = problem.getCodeTemplates().stream()
            .filter(t -> t.getLanguage().equalsIgnoreCase("Java"))
            .map(CodeTemplate::getCode)
            .findFirst()
            .orElse("No template available");
        userPrompt.append(javaTemplate).append("\n\n");
        
        userPrompt.append("Requirements:\n");
        userPrompt.append("1. Each test case MUST have non-empty 'input' and 'expectedOutput' fields\n");
        userPrompt.append("2. Generate test cases that are specific to this ").append(analysis.getProblemType()).append(" problem\n");
        userPrompt.append("3. Include a variety of test cases:\n");
        userPrompt.append("   - Edge cases (minimum/maximum values, empty inputs, special characters)\n");
        userPrompt.append("   - Boundary conditions (values at the limits of constraints)\n");
        userPrompt.append("   - Complex scenarios (nested structures, large inputs, time complexity tests)\n");
        userPrompt.append("   - Algorithm-specific corner cases (e.g., for sorting: already sorted, reverse sorted, duplicates)\n");
        userPrompt.append("   - Performance test cases (large inputs that require optimal solutions)\n");
        userPrompt.append("4. Ensure inputs and outputs match the format shown in the example.\n");
        userPrompt.append("5. Return as a JSON array with 'input' and 'expectedOutput' fields.\n");
        userPrompt.append("6. Make sure test cases are logically correct for this problem.\n");
        userPrompt.append("7. CRITICAL: Do not generate empty or null values for input or output.\n");
        userPrompt.append("8. IMPORTANT: Your response must be ONLY a valid JSON array. Do not include any additional text or formatting.\n");
        userPrompt.append("9. Example of expected JSON format:\n");
        userPrompt.append("   [\n");
        userPrompt.append("   {\n");
        userPrompt.append("     \"input\": \"1\\n1\",\n");
        userPrompt.append("     \"expectedOutput\": \"1\"\n");
        userPrompt.append("   },\n");
        userPrompt.append("   {\n");
        userPrompt.append("     \"input\": \"2\\n1 2\",\n");
        userPrompt.append("     \"expectedOutput\": \"3\"\n");
        userPrompt.append("   }\n");
        userPrompt.append("   ]\n");
        userPrompt.append("10. CRITICAL: Do NOT use mathematical notation like [A(1), B(2)]. Use proper JSON format only.\n");
        
        if (analysis.getDifficulty().equalsIgnoreCase("HARD")) {
            userPrompt.append("11. Include at least one test case that tests the worst-case time complexity.\n");
        }
        
        // Add specific instructions based on problem type
        switch (analysis.getProblemType()) {
            case TREE:
                userPrompt.append("12. For tree problems, include test cases with:\n");
                userPrompt.append("    - Empty trees\n");
                userPrompt.append("    - Single node trees\n");
                userPrompt.append("    - Balanced trees (both small and large)\n");
                userPrompt.append("    - Unbalanced trees (left-heavy and right-heavy)\n");
                userPrompt.append("    - Trees with negative values\n");
                userPrompt.append("    - Trees with duplicate values\n");
                break;
            case GRAPH:
                userPrompt.append("12. For graph problems, include test cases with:\n");
                userPrompt.append("    - Empty graphs\n");
                userPrompt.append("    - Single node graphs\n");
                userPrompt.append("    - Connected graphs (both sparse and dense)\n");
                userPrompt.append("    - Disconnected graphs\n");
                userPrompt.append("    - Graphs with cycles\n");
                userPrompt.append("    - Directed and undirected graphs\n");
                break;
            case STRING:
                userPrompt.append("12. For string problems, include test cases with:\n");
                userPrompt.append("    - Empty strings\n");
                userPrompt.append("    - Single character strings\n");
                userPrompt.append("    - Strings with special characters\n");
                userPrompt.append("    - Palindromic strings\n");
                userPrompt.append("    - Very long strings (testing performance)\n");
                userPrompt.append("    - Strings with repeated characters\n");
                break;
            case ARRAY:
                userPrompt.append("12. For array problems, include test cases with:\n");
                userPrompt.append("    - Empty arrays\n");
                userPrompt.append("    - Single element arrays\n");
                userPrompt.append("    - Arrays with negative numbers\n");
                userPrompt.append("    - Arrays with duplicates\n");
                userPrompt.append("    - Large arrays (testing performance)\n");
                userPrompt.append("    - Arrays with mixed data types if applicable\n");
                break;
            case SORTING:
                userPrompt.append("12. For sorting problems, include test cases with:\n");
                userPrompt.append("    - Already sorted arrays\n");
                userPrompt.append("    - Reverse sorted arrays\n");
                userPrompt.append("    - Arrays with duplicates\n");
                userPrompt.append("    - Large arrays (testing performance)\n");
                userPrompt.append("    - Arrays with negative and positive numbers\n");
                break;
            case DYNAMIC_PROGRAMMING:
                userPrompt.append("12. For DP problems, include test cases with:\n");
                userPrompt.append("    - Small input cases (base cases)\n");
                userPrompt.append("    - Cases with negative numbers\n");
                userPrompt.append("    - Large input cases (testing performance)\n");
                userPrompt.append("    - Cases that require optimal substructure\n");
                break;
        }
        
        return callGeminiWithSystemPrompt(TEST_CASE_SYSTEM_PROMPT, userPrompt.toString());
    }
    
    // Generate test cases with more details
    private String generateProblemSpecificTestCasesWithMoreDetails(Problem problem, ProblemAnalysis analysis, int count) {
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Generate ").append(count).append(" additional unique and challenging test cases for this programming problem:\n\n");
        userPrompt.append("Title: ").append(problem.getTitle()).append("\n\n");
        userPrompt.append("Description: ").append(problem.getDescription()).append("\n\n");
        userPrompt.append("Constraints: ").append(problem.getConstraints()).append("\n\n");
        userPrompt.append("Example: ").append(problem.getExample()).append("\n\n");
        
        // Add problem analysis context
        userPrompt.append("Problem Analysis:\n");
        userPrompt.append("- Type: ").append(analysis.getProblemType()).append("\n");
        userPrompt.append("- Difficulty: ").append(analysis.getDifficulty()).append("\n");
        userPrompt.append("- Input Size: ").append(analysis.getInputSize()).append("\n");
        userPrompt.append("- Operation: ").append(analysis.getOperationType()).append("\n\n");
        
        // Include all code templates
        userPrompt.append("Code Templates:\n");
        for (String language : SUPPORTED_LANGUAGES) {
            userPrompt.append("\n").append(language).append(":\n");
            String template = problem.getCodeTemplates().stream()
                .filter(t -> t.getLanguage().equalsIgnoreCase(language))
                .map(CodeTemplate::getCode)
                .findFirst()
                .orElse("No template available");
            userPrompt.append(template).append("\n");
        }
        
        userPrompt.append("\nRequirements:\n");
        userPrompt.append("1. Each test case MUST have non-empty 'input' and 'expectedOutput' fields\n");
        userPrompt.append("2. Generate test cases that are specific to this ").append(analysis.getProblemType()).append(" problem\n");
        userPrompt.append("3. Include more complex and challenging test cases than before\n");
        userPrompt.append("4. Ensure inputs and outputs match the format shown in the example\n");
        userPrompt.append("5. Return as a JSON array with 'input' and 'expectedOutput' fields\n");
        userPrompt.append("6. Make sure test cases are logically correct for this problem\n");
        userPrompt.append("7. CRITICAL: Do not generate empty or null values for input or output\n");
        userPrompt.append("8. IMPORTANT: Your response must be ONLY a valid JSON array. Do not include any additional text or formatting\n");
        userPrompt.append("9. Example of expected JSON format:\n");
        userPrompt.append("   [\n");
        userPrompt.append("   {\n");
        userPrompt.append("     \"input\": \"1\\n1\",\n");
        userPrompt.append("     \"expectedOutput\": \"1\"\n");
        userPrompt.append("   },\n");
        userPrompt.append("   {\n");
        userPrompt.append("     \"input\": \"2\\n1 2\",\n");
        userPrompt.append("     \"expectedOutput\": \"3\"\n");
        userPrompt.append("   }\n");
        userPrompt.append("   ]\n");
        userPrompt.append("10. CRITICAL: Do NOT use mathematical notation like [A(1), B(2)]. Use proper JSON format only.\n");
        
        return callGeminiWithSystemPrompt(TEST_CASE_SYSTEM_PROMPT, userPrompt.toString());
    }
    
    private List<GeneratedTestCase> parseGeneratedTestCases(String generatedText, int expectedCount) {
        try {
            // Check for DNS resolution errors
            if (generatedText.contains("Failed to resolve") || generatedText.contains("[A(")) {
                logger.warn("Detected DNS resolution error in response: {}", generatedText);
                return generateDefaultTestCases(expectedCount);
            }

            // Check if we got an error response
            if (generatedText.startsWith("Error:")) {
                logger.warn("Error response detected: {}", generatedText);
                return generateDefaultTestCases(expectedCount);
            }

            String jsonPart = extractJsonArrayFromText(generatedText);
            
            if (jsonPart == null || jsonPart.trim().isEmpty()) {
                logger.warn("No valid JSON array found in generated response. Attempting to extract and convert test cases.");
                return extractAndConvertTestCases(generatedText, expectedCount);
            }

            logger.debug("Extracted JSON for test cases: {}", jsonPart);
            
            JsonNode arrayNode = objectMapper.readTree(jsonPart);

            List<GeneratedTestCase> testCases = new ArrayList<>();
            for (int i = 0; i < arrayNode.size(); i++) {
                JsonNode testCaseNode = arrayNode.get(i);
                GeneratedTestCase testCase = new GeneratedTestCase();
                
                String input = testCaseNode.path("input").asText();
                String expectedOutput = testCaseNode.path("expectedOutput").asText();
                
                if ((input == null || input.trim().isEmpty()) && 
                    (expectedOutput == null || expectedOutput.trim().isEmpty())) {
                    logger.warn("Test case {} has empty input and output, skipping", i);
                    continue;
                }
                
                if (input == null || input.trim().isEmpty()) {
                    input = "1";
                    logger.warn("Test case {} has empty input, using default: {}", i, input);
                }
                
                if (expectedOutput == null || expectedOutput.trim().isEmpty()) {
                    expectedOutput = "0";
                    logger.warn("Test case {} has empty output, using default: {}", i, expectedOutput);
                }
                
                testCase.setInput(input);
                testCase.setExpectedOutput(expectedOutput);
                testCases.add(testCase);
            }
            
            if (testCases.size() < expectedCount) {
                logger.warn("Only generated {} test cases out of requested {}", testCases.size(), expectedCount);
            }
            
            return testCases;
        } catch (Exception e) {
            logger.error("Failed to parse generated test cases as JSON: {}", e.getMessage(), e);
            logger.info("Attempting to extract and convert test cases from text format.");
            return extractAndConvertTestCases(generatedText, expectedCount);
        }
    }
    
    /**
     * Extract test cases from text format and convert them to proper format
     * This handles cases where AI returns test cases in a non-JSON format like "[A(1), AAAA(28)]"
     */
    private List<GeneratedTestCase> extractAndConvertTestCases(String generatedText, int expectedCount) {
        List<GeneratedTestCase> testCases = new ArrayList<>();
        
        try {
            // Try to extract test cases from common non-JSON formats
            // Format 1: [Input1, Output1], [Input2, Output2], ...
            Pattern testCasePattern = Pattern.compile("\\[(.*?)\\]");
            Matcher matcher = testCasePattern.matcher(generatedText);
            
            int count = 0;
            while (matcher.find() && count < expectedCount) {
                String testCaseStr = matcher.group(1);
                String[] parts = testCaseStr.split(",\\s*");
                
                if (parts.length >= 2) {
                    GeneratedTestCase testCase = new GeneratedTestCase();
                    
                    // Handle different input formats
                    String input = parts[0].trim();
                    String output = parts[1].trim();
                    
                    // If input looks like "A(1)", convert to proper format
                    if (input.matches(".+\\(\\d+\\)")) {
                        input = input.replaceAll(".+\\((\\d+)\\)", "$1");
                    }
                    
                    // If output looks like "AAAA(28)", convert to proper format
                    if (output.matches(".+\\(\\d+\\)")) {
                        output = output.replaceAll(".+\\((\\d+)\\)", "$1");
                    }
                    
                    testCase.setInput(input);
                    testCase.setExpectedOutput(output);
                    testCase.setHidden(count >= 2); // First 2 are visible, rest are hidden
                    
                    testCases.add(testCase);
                    count++;
                }
            }
            
            if (testCases.size() > 0) {
                logger.info("Successfully extracted {} test cases from text format", testCases.size());
                return testCases;
            }
            
            // If we still don't have test cases, generate default ones
            logger.warn("Could not extract test cases from text, generating default ones");
            return generateDefaultTestCases(expectedCount);
            
        } catch (Exception e) {
            logger.error("Failed to extract test cases from text: {}", e.getMessage(), e);
            return generateDefaultTestCases(expectedCount);
        }
    }
    
    /**
     * Generate default test cases as a fallback
     */
    private List<GeneratedTestCase> generateDefaultTestCases(int count) {
        List<GeneratedTestCase> testCases = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            GeneratedTestCase testCase = new GeneratedTestCase();
            
            // Generate unique test cases by using different input values
            switch (i) {
                case 0:
                    testCase.setInput("1\n1");
                    testCase.setExpectedOutput("1");
                    break;
                case 1:
                    testCase.setInput("2\n1 2");
                    testCase.setExpectedOutput("3");
                    break;
                case 2:
                    testCase.setInput("3\n1 2 3");
                    testCase.setExpectedOutput("6");
                    break;
                case 3:
                    testCase.setInput("4\n-1 0 1 2");
                    testCase.setExpectedOutput("2");
                    break;
                case 4:
                    testCase.setInput("5\n10 20 30 40 50");
                    testCase.setExpectedOutput("150");
                    break;
                case 5:
                    testCase.setInput("6\n1 1 1 1 1 1");
                    testCase.setExpectedOutput("6");
                    break;
                case 6:
                    testCase.setInput("7\n-1 -2 -3 -4 -5 -6 -7");
                    testCase.setExpectedOutput("-28");
                    break;
                case 7:
                    testCase.setInput("8\n100 200 300 400 500 600 700 800");
                    testCase.setExpectedOutput("3600");
                    break;
                case 8:
                    testCase.setInput("9\n0 0 0 0 0 0 0 0 0");
                    testCase.setExpectedOutput("0");
                    break;
                case 9:
                    testCase.setInput("10\n1 2 3 4 5 6 7 8 9 10");
                    testCase.setExpectedOutput("55");
                    break;
                default:
                    // For any additional test cases, generate unique values
                    int size = i + 1;
                    StringBuilder inputBuilder = new StringBuilder();
                    inputBuilder.append(size).append("\n");
                    int sum = 0;
                    for (int j = 0; j < size; j++) {
                        int value = (j + 1) * 10; // Ensure unique values
                        inputBuilder.append(value);
                        if (j < size - 1) {
                            inputBuilder.append(" ");
                        }
                        sum += value;
                    }
                    testCase.setInput(inputBuilder.toString());
                    testCase.setExpectedOutput(String.valueOf(sum));
            }
            
            testCase.setHidden(i >= 2);
            testCases.add(testCase);
        }
        
        return testCases;
    }
    
    private String extractJsonArrayFromText(String text) {
        Pattern jsonArrayCodeBlockPattern = Pattern.compile("```json\\s*(\\[.*?\\])\\s*```", Pattern.DOTALL);
        Matcher matcher = jsonArrayCodeBlockPattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        Pattern codeBlockPattern = Pattern.compile("```\\s*(\\[.*?\\])\\s*```", Pattern.DOTALL);
        matcher = codeBlockPattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return extractBalancedJsonArray(text);
    }
    
    private String extractBalancedJsonArray(String text) {
        int startIndex = -1;
        int endIndex = -1;
        int bracketCount = 0;
        boolean inString = false;
        boolean escapeNext = false;
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            
            if (escapeNext) {
                escapeNext = false;
                continue;
            }
            
            if (c == '\\') {
                escapeNext = true;
                continue;
            }
            
            if (c == '"' && !escapeNext) {
                inString = !inString;
                continue;
            }
            
            if (!inString) {
                if (c == '[') {
                    if (bracketCount == 0) {
                        startIndex = i;
                    }
                    bracketCount++;
                } else if (c == ']') {
                    bracketCount--;
                    if (bracketCount == 0) {
                        endIndex = i;
                        break;
                    }
                }
            }
        }
        
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return text.substring(startIndex, endIndex + 1);
        }
        
        int firstBracket = text.indexOf('[');
        int lastBracket = text.lastIndexOf(']');
        
        if (firstBracket != -1 && lastBracket != -1 && lastBracket > firstBracket) {
            return text.substring(firstBracket, lastBracket + 1);
        }
        
        return null;
    }
    
    private String fixJsonStringValues(String json) {
        StringBuilder fixedJson = new StringBuilder();
        boolean inString = false;
        boolean escapeNext = false;
        
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            
            if (escapeNext) {
                fixedJson.append(c);
                escapeNext = false;
                continue;
            }
            
            if (c == '\\') {
                fixedJson.append(c);
                escapeNext = true;
                continue;
            }
            
            if (c == '"') {
                fixedJson.append(c);
                inString = !inString;
                continue;
            }
            
            if (inString) {
                switch (c) {
                    case '\n':
                        fixedJson.append("\\n");
                        break;
                    case '\r':
                        fixedJson.append("\\r");
                        break;
                    case '\t':
                        fixedJson.append("\\t");
                        break;
                    case '\b':
                        fixedJson.append("\\b");
                        break;
                    case '\f':
                        fixedJson.append("\\f");
                        break;
                    default:
                        if (c < 32) {
                            fixedJson.append(String.format("\\u%04x", (int) c));
                        } else {
                            fixedJson.append(c);
                        }
                }
            } else {
                fixedJson.append(c);
            }
        }
        
        return fixedJson.toString();
    }
    
    private String generateDefaultTitle(String difficulty) {
        switch (difficulty.toUpperCase()) {
            case "EASY":
                return "Array Sum Problem";
            case "MEDIUM":
                return "Dynamic Programming Challenge";
            case "HARD":
                return "Advanced Graph Algorithm";
            default:
                return "Programming Problem";
        }
    }
    
    private String generateComprehensiveDefaultDescription(String title, String difficulty) {
        StringBuilder sb = new StringBuilder();
        sb.append("Problem Statement:\n");
        sb.append("You are given a programming problem titled '").append(title).append("'. ");
        sb.append("Your task is to implement an efficient solution that meets the specified requirements.\n\n");
        
        sb.append("Input Format:\n");
        sb.append("The input consists of multiple lines of data. The first line contains an integer N, ");
        sb.append("representing the size of the input. The subsequent lines contain the actual data to be processed.\n\n");
        
        sb.append("Output Format:\n");
        sb.append("Your solution should output the result as specified in the problem statement. ");
        sb.append("Ensure your output format matches exactly what is required.\n\n");
        
        sb.append("Examples:\n");
        sb.append("Sample Input 0:\n3\n1 2 3\n");
        sb.append("Sample Output 0:\n6\n");
        sb.append("Explanation 0:\nThe sum of the elements is 1+2+3=6.\n\n");
        
        sb.append("Sample Input 1:\n4\n5 6 7 8\n");
        sb.append("Sample Output 1:\n26\n");
        sb.append("Explanation 1:\nThe sum of the elements is 5+6+7+8=26.\n\n");
        
        sb.append("Constraints:\n");
        sb.append("1 ≤ N ≤ 10^5\n");
        sb.append("Time complexity: O(N) or O(N log N)\n");
        sb.append("Space complexity: O(1) or O(N)\n");
        
        return sb.toString();
    }
    
    private String generateDetailedExamples(String title, String difficulty) {
        StringBuilder sb = new StringBuilder();
        sb.append("Sample Input 0:\n3\n1 2 3\n");
        sb.append("Sample Output 0:\n6\n");
        sb.append("Explanation 0:\nThe sum of the elements is 1+2+3=6.\n\n");
        
        sb.append("Sample Input 1:\n4\n5 6 7 8\n");
        sb.append("Sample Output 1:\n26\n");
        sb.append("Explanation 1:\nThe sum of the elements is 5+6+7+8=26.\n\n");
        
        if (difficulty.equalsIgnoreCase("MEDIUM") || difficulty.equalsIgnoreCase("HARD")) {
            sb.append("Sample Input 2:\n5\n10 20 30 40 50\n");
            sb.append("Sample Output 2:\n150\n");
            sb.append("Explanation 2:\nThe sum of the elements is 10+20+30+40+50=150.\n");
        }
        
        return sb.toString();
    }
    
    private String generateDetailedConstraints(String difficulty) {
        StringBuilder sb = new StringBuilder();
        
        switch (difficulty.toUpperCase()) {
            case "EASY":
                sb.append("1 ≤ N ≤ 100\n");
                sb.append("Each element in the array satisfies: 1 ≤ arr[i] ≤ 100\n");
                sb.append("Time complexity: O(N)\n");
                sb.append("Space complexity: O(1)\n");
                break;
            case "MEDIUM":
                sb.append("1 ≤ N ≤ 10^5\n");
                sb.append("Each element in the array satisfies: -10^9 ≤ arr[i] ≤ 10^9\n");
                sb.append("Time complexity: O(N log N)\n");
                sb.append("Space complexity: O(N)\n");
                break;
            case "HARD":
                sb.append("1 ≤ N ≤ 10^6\n");
                sb.append("Each element in the array satisfies: -10^18 ≤ arr[i] ≤ 10^18\n");
                sb.append("Time complexity: O(N) or O(N log N)\n");
                sb.append("Space complexity: O(1) or O(N)\n");
                break;
            default:
                sb.append("1 ≤ N ≤ 100\n");
                sb.append("Time complexity: O(N)\n");
        }
        
        sb.append("Your solution must pass all test cases within the given constraints.");
        return sb.toString();
    }
    
    public String generateDetailedCodeTemplate(String title, String difficulty, String language) {
        String lowerLang = language.toLowerCase();
        
        switch (lowerLang) {
            case "java":
                return "import java.util.*;\n\n" +
                       "/**\n" +
                       " * Solution for " + title + "\n" +
                       " * Difficulty: " + difficulty + "\n" +
                       " * \n" +
                       " * This class provides a solution to the given problem. The implementation\n" +
                       " * should handle all edge cases and constraints specified in the problem statement.\n" +
                       " */\n" +
                       "public class Solution {\n" +
                       "    /**\n" +
                       "     * TODO: Implement your solution here\n" +
                       "     * \n" +
                       "     * @param arr Input array as specified in the problem\n" +
                       "     * @return The computed result according to the problem requirements\n" +
                       "     * \n" +
                       "     * This method should implement the core logic to solve the problem.\n" +
                       "     * Consider time and space complexity constraints.\n" +
                       "     */\n" +
                       "    public int solve(int[] arr) {\n" +
                       "        // Your implementation here\n" +
                       "        return 0;\n" +
                       "    }\n" +
                       "}";

            case "python":
                return "\"\"\"\n" +
                       "Solution for " + title + "\n" +
                       "Difficulty: " + difficulty + "\n" +
                       "\"\"\"\n\n" +
                       "def solve(arr):\n" +
                       "    \"\"\"\n" +
                       "    TODO: Implement your solution here\n" +
                       "    \n" +
                       "    Args:\n" +
                       "        arr: Input array as specified in the problem\n" +
                       "        \n" +
                       "    Returns:\n" +
                       "        The computed result according to the problem requirements\n" +
                       "        \n" +
                       "    This function should implement the core logic to solve the problem.\n" +
                       "    Consider time and space complexity constraints.\n" +
                       "    \"\"\"\n" +
                       "    # Your implementation here\n" +
                       "    return 0";

            case "cpp":
            case "c++":
                return "#include <bits/stdc++.h>\n" +
                       "using namespace std;\n\n" +
                       "/**\n" +
                       " * Solution for " + title + "\n" +
                       " * Difficulty: " + difficulty + "\n" +
                       " * \n" +
                       " * This file provides a solution to the given problem. The implementation\n" +
                       " * should handle all edge cases and constraints specified in the problem statement.\n" +
                       " */\n" +
                       "/**\n" +
                       " * TODO: Implement your solution here\n" +
                       " * \n" +
                       " * @param arr Input vector as specified in the problem\n" +
                       " * @return The computed result according to the problem requirements\n" +
                       " * \n" +
                       " * This function should implement the core logic to solve the problem.\n" +
                       " * Consider time and space complexity constraints.\n" +
                       " */\n" +
                       "int solve(vector<int>& arr) {\n" +
                       "    // Your implementation here\n" +
                       "    return 0;\n" +
                       "}\n\n" +
                       "int main() {\n" +
                       "    // Read input\n" +
                       "    int n;\n" +
                       "    cin >> n;\n" +
                       "    vector<int> arr(n);\n" +
                       "    for (int i = 0; i < n; i++) {\n" +
                       "        cin >> arr[i];\n" +
                       "    }\n" +
                       "    \n" +
                       "    // Compute and output result\n" +
                       "    cout << solve(arr) << endl;\n" +
                       "    \n" +
                       "    return 0;\n" +
                       "}";

            case "javascript":
            case "js":
                return "/**\n" +
                       " * Solution for " + title + "\n" +
                       " * Difficulty: " + difficulty + "\n" +
                       " * \n" +
                       " * This file provides a solution to the given problem. The implementation\n" +
                       " * should handle all edge cases and constraints specified in the problem statement.\n" +
                       " */\n\n" +
                       "/**\n" +
                       " * TODO: Implement your solution here\n" +
                       " * \n" +
                       " * @param {Array<number>} arr Input array as specified in the problem\n" +
                       " * @return {number} The computed result according to the problem requirements\n" +
                       " * \n" +
                       " * This function should implement the core logic to solve the problem.\n" +
                       " * Consider time and space complexity constraints.\n" +
                       " */\n" +
                       "function solve(arr) {\n" +
                       "    // Your implementation here\n" +
                       "    return 0;\n" +
                       "}\n\n" +
                       "// Example usage:\n" +
                       "// console.log(solve([1, 2, 3])); // Output: 6";

            default:
                return "// Solution for " + title + "\n" +
                       "// Difficulty: " + difficulty + "\n\n" +
                       "// TODO: Implement your solution here\n" +
                       "// Consider time and space complexity constraints.\n";
        }
    }

    private void generateDetailedTags(GeneratedProblem problem, String difficulty) {
        switch (difficulty.toUpperCase()) {
            case "EASY":
                problem.getTags().add("Arrays");
                problem.getTags().add("Implementation");
                problem.getTags().add("Basic Programming");
                break;
            case "MEDIUM":
                problem.getTags().add("Dynamic Programming");
                problem.getTags().add("Algorithms");
                problem.getTags().add("Data Structures");
                break;
            case "HARD":
                problem.getTags().add("Graph Theory");
                problem.getTags().add("Advanced Algorithms");
                problem.getTags().add("Advanced Data Structures");
                break;
            default:
                problem.getTags().add("Problem Solving");
                problem.getTags().add("Algorithms");
        }
    }
    
    // Problem analysis enums and classes
    private enum ProblemType {
        TREE, GRAPH, STRING, ARRAY, SORTING, DYNAMIC_PROGRAMMING, SEARCH, GENERIC
    }
    
    private enum InputSize {
        SMALL, MEDIUM, LARGE
    }
    
    private enum OperationType {
        SUM, MAX, MIN, COUNT, FIND, GENERIC
    }
    
    private static class ProblemAnalysis {
        private ProblemType problemType;
        private InputSize inputSize;
        private String difficulty;
        private OperationType operationType;
        
        public ProblemType getProblemType() { return problemType; }
        public void setProblemType(ProblemType problemType) { this.problemType = problemType; }
        
        public InputSize getInputSize() { return inputSize; }
        public void setInputSize(InputSize inputSize) { this.inputSize = inputSize; }
        
        public String getDifficulty() { return difficulty; }
        public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
        
        public OperationType getOperationType() { return operationType; }
        public void setOperationType(OperationType operationType) { this.operationType = operationType; }
    }
    
    public static class GeneratedProblem {
        private String title;
        private String description;
        private String difficulty;
        private String example;
        private String constraints;
        private String explanation;
        private java.util.List<GeneratedCodeTemplate> codeTemplates = new java.util.ArrayList<>();
        private java.util.List<String> tags = new java.util.ArrayList<>();
        
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public String getDifficulty() { return difficulty; }
        public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
        
        public String getExample() { return example; }
        public void setExample(String example) { this.example = example; }
        
        public String getConstraints() { return constraints; }
        public void setConstraints(String constraints) { this.constraints = constraints; }
        
        public String getExplanation() { return explanation; }
        public void setExplanation(String explanation) { this.explanation = explanation; }
        
        public java.util.List<GeneratedCodeTemplate> getCodeTemplates() { return codeTemplates; }
        public void setCodeTemplates(java.util.List<GeneratedCodeTemplate> codeTemplates) { this.codeTemplates = codeTemplates; }
        
        public java.util.List<String> getTags() { return tags; }
        public void setTags(java.util.List<String> tags) { this.tags = tags; }
    }
    
    public static class GeneratedCodeTemplate {
        private String language;
        private String code;
        
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
    }
    
    public static class GeneratedTestCase {
        private String input;
        private String expectedOutput;
        private boolean hidden;
        
        public String getInput() { return input; }
        public void setInput(String input) { this.input = input; }
        
        public String getExpectedOutput() { return expectedOutput; }
        public void setExpectedOutput(String expectedOutput) { this.expectedOutput = expectedOutput; }
        
        public boolean isHidden() { return hidden; }
        public void setHidden(boolean hidden) { this.hidden = hidden; }
    }
    
    public String generateSolution(String title, String description, String difficulty, String language) {
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Generate a complete solution in ").append(language).append(" for this programming problem:\n\n");
        userPrompt.append("Title: ").append(title).append("\n\n");
        userPrompt.append("Description: ").append(description).append("\n\n");
        userPrompt.append("Difficulty: ").append(difficulty).append("\n\n");
        
        userPrompt.append("Requirements:\n");
        userPrompt.append("1. Provide a complete, working solution that solves the problem correctly.\n");
        userPrompt.append("2. Include proper imports and a main method if needed.\n");
        userPrompt.append("3. Ensure the solution handles all edge cases mentioned in the problem.\n");
        userPrompt.append("4. Optimize the solution for the given difficulty level.\n");
        userPrompt.append("5. Include detailed comments explaining key parts of the solution.\n");
        userPrompt.append("6. Add a brief explanation of the algorithmic approach used.\n");
        userPrompt.append("7. Discuss time and space complexity of your solution.\n");
        userPrompt.append("8. IMPORTANT: Your response must be ONLY the code with comments. Do not include any additional text, explanations, or formatting.\n");
        
        return callGeminiWithSystemPrompt(
            "You are an expert programmer. Provide a complete, efficient solution to the given problem with detailed comments explaining the approach and complexity.",
            userPrompt.toString()
        );
    }
}