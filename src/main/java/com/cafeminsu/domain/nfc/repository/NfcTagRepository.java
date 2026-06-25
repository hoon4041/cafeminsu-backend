package com.cafeminsu.domain.nfc.repository;

import com.cafeminsu.domain.nfc.entity.NfcTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NfcTagRepository extends JpaRepository<NfcTag, Long> {

    Optional<NfcTag> findByCode(String code);

    /** 코드 발급 시 중복 회피용. */
    boolean existsByCode(String code);

    /** 점주 어드민용 — 매장의 태그 목록. */
    List<NfcTag> findAllByStoreIdOrderByIdDesc(Long storeId);
}
