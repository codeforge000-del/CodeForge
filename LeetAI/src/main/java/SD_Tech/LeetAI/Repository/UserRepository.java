package SD_Tech.LeetAI.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import SD_Tech.LeetAI.Entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByEmail(String email);
}
