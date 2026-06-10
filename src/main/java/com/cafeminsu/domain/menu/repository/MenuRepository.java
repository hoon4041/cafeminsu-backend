package com.cafeminsu.domain.menu.repository;

import com.cafeminsu.domain.menu.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MenuRepository extends JpaRepository<Menu, Long> {

    /** 매장의 전체 메뉴 (등록 순서 역순) */
    List<Menu> findAllByStoreIdOrderByIdDesc(Long storeId);

    /** 매장의 카테고리별 메뉴 */
    List<Menu> findAllByStoreIdAndCategoryOrderByIdDesc(Long storeId, String category);
}
