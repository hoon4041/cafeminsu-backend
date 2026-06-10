package com.cafeminsu.domain.gifticon.repository;

import com.cafeminsu.domain.gifticon.entity.GifticonUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GifticonUsageRepository extends JpaRepository<GifticonUsage, Long> {

    List<GifticonUsage> findAllByGifticonIdOrderByIdDesc(Long gifticonId);
}
