package SD_Tech.LeetAI.Controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import SD_Tech.LeetAI.DTO.SubmissionDetailsDTO;
import SD_Tech.LeetAI.DTO.SubmissionRequestDTO;
import SD_Tech.LeetAI.DTO.TestCaseResultDTO;
import SD_Tech.LeetAI.Entity.Problem;
import SD_Tech.LeetAI.Entity.Submission;
import SD_Tech.LeetAI.Entity.SubmissionTestCaseResult;
import SD_Tech.LeetAI.Entity.TestCase;
import SD_Tech.LeetAI.Entity.User;
import SD_Tech.LeetAI.Repository.ProblemRepository;
import SD_Tech.LeetAI.Repository.SubmissionRepository;
import SD_Tech.LeetAI.Repository.TestCaseRepository;
import SD_Tech.LeetAI.Repository.UserRepository;
import SD_Tech.LeetAI.Service.GeminiService;
import SD_Tech.LeetAI.Service.Judge0Service;
import SD_Tech.LeetAI.DTO.Judge0BatchSubmissionRequest;
import SD_Tech.LeetAI.DTO.Judge0Response;
import SD_Tech.LeetAI.DTO.Judge0BatchResponse;

@RestController
@RequestMapping("/api/submissions")
@Transactional(readOnly = true)
public class SubmissionController {

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private TestCaseRepository testCaseRepository;

    @Autowired
    private GeminiService geminiService;
    
    @Autowired
    private Judge0Service judge0Service;
    
    @Autowired
    private ProblemRepository problemRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Value("${app.demo.mock:false}")
    private boolean appDemoMock;
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping
    @Transactional(readOnly = false)
    public ResponseEntity<?> submitCode(@RequestBody SubmissionRequestDTO submissionRequest) {
        try {
            if (submissionRequest.getCode() == null || submissionRequest.getCode().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Code cannot be empty");
            }
            
            if (submissionRequest.getProblemId() == null) {
                return ResponseEntity.badRequest().body("Problem ID is required");
            }
            
            if (submissionRequest.getUserId() == null) {
                return ResponseEntity.badRequest().body("User ID is required");
            }
            
            Problem problem = problemRepository.findById(submissionRequest.getProblemId())
                    .orElseThrow(() -> new IllegalArgumentException("Problem not found with id: " + submissionRequest.getProblemId()));
            
            User user = userRepository.findById(submissionRequest.getUserId())
                    .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + submissionRequest.getUserId()));
            
            Submission submission = new Submission();
            submission.setCode(submissionRequest.getCode());
            submission.setLanguage(submissionRequest.getLanguage());
            submission.setProblem(problem);
            submission.setUser(user);
            submission.setSubmissionTime(LocalDateTime.now());
            submission.setStatus("PENDING"); 
            submission.setRuntimeMs(0L);
            submission.setTotalTestCases(0);
            submission.setPassedTestCases(0);

            Submission savedSubmission = submissionRepository.save(submission);

            // Start asynchronous AI evaluation
            CompletableFuture.runAsync(() -> {
                try {
                    evaluateSubmissionWithAI(savedSubmission);
                } catch (Exception e) {
                    savedSubmission.setStatus("ERROR");
                    savedSubmission.setFeedback("AI evaluation failed: " + e.getMessage());
                    submissionRepository.save(savedSubmission);
                }
            });

            return ResponseEntity.ok(savedSubmission);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred during submission: " + e.getMessage());
        }
    }

    @Transactional(readOnly = false)
    private void evaluateSubmissionWithAI(Submission submission) {
        if (submission.getProblem() == null) {
            throw new IllegalStateException("Submission must have a problem associated");
        }

        List<TestCase> testCases = testCaseRepository.findByProblemId(submission.getProblem().getId());

        if (testCases.isEmpty()) {
            throw new IllegalStateException("No test cases found for problem: " + submission.getProblem().getId());
        }

        // Demo/mock mode: simulate Judge0 responses locally without calling external Judge0
        if (appDemoMock) {
            List<SubmissionTestCaseResult> simulatedResults = new ArrayList<>();
            long totalRuntime = 0L;
            for (TestCase tc : testCases) {
                SubmissionTestCaseResult res = new SubmissionTestCaseResult();
                res.setSubmission(submission);
                res.setTestCase(tc);
                res.setPassed(true);
                String expected = tc.getExpectedOutput() != null ? tc.getExpectedOutput().trim() : "";
                res.setActualOutput(expected);
                res.setRuntimeMs(10L); // tiny simulated runtime
                res.setError(null);
                simulatedResults.add(res);
                totalRuntime += res.getRuntimeMs();
            }

            int passedCount = simulatedResults.size();
            int totalCount = simulatedResults.size();

            submission.setStatus("PASSED");
            submission.setFeedback("Demo mode: all test cases marked as passed (simulated)");
            submission.setPassedTestCases(passedCount);
            submission.setTotalTestCases(totalCount);
            submission.setRuntimeMs(totalRuntime);
            submission.setTestCaseResults(simulatedResults);
            try {
                List<TestCaseResultDTO> dtoList = simulatedResults.stream().map(r -> {
                    TestCaseResultDTO d = new TestCaseResultDTO();
                    d.setTestCaseId(r.getTestCase() != null ? r.getTestCase().getId() : null);
                    d.setInput(r.getTestCase() != null ? r.getTestCase().getInput() : null);
                    d.setExpectedOutput(r.getTestCase() != null ? r.getTestCase().getExpectedOutput() : null);
                    d.setActualOutput(r.getActualOutput());
                    d.setPassed(r.isPassed());
                    d.setRuntimeMs(r.getRuntimeMs());
                    d.setError(r.getError());
                    return d;
                }).collect(Collectors.toList());

                submission.setTestCaseResultsJson(objectMapper.writeValueAsString(dtoList));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
            submissionRepository.save(submission);
            return;
        }

        try {
            // Prepare batch requests for Judge0
            int languageId = judge0Service.getLanguageId(submission.getLanguage());

            List<Judge0BatchSubmissionRequest> batchRequests = new ArrayList<>();
            for (TestCase tc : testCases) {
                Judge0BatchSubmissionRequest req = new Judge0BatchSubmissionRequest(String.valueOf(languageId), submission.getCode(), tc.getInput());
                req.setExpected_output(tc.getExpectedOutput());
                batchRequests.add(req);
            }

            // Submit batch and poll for results
            Judge0BatchResponse batchResponse = judge0Service.submitBatch(batchRequests);

            List<String> tokens = new ArrayList<>();
            if (batchResponse != null && batchResponse.getSubmissions() != null) {
                for (Judge0Response r : batchResponse.getSubmissions()) {
                    if (r != null && r.getToken() != null) tokens.add(r.getToken());
                }
            }

            List<Judge0Response> results = judge0Service.pollBatchResults(tokens);

            // Map results back to test case results
            List<SubmissionTestCaseResult> testCaseResults = new ArrayList<>();
            int idx = 0;
            long totalRuntime = 0L;
            for (Judge0Response jr : results) {
                SubmissionTestCaseResult res = new SubmissionTestCaseResult();
                res.setSubmission(submission);
                TestCase tc = testCases.get(Math.min(idx, testCases.size() - 1));
                res.setTestCase(tc);

                boolean passed = jr.getStatus() != null && jr.getStatus().getId() == 3; // 3 == Accepted
                res.setPassed(passed);

                String actualOutput = jr.getStdout();
                if ((actualOutput == null || actualOutput.isEmpty()) && jr.getStderr() != null) {
                    actualOutput = jr.getStderr();
                }
                if ((actualOutput == null || actualOutput.isEmpty()) && jr.getCompile_output() != null) {
                    actualOutput = jr.getCompile_output();
                }
                res.setActualOutput(actualOutput != null ? actualOutput.trim() : "");

                res.setRuntimeMs(jr.getTime());
                totalRuntime += jr.getTime();

                // Set error details when appropriate
                if (jr.getStatus() != null && jr.getStatus().getId() == 6) { // Compilation error
                    res.setError(jr.getCompile_output());
                } else if (jr.getStatus() != null && jr.getStatus().getId() != 3) {
                    // Non-accepted and non-compilation statuses
                    res.setError(jr.getStderr() != null ? jr.getStderr() : null);
                } else {
                    res.setError(null);
                }

                testCaseResults.add(res);
                idx++;
            }

            int passedCount = (int) testCaseResults.stream().filter(SubmissionTestCaseResult::isPassed).count();
            int totalCount = testCaseResults.size();

            submission.setStatus(passedCount == totalCount ? "PASSED" : "FAILED");
            // Build a concise feedback summary
            StringBuilder feedback = new StringBuilder();
            feedback.append("Judge0 evaluation results:\n");
            for (int i = 0; i < testCaseResults.size(); i++) {
                SubmissionTestCaseResult r = testCaseResults.get(i);
                feedback.append(String.format("Test %d: %s\n", i + 1, r.isPassed() ? "PASS" : "FAIL"));
                if (r.getError() != null && !r.getError().isEmpty()) {
                    feedback.append("Error: ").append(r.getError()).append("\n");
                }
            }

            submission.setFeedback(feedback.toString());
            submission.setPassedTestCases(passedCount);
            submission.setTotalTestCases(totalCount);
            submission.setRuntimeMs(totalRuntime);

            submission.setTestCaseResults(testCaseResults);

            try {
                // Serialize a lightweight DTO list instead of full entities to avoid
                // jackson problems with LocalDateTime and circular references.
                List<TestCaseResultDTO> dtoList = testCaseResults.stream().map(r -> {
                    TestCaseResultDTO d = new TestCaseResultDTO();
                    d.setTestCaseId(r.getTestCase() != null ? r.getTestCase().getId() : null);
                    d.setInput(r.getTestCase() != null ? r.getTestCase().getInput() : null);
                    d.setExpectedOutput(r.getTestCase() != null ? r.getTestCase().getExpectedOutput() : null);
                    d.setActualOutput(r.getActualOutput());
                    d.setPassed(r.isPassed());
                    d.setRuntimeMs(r.getRuntimeMs());
                    d.setError(r.getError());
                    return d;
                }).collect(Collectors.toList());

                submission.setTestCaseResultsJson(objectMapper.writeValueAsString(dtoList));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }

            submissionRepository.save(submission);
        } catch (Exception e) {
            // If Judge0 fails, persist an error state and include message
            submission.setStatus("ERROR");
            submission.setFeedback("Judge0 evaluation failed: " + e.getMessage());
            submissionRepository.save(submission);
        }
    }
    
    private List<SubmissionTestCaseResult> parseTestCaseResultsFromAIResponse(String aiResponse, List<TestCase> testCases, Submission submission) {
        List<SubmissionTestCaseResult> results = new ArrayList<>();
        
        // Simple parsing logic - in a real implementation, this would be more sophisticated
        // This is a basic approach that looks for patterns in the AI response
        
        // For each test case, try to determine if it passed based on the AI response
        for (int i = 0; i < testCases.size(); i++) {
            TestCase testCase = testCases.get(i);
            SubmissionTestCaseResult result = new SubmissionTestCaseResult();
            result.setSubmission(submission);
            result.setTestCase(testCase);
            
            // Look for mentions of this test case in the AI response
            String testCasePattern = "Test Case " + (i+1) + ":";
            int testCaseIndex = aiResponse.indexOf(testCasePattern);
            
            if (testCaseIndex != -1) {
                // Find the next section or end of response
                int nextSectionIndex = aiResponse.indexOf("Test Case " + (i+2) + ":", testCaseIndex);
                if (nextSectionIndex == -1) {
                    nextSectionIndex = aiResponse.length();
                }
                
                String testCaseSection = aiResponse.substring(testCaseIndex, nextSectionIndex);
                
                // Check if the AI indicated this test case passed
                boolean passed = testCaseSection.toLowerCase().contains("pass") && 
                                !testCaseSection.toLowerCase().contains("fail");
                
                result.setPassed(passed);
                
                // Try to extract actual output if mentioned
                String actualOutputPattern = "actual output[:\\s]+([^\n]+)";
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(actualOutputPattern, java.util.regex.Pattern.CASE_INSENSITIVE);
                java.util.regex.Matcher matcher = pattern.matcher(testCaseSection);
                
                if (matcher.find()) {
                    result.setActualOutput(matcher.group(1).trim());
                } else {
                    // If no actual output found, use a placeholder
                    result.setActualOutput("No output provided by AI");
                }
                
                result.setRuntimeMs(0L); // AI doesn't provide runtime
                result.setError(null); // No error for now
            } else {
                // If no specific mention, assume it failed
                result.setPassed(false);
                result.setActualOutput("No evaluation provided by AI");
                result.setRuntimeMs(0L);
                result.setError(null);
            }
            
            results.add(result);
        }
        
        return results;
    }

    @PostMapping("/review")
    @Transactional(readOnly = false)
    public ResponseEntity<?> reviewCode(@RequestBody SubmissionRequestDTO submissionRequest) {
        try {
            if (submissionRequest.getCode() == null || submissionRequest.getCode().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Code cannot be empty");
            }
            
            if (submissionRequest.getProblemId() == null) {
                return ResponseEntity.badRequest().body("Problem ID is required");
            }
            
            Problem problem = problemRepository.findById(submissionRequest.getProblemId())
                    .orElseThrow(() -> new IllegalArgumentException("Problem not found with id: " + submissionRequest.getProblemId()));
            
            // Generate AI review and explanation
            String prompt = String.format(
                "You are a code reviewer. Analyze the following code for the given problem and provide a detailed review.\n\n" +
                "Problem: %s\n\n" +
                "Code:\n```%s\n%s\n```\n\n" +
                "Please provide:\n" +
                "1. A brief summary of what the code does\n" +
                "2. Analysis of the approach and algorithm used\n" +
                "3. Time and space complexity analysis\n" +
                "4. Potential bugs or issues\n" +
                "5. Suggestions for improvement\n" +
                "6. Overall assessment of the code quality",
                problem.getDescription(),
                submissionRequest.getLanguage(),
                submissionRequest.getCode()
            );
            
            String aiResponse = geminiService.getExplanation(prompt);
            
            return ResponseEntity.ok(aiResponse);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred during code review: " + e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<List<SubmissionDetailsDTO>> getAllSubmissions() {
        try {
            List<Submission> submissions = submissionRepository.findAll();
            List<SubmissionDetailsDTO> dtos = submissions.stream()
                    .map(this::convertToSubmissionDetailsDTO)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<SubmissionDetailsDTO>> getSubmissionsByUser(@PathVariable Long userId) {
        try {
            List<Submission> submissions = submissionRepository.findByUserId(userId);
            List<SubmissionDetailsDTO> dtos = submissions.stream()
                    .map(this::convertToSubmissionDetailsDTO)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/problem/{problemId}")
    public ResponseEntity<List<SubmissionDetailsDTO>> getSubmissionsByProblem(@PathVariable Long problemId) {
        try {
            List<Submission> submissions = submissionRepository.findByProblemId(problemId);
            List<SubmissionDetailsDTO> dtos = submissions.stream()
                    .map(this::convertToSubmissionDetailsDTO)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubmissionDetailsDTO> getSubmissionById(@PathVariable Long id) {
        try {
            return submissionRepository.findById(id)
                    .map(submission -> ResponseEntity.ok(convertToSubmissionDetailsDTO(submission)))
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    private SubmissionDetailsDTO convertToSubmissionDetailsDTO(Submission submission) {
        SubmissionDetailsDTO dto = new SubmissionDetailsDTO();
        dto.setId(submission.getId());
        dto.setUserId(submission.getUser().getId());
        dto.setUserName(submission.getUser().getName());
        dto.setProblemId(submission.getProblem().getId());
        dto.setProblemTitle(submission.getProblem().getTitle());
        dto.setCode(submission.getCode());
        dto.setLanguage(submission.getLanguage());
        dto.setStatus(submission.getStatus());
        dto.setRuntimeMs(submission.getRuntimeMs());
        dto.setSubmissionTime(submission.getSubmissionTime());
        dto.setFeedback(submission.getFeedback());
        
        int total = Optional.ofNullable(submission.getTotalTestCases()).orElse(0);
        int passed = Optional.ofNullable(submission.getPassedTestCases()).orElse(0);
        dto.setTotalTestCases(total);
        dto.setPassedTestCases(passed);
        dto.setFailedTestCases(Math.max(0, total - passed));       
        
        // Convert test case results to DTOs
        if (submission.getTestCaseResults() != null && !submission.getTestCaseResults().isEmpty()) {
            List<TestCaseResultDTO> testCaseResultDTOs = submission.getTestCaseResults().stream()
                .map(result -> {
                    TestCaseResultDTO resultDTO = new TestCaseResultDTO();
                    resultDTO.setTestCaseId(result.getTestCase().getId());
                    resultDTO.setInput(result.getTestCase().getInput());
                    resultDTO.setExpectedOutput(result.getTestCase().getExpectedOutput());
                    resultDTO.setActualOutput(result.getActualOutput());
                    resultDTO.setPassed(result.isPassed());
                    resultDTO.setRuntimeMs(result.getRuntimeMs());
                    resultDTO.setError(result.getError());
                    return resultDTO;
                })
                .collect(Collectors.toList());
            dto.setTestCaseResults(testCaseResultDTOs);
        } else if (submission.getTestCaseResultsJson() != null && !submission.getTestCaseResultsJson().isEmpty()) {
            // Fallback to JSON if the relationship isn't loaded
            try {
                // Our stored JSON is a list of TestCaseResultDTO (lightweight), try to read that first
                List<TestCaseResultDTO> testCaseResultDTOs = objectMapper.readValue(
                    submission.getTestCaseResultsJson(), 
                    new TypeReference<List<TestCaseResultDTO>>() {}
                );
                dto.setTestCaseResults(testCaseResultDTOs);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }
        
        return dto;
    }
}