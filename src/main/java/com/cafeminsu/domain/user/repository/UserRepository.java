package com.cafeminsu.domain.user.repository;

import com.cafeminsu.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByKakaoId(String kakaoId);

    Optional<User> findByLoginId(String loginId);

    boolean existsByNickname(String nickname);
}
