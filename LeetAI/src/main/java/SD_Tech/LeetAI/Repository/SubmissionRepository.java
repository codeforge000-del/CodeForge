package SD_Tech.LeetAI.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import SD_Tech.LeetAI.Entity.Submission;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    List<Submission> findByUserId(Long userId);
    List<Submission> findByProblemId(Long problemId);
}
