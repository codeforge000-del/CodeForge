package SD_Tech.LeetAI.Repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import SD_Tech.LeetAI.Entity.Problem;

@Repository
public interface ProblemRepository extends JpaRepository<Problem, Long> {

    @Query("SELECT p FROM Problem p LEFT JOIN FETCH p.tags WHERE p.id = :id")
    Optional<Problem> findByIdWithTags(@Param("id") Long id);
    
    @Query("SELECT p FROM Problem p LEFT JOIN FETCH p.codeTemplates WHERE p.id = :id")
    Optional<Problem> findByIdWithCodeTemplates(@Param("id") Long id);
    
    @Query("SELECT p FROM Problem p LEFT JOIN FETCH p.testCases WHERE p.id = :id")
    Optional<Problem> findByIdWithTestCases(@Param("id") Long id);
    
    @Query("SELECT p FROM Problem p LEFT JOIN FETCH p.tags LEFT JOIN FETCH p.codeTemplates WHERE p.id = :id")
    Optional<Problem> findByIdWithTagsAndCodeTemplates(@Param("id") Long id);
    
    @Query("SELECT p FROM Problem p WHERE p.id = :id")
    Optional<Problem> findByIdWithReferenceSolution(@Param("id") Long id);
    
    Page<Problem> findByTags(String tagName, Pageable pageable);
    
    Page<Problem> findByDifficulty(String difficulty, Pageable pageable);
}