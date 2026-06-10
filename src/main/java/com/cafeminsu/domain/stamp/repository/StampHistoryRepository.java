package com.cafeminsu.domain.stamp.repository;

import com.cafeminsu.domain.stamp.entity.StampHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StampHistoryRepository extends JpaRepository<StampHistory, Long> {

    List<StampHistory> findAllByStampIdOrderByIdDesc(Long stampId);
}
