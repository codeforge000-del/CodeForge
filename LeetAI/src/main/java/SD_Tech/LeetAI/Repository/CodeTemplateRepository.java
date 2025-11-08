package SD_Tech.LeetAI.Repository;

import SD_Tech.LeetAI.Entity.CodeTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CodeTemplateRepository extends JpaRepository<CodeTemplate, Long> {
}