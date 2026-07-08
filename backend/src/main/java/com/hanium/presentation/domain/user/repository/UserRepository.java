// 역할: 도메인 엔티티를 조회하고 저장하는 Spring Data JPA 저장소입니다.
package com.hanium.presentation.domain.user.repository;

import com.hanium.presentation.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);
}
