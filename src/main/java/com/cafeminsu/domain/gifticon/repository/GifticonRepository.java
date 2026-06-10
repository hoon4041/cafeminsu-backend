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

    Optional<Gifticon> findByQrCode(String qrCode);

    /**
     * 사용/차감 시 row를 비관적 락으로 잡음.
     * 같은 기프티콘 두 키오스크 동시 스캔 시 한쪽은 대기 → 순차 처리.
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
