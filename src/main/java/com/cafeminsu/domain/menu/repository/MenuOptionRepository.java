package com.cafeminsu.domain.menu.repository;

import com.cafeminsu.domain.menu.entity.MenuOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MenuOptionRepository extends JpaRepository<MenuOption, Long> {

    /** 메뉴 상세 조회 시 옵션 한 번에 가져오기. group, id 순서로 정렬. */
    List<MenuOption> findAllByMenuIdOrderByOptionGroupAscIdAsc(Long menuId);
}
