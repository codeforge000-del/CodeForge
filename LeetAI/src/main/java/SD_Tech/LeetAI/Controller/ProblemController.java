package SD_Tech.LeetAI.Controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import SD_Tech.LeetAI.DTO.Judge0Response;
import SD_Tech.LeetAI.Entity.CodeTemplate;
import SD_Tech.LeetAI.Entity.Problem;
import SD_Tech.LeetAI.Entity.Tag;
import SD_Tech.LeetAI.Entity.TestCase;
import SD_Tech.LeetAI.Repository.ProblemRepository;
import SD_Tech.LeetAI.Repository.TagRepository;
import SD_Tech.LeetAI.Service.GeminiService;
import SD_Tech.LeetAI.Service.GeminiService.GeneratedProblem;
import SD_Tech.LeetAI.Service.GeminiService.GeneratedTestCase;
import SD_Tech.LeetAI.Service.Judge0Service;

@RestController
@RequestMapping("/api/problems")
public class ProblemController {

    private static final Logger logger = LoggerFactory.getLogger(ProblemController.class);
    
    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private TagRepository tagRepository;
    
    @Autowired
    private GeminiService geminiService;
    
    @Autowired
    private Judge0Service judge0Service;

    // Manual problem creation
    @PostMapping
    @Transactional
    public ResponseEntity<?> createProblem(
            @RequestBody Problem problem, 
            @RequestParam(required = false) List<String> tags) {
        
        try {
            logger.info("Creating new problem: {}", problem.getTitle());
            
            // Validate required fields
            if (problem.getTitle() == null || problem.getTitle().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Title is required");
            }
            
            if (problem.getDescription() == null || problem.getDescription().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Description is required");
            }
            
            if (problem.getDifficulty() == null || problem.getDifficulty().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Difficulty is required");
            }
            
            // Set tags if provided
            if (tags != null && !tags.isEmpty()) {
                Set<Tag> tagSet = new HashSet<>();
                for (String t : tags) {
                    Tag tag = tagRepository.findByName(t).orElseGet(() -> {
                        Tag newTag = new Tag();
                        newTag.setName(t);
                        return tagRepository.save(newTag);
                    });
                    tagSet.add(tag);
                }
                problem.setTags(tagSet);
            }
            
            // Ensure example and constraints are not empty
            if (problem.getExample() == null || problem.getExample().trim().isEmpty()) {
                problem.setExample(generateDetailedExamples(problem.getDifficulty()));
            }
            
            if (problem.getConstraints() == null || problem.getConstraints().trim().isEmpty()) {
                problem.setConstraints(generateDetailedConstraints(problem.getDifficulty()));
            }
            
            // Initialize code templates if null
            if (problem.getCodeTemplates() == null) {
                problem.setCodeTemplates(new HashSet<>());
            }
            
            // Add code templates for all supported languages
            for (String language : GeminiService.SUPPORTED_LANGUAGES) {
                CodeTemplate template = new CodeTemplate();
                template.setLanguage(language);
                
                // Try to find a template for this language in the request
                String code = null;
                if (problem.getCodeTemplates() != null) {
                    for (CodeTemplate existingTemplate : problem.getCodeTemplates()) {
                        if (language.equals(existingTemplate.getLanguage())) {
                            code = existingTemplate.getCode();
                            break;
                        }
                    }
                }
                
                // If no template was found in the request, generate a default one
                if (code == null || code.trim().isEmpty()) {
                    code = geminiService.generateDetailedCodeTemplate(
                        problem.getTitle(), problem.getDifficulty(), language);
                }
                
                template.setCode(code);
                template.setProblem(problem);
                problem.getCodeTemplates().add(template);
            }
            
            // Generate solution for Java if not provided
            if (problem.getSolution() == null || problem.getSolution().trim().isEmpty()) {
                String javaSolution = geminiService.generateSolution(
                    problem.getTitle(), 
                    problem.getDescription(), 
                    problem.getDifficulty(), 
                    "Java"
                );
                problem.setSolution(javaSolution);
            }
            
            // Save the problem
            Problem savedProblem = problemRepository.save(problem);
            
            // Fetch the problem again with all relationships loaded
            return ResponseEntity.ok(problemRepository.findByIdWithTags(savedProblem.getId()).get());
            
        } catch (Exception e) {
            logger.error("Error creating problem: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error creating problem: " + e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<List<Problem>> getAllProblems(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        
        try {
            Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
            Pageable pageable = PageRequest.of(page, size, sort);
            Page<Problem> problems = problemRepository.findAll(pageable);
            
            return ResponseEntity.ok()
                    .header("X-Total-Count", String.valueOf(problems.getTotalElements()))
                    .body(problems.getContent());
        } catch (Exception e) {
            logger.error("Error getting all problems: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Problem> getProblemById(@PathVariable Long id) {
        try {
            return problemRepository.findByIdWithTags(id)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            logger.error("Error getting problem by id: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    // Get problems by tag
    @GetMapping("/tag/{tagName}")
    public ResponseEntity<List<Problem>> getProblemsByTag(
            @PathVariable String tagName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<Problem> problems = problemRepository.findByTags(tagName, pageable);
            
            return ResponseEntity.ok()
                    .header("X-Total-Count", String.valueOf(problems.getTotalElements()))
                    .body(problems.getContent());
        } catch (Exception e) {
            logger.error("Error getting problems by tag: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    // Get problems by difficulty
    @GetMapping("/difficulty/{difficulty}")
    public ResponseEntity<List<Problem>> getProblemsByDifficulty(
            @PathVariable String difficulty,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<Problem> problems = problemRepository.findByDifficulty(difficulty, pageable);
            
            return ResponseEntity.ok()
                    .header("X-Total-Count", String.valueOf(problems.getTotalElements()))
                    .body(problems.getContent());
        } catch (Exception e) {
            logger.error("Error getting problems by difficulty: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    // Get all tags
    @GetMapping("/tags")
    public ResponseEntity<List<Tag>> getAllTags() {
        try {
            List<Tag> tags = tagRepository.findAll();
            return ResponseEntity.ok(tags);
        } catch (Exception e) {
            logger.error("Error getting all tags: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    // Get code templates for a problem
    @GetMapping("/{id}/code-templates")
    public ResponseEntity<?> getCodeTemplates(@PathVariable Long id) {
        try {
            Problem problem = problemRepository.findByIdWithCodeTemplates(id)
                    .orElseThrow(() -> new RuntimeException("Problem not found"));
            
            return ResponseEntity.ok(problem.getCodeTemplates());
        } catch (Exception e) {
            logger.error("Error getting code templates: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to get code templates: " + e.getMessage());
        }
    }
    
    // Update problem
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<Problem> updateProblem(
            @PathVariable Long id, 
            @RequestBody Problem problemDetails, 
            @RequestParam(required = false) List<String> tags) {
        
        try {
            logger.info("Updating problem with id: {}", id);
            
            return problemRepository.findByIdWithTags(id)
                    .map(problem -> {
                        problem.setTitle(problemDetails.getTitle());
                        problem.setDescription(problemDetails.getDescription());
                        problem.setDifficulty(problemDetails.getDifficulty());
                        
                        // Update example and constraints if provided
                        if (problemDetails.getExample() != null && !problemDetails.getExample().trim().isEmpty()) {
                            problem.setExample(problemDetails.getExample());
                        }
                        
                        if (problemDetails.getConstraints() != null && !problemDetails.getConstraints().trim().isEmpty()) {
                            problem.setConstraints(problemDetails.getConstraints());
                        }
                        
                        if (problemDetails.getExplanation() != null && !problemDetails.getExplanation().trim().isEmpty()) {
                            problem.setExplanation(problemDetails.getExplanation());
                        }
                        
                        // Initialize code templates if null
                        if (problem.getCodeTemplates() == null) {
                            problem.setCodeTemplates(new HashSet<>());
                        }
                        
                        // Clear existing code templates
                        problem.getCodeTemplates().clear();
                        
                        // Add code templates for all supported languages
                        for (String language : GeminiService.SUPPORTED_LANGUAGES) {
                            CodeTemplate template = new CodeTemplate();
                            template.setLanguage(language);
                            
                            // Try to find a template for this language in the request
                            String code = null;
                            if (problemDetails.getCodeTemplates() != null) {
                                for (CodeTemplate existingTemplate : problemDetails.getCodeTemplates()) {
                                    if (language.equals(existingTemplate.getLanguage())) {
                                        code = existingTemplate.getCode();
                                        break;
                                    }
                                }
                            }
                            
                            // If no template was found in the request, generate a default one
                            if (code == null || code.trim().isEmpty()) {
                                code = geminiService.generateDetailedCodeTemplate(
                                    problem.getTitle(), problem.getDifficulty(), language);
                            }
                            
                            template.setCode(code);
                            template.setProblem(problem);
                            problem.getCodeTemplates().add(template);
                        }
                        
                        // Update solution if provided
                        if (problemDetails.getSolution() != null && !problemDetails.getSolution().trim().isEmpty()) {
                            problem.setSolution(problemDetails.getSolution());
                        } else if (problem.getSolution() == null || problem.getSolution().trim().isEmpty()) {
                            // Generate solution if not available
                            String javaSolution = geminiService.generateSolution(
                                problem.getTitle(), 
                                problem.getDescription(), 
                                problem.getDifficulty(), 
                                "Java"
                            );
                            problem.setSolution(javaSolution);
                        }
                        
                        // Update tags if provided
                        if (tags != null) {
                            Set<Tag> tagSet = new HashSet<>();
                            for (String t : tags) {
                                Tag tag = tagRepository.findByName(t).orElseGet(() -> {
                                    Tag newTag = new Tag();
                                    newTag.setName(t);
                                    return tagRepository.save(newTag);
                                });
                                tagSet.add(tag);
                            }
                            problem.setTags(tagSet);
                        }
                        
                        // Save the problem
                        Problem savedProblem = problemRepository.save(problem);
                        
                        // Fetch the problem again with tags loaded
                        return ResponseEntity.ok(problemRepository.findByIdWithTags(savedProblem.getId()).get());
                    })
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            logger.error("Error updating problem: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> deleteProblem(@PathVariable Long id) {
        try {
            logger.info("Deleting problem with id: {}", id);
            
            return problemRepository.findById(id)
                    .map(problem -> {
                        problemRepository.delete(problem);
                        return ResponseEntity.ok().<Void>build();
                    })
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            logger.error("Error deleting problem: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    // AI-generated problem creation - returns data without saving
    @PostMapping("/generate")
    public ResponseEntity<?> generateProblem(
            @RequestParam String topic,
            @RequestParam String difficulty) {

        try {
            logger.info("Generating problem for topic: {} with difficulty: {}", topic, difficulty);
            
            // Validate parameters
            if (topic == null || topic.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Topic is required");
            }
            
            if (difficulty == null || difficulty.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Difficulty is required");
            }
            
            // Generate problem
            String generatedText = geminiService.generateDSAProblem(topic, difficulty);

            // Parse structured problem
            GeneratedProblem generatedProblem = geminiService.parseGeneratedProblem(generatedText, difficulty);

            // Return as JSON (not persisted)
            return ResponseEntity.ok(generatedProblem);

        } catch (Exception e) {
            logger.error("Error generating problem: {}", e.getMessage(), e);
            
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to generate problem");
            errorResponse.put("details", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // Generate test cases for a problem - returns data without saving
    @PostMapping("/{id}/generate-testcases")
    public ResponseEntity<?> generateTestCases(
            @PathVariable Long id,
            @RequestParam int count) {
        try {
            logger.info("Generating {} test cases for problem with id: {}", count, id);
            
            // Validate count
            if (count <= 0 || count > 20) {
                return ResponseEntity.badRequest().body("Count must be between 1 and 20");
            }
            
            // Get the problem with existing test cases
            Problem problem = problemRepository.findByIdWithTestCases(id)
                    .orElseThrow(() -> new RuntimeException("Problem not found"));
            
            // Get existing test cases
            List<TestCase> existingTestCases = problem.getTestCases() != null ? 
                    new ArrayList<>(problem.getTestCases()) : new ArrayList<>();
            
            // Generate test cases using Gemini, avoiding duplicates
            List<GeneratedTestCase> generatedTestCases = geminiService.generateTestCasesForProblem(problem, existingTestCases, count);
            
            // Return generated test cases without saving to database
            return ResponseEntity.ok(generatedTestCases);
        } catch (Exception e) {
            logger.error("Error generating test cases: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to generate test cases: " + e.getMessage());
        }
    }
    
 // In ProblemController, update the saveTestCases method
    @PostMapping("/{id}/testcases")
    @Transactional
    public ResponseEntity<?> saveTestCases(@PathVariable Long id, @RequestBody List<TestCase> testCases) {
        try {
            logger.info("Saving {} test cases for problem with id: {}", testCases.size(), id);

            // --- 1. Fetch problem with validation ---
            Problem problem = problemRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Problem not found"));

            if (testCases == null || testCases.isEmpty()) {
                return ResponseEntity.badRequest().body("No test cases provided");
            }

            // --- 2. Basic input validation ---
            for (TestCase testCase : testCases) {
                if (testCase.getInput() == null || testCase.getInput().trim().isEmpty()) {
                    return ResponseEntity.badRequest().body("Test case input cannot be empty");
                }
                if (testCase.getExpectedOutput() == null || testCase.getExpectedOutput().trim().isEmpty()) {
                    return ResponseEntity.badRequest().body("Test case expected output cannot be empty");
                }
            }

            // --- 3. Initialize test case list ---
            if (problem.getTestCases() == null) {
                problem.setTestCases(new ArrayList<>());
            }

            // --- 4. Clear old test cases ---
            problem.getTestCases().clear();

            // --- 5. Judge0 validation (with quota-safe fallback) ---
            if (problem.getSolution() != null && !problem.getSolution().trim().isEmpty()) {
                logger.info("Validating test cases using Judge0...");

                int validatedCount = 0;
                for (TestCase testCase : testCases) {
                    try {
                        // Limit number of Judge0 calls to prevent quota exhaustion
                        if (validatedCount >= 3) {
                            logger.warn("Skipping validation for remaining test cases to avoid quota exhaustion");
                            break;
                        }

                        // Use the refactored Judge0Service.executeCode method
                        Judge0Response response = judge0Service.executeCode(
                                problem.getSolution(),
                                problem.getReferenceLanguage() != null ? problem.getReferenceLanguage() : "Java",
                                testCase.getInput()
                        );

                        if (response.getStatus() != null && response.getStatus().getId() == 3) { // Accepted
                            String actualOutput = response.getStdout();
                            if (actualOutput == null) actualOutput = "";

                            if (!actualOutput.trim().equals(testCase.getExpectedOutput().trim())) {
                                logger.warn("Test case failed validation: expected [{}] but got [{}] for input [{}]",
                                        testCase.getExpectedOutput(), actualOutput, testCase.getInput());
                                testCase.setHidden(true); // mark invalid as hidden
                            } else {
                                logger.info("✅ Test case passed for input: {}", testCase.getInput());
                            }
                        } else {
                            logger.error("Judge0 returned non-accepted status: {}", 
                                    response.getStatus() != null ? response.getStatus().getDescription() : "Unknown error");
                            testCase.setHidden(true); // hide to prevent user-facing issues
                        }

                        validatedCount++;

                    } catch (Exception e) {
                        String errMsg = e.getMessage() != null ? e.getMessage() : "Unknown error";
                        if (errMsg.contains("429") || errMsg.contains("Too Many Requests") || 
                            errMsg.contains("quota") || errMsg.contains("rate limit")) {
                            logger.error("🚫 Judge0 quota exceeded. Skipping further validations.");
                            break; // stop further calls
                        }
                        logger.error("Error validating test case [{}]: {}", testCase.getInput(), errMsg);
                        testCase.setHidden(true);
                    }
                }
            } else {
                logger.warn("⚠️ No solution available for problem {} — skipping validation.", id);
            }

            // --- 6. Link test cases with problem ---
            for (TestCase testCase : testCases) {
                testCase.setProblem(problem);
                problem.getTestCases().add(testCase);
            }

            // --- 7. Save everything ---
            Problem savedProblem = problemRepository.save(problem);
            logger.info("✅ Saved {} test cases successfully for problem {}", testCases.size(), id);

            return ResponseEntity.ok(problemRepository.findByIdWithTestCases(savedProblem.getId()).get());

        } catch (Exception e) {
            logger.error("❌ Error saving test cases: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to save test cases: " + e.getMessage());
        }
    }
    
    // Get test cases for a problem
    @GetMapping("/{id}/testcases")
    public ResponseEntity<?> getTestCases(@PathVariable Long id) {
        try {
            Problem problem = problemRepository.findByIdWithTestCases(id)
                    .orElseThrow(() -> new RuntimeException("Problem not found"));
            
            return ResponseEntity.ok(problem.getTestCases());
        } catch (Exception e) {
            logger.error("Error getting test cases: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to get test cases: " + e.getMessage());
        }
    }
    
    /**
     * Generate detailed examples if admin doesn't provide them
     */
    private String generateDetailedExamples(String difficulty) {
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
            sb.append("Explanation 2:\nThe sum of the elements is 10+20+30+40+50=150.\n\n");
            
            if (difficulty.equalsIgnoreCase("HARD")) {
                sb.append("Sample Input 3:\n10\n-1 -2 -3 -4 -5 1 2 3 4 5\n");
                sb.append("Sample Output 3:\n0\n");
                sb.append("Explanation 3:\nThe sum of positive and negative numbers cancels out to 0.\n");
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Generate detailed constraints if admin doesn't provide them
     */
    private String generateDetailedConstraints(String difficulty) {
        StringBuilder sb = new StringBuilder();
        
        switch (difficulty.toUpperCase()) {
            case "EASY":
                sb.append("1 ≤ N ≤ 1000\n");
                sb.append("-1000 ≤ arr[i] ≤ 1000\n");
                sb.append("Time complexity: O(N)\n");
                sb.append("Space complexity: O(1)\n");
                break;
            case "MEDIUM":
                sb.append("1 ≤ N ≤ 10^5\n");
                sb.append("-10^9 ≤ arr[i] ≤ 10^9\n");
                sb.append("Time complexity: O(N log N)\n");
                sb.append("Space complexity: O(N)\n");
                break;
            case "HARD":
                sb.append("1 ≤ N ≤ 10^6\n");
                sb.append("-10^18 ≤ arr[i] ≤ 10^18\n");
                sb.append("Time complexity: O(N) or O(N log N)\n");
                sb.append("Space complexity: O(1) or O(N)\n");
                break;
            default:
                sb.append("1 ≤ N ≤ 1000\n");
                sb.append("-1000 ≤ arr[i] ≤ 1000\n");
                sb.append("Time complexity: O(N)\n");
        }
        
        sb.append("Your solution must pass all test cases within the given constraints.");
        return sb.toString();
    }
    
 // Add these endpoints to ProblemController

    @PostMapping("/generate-solution")
    public ResponseEntity<?> generateSolution(
            @RequestParam String title,
            @RequestParam String description,
            @RequestParam String difficulty,
            @RequestParam String language) {
        
        try {
            logger.info("Generating solution for problem: {} with language: {}", title, language);
            
            // Validate parameters
            if (title == null || title.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Title is required");
            }
            
            if (description == null || description.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Description is required");
            }
            
            if (difficulty == null || difficulty.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Difficulty is required");
            }
            
            if (language == null || language.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Language is required");
            }
            
            // Generate solution
            String solution = geminiService.generateSolution(title, description, difficulty, language);
            
            return ResponseEntity.ok(solution);
            
        } catch (Exception e) {
            logger.error("Error generating solution: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to generate solution: " + e.getMessage());
        }
    }

    @PostMapping("/generate-reference-solution")
    public ResponseEntity<?> generateReferenceSolution(
            @RequestParam String title,
            @RequestParam String description,
            @RequestParam String difficulty,
            @RequestParam String language) {
        
        try {
            logger.info("Generating reference solution for problem: {} with language: {}", title, language);
            
            // Validate parameters
            if (title == null || title.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Title is required");
            }
            
            if (description == null || description.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Description is required");
            }
            
            if (difficulty == null || difficulty.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Difficulty is required");
            }
            
            if (language == null || language.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Language is required");
            }
            
            // Generate reference solution (using the same method as solution)
            String referenceSolution = geminiService.generateSolution(title, description, difficulty, language);
            
            return ResponseEntity.ok(referenceSolution);
            
        } catch (Exception e) {
            logger.error("Error generating reference solution: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to generate reference solution: " + e.getMessage());
        }
    }
}