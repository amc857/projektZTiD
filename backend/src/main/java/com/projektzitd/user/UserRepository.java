package com.projektzitd.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;


public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    Optional<User> findByGithubId(Long githubId);
    Optional<User> findByGoogleId(String googleId);
    long countByUsernameContainingIgnoreCase(String query);
    List<User> findTop10ByUsernameContainingIgnoreCaseOrderByUsernameAsc(String query);
}
