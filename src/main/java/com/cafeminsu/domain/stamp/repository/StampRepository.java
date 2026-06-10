package com.cafeminsu.domain.stamp.repository;

import com.cafeminsu.domain.stamp.entity.Stamp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StampRepository extends JpaRepository<Stamp, Long> {

    Optional<Stamp> findByUserIdAndStoreId(Long userId, Long storeId);

    List<Stamp> findAllByUserIdOrderByIdDesc(Long userId);
}
