package SD_Tech.LeetAI.Repository;

import SD_Tech.LeetAI.Entity.TestCase;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

	List<TestCase> findByProblemId(Long problemId);
}