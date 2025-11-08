package SD_Tech.LeetAI.Controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import SD_Tech.LeetAI.Entity.TestCase;
import SD_Tech.LeetAI.Repository.TestCaseRepository;

@RestController
@RequestMapping("/api/testcases")
public class TestCaseController {

    @Autowired
    private TestCaseRepository testCaseRepository;

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTestCase(@PathVariable Long id) {
        return testCaseRepository.findById(id)
                .map(testCase -> {
                    testCaseRepository.delete(testCase);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping
    public List<TestCase> getAllTestCases() {
        return testCaseRepository.findAll();
    }

    // Get test cases by problem ID
    @GetMapping("/problem/{problemId}")
    public List<TestCase> getTestCasesByProblemId(@PathVariable Long problemId) {
        return testCaseRepository.findByProblemId(problemId);
    }
}