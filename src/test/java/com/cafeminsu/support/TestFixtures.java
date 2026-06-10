package com.cafeminsu.support;

import com.cafeminsu.domain.user.entity.User;
import com.cafeminsu.domain.user.repository.UserRepository;
import com.cafeminsu.global.security.jwt.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 테스트용 공통 fixture. DevController 거치지 않고 직접 User/JWT 발급.
 *
 * src/test/java 안에 있어서 main 빌드에는 포함되지 않음.
 */
@Component
public class TestFixtures {

    /** 같은 닉네임으로 두 번 호출 시 unique 충돌 방지를 위해 nickname suffix 자동 부여 */
    private final AtomicLong seq = new AtomicLong();

    @Autowired private UserRepository userRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    public User createCustomer(String nickname) {
        long n = seq.incrementAndGet();
        User user = User.createFromKakao(
                "kakao-cust-" + n,
                nickname + n + "@test.local",
                null
        );
        user.completeSignup(nickname + n, null);
        return userRepository.save(user);
    }

    public User createOwner(String nickname) {
        long n = seq.incrementAndGet();
        User user = User.createFromKakao(
                "kakao-own-" + n,
                nickname + n + "@test.local",
                null
        );
        user.completeSignup(nickname + n, null);
        user.becomeOwner();
        return userRepository.save(user);
    }

    public String tokenFor(User user) {
        return jwtTokenProvider.createAccessToken(user.getId(), user.getRole().name());
    }

    public String authHeader(User user) {
        return "Bearer " + tokenFor(user);
    }
}
