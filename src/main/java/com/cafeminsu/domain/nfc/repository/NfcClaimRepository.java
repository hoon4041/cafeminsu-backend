package com.cafeminsu.domain.nfc.repository;

import com.cafeminsu.domain.nfc.entity.NfcClaim;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface NfcClaimRepository extends JpaRepository<NfcClaim, Long> {

    /** 하루 1회 발급 — 같은 태그를 오늘 이미 받았는지(빠른 경로 체크). */
    boolean existsByTagIdAndUserIdAndClaimDate(Long tagId, Long userId, LocalDate claimDate);
}
