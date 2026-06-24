package com.cafeminsu.domain.gifticon.repository;

import com.cafeminsu.domain.gifticon.entity.Gifticon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GifticonRepository extends JpaRepository<Gifticon, Long> {

    /** 클레임 코드 발급 시 중복 회피용. */
    boolean existsByClaimToken(String claimToken);

    /**
     * 등록(claim) 시 row를 비관적 락으로 잡음.
     * 같은 링크를 받은 두 사람이 동시에 등록을 눌러도 한쪽만 귀속되도록 직렬화.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM Gifticon g WHERE g.claimToken = :token")
    Optional<Gifticon> findByClaimTokenForUpdate(@Param("token") String token);

    /**
     * 사용/차감 시 row를 비관적 락으로 잡음.
     * 같은 기프티콘 동시 차감 시 한쪽은 대기 → 순차 처리.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM Gifticon g WHERE g.id = :id")
    Optional<Gifticon> findByIdForUpdate(@Param("id") Long id);

    /** 보낸 기프티콘 */
    List<Gifticon> findAllBySenderIdOrderByIdDesc(Long senderId);

    /** 받은 기프티콘 (회원 매핑된 것만) */
    List<Gifticon> findAllByReceiverIdOrderByIdDesc(Long receiverId);

    /** 내가 받았고 + 잔액 > 0 + 만료 전 — 결제 화면용 */
    @Query("""
            SELECT g FROM Gifticon g
            WHERE g.receiverId = :userId
              AND g.balance > 0
              AND g.status <> com.cafeminsu.domain.gifticon.entity.GifticonStatus.EXPIRED
              AND g.expiresAt > CURRENT_TIMESTAMP
            ORDER BY g.expiresAt ASC
            """)
    List<Gifticon> findUsableByReceiverId(@Param("userId") Long userId);
}
