-- =======================================================
-- 로컬 테스트용 시드 데이터 — 점주 1명 + 매장(서울 2 + 구미권 6) + 매장별 메뉴
-- =======================================================
-- 적용:
--   docker exec -i cafeminsu-mysql mysql --default-character-set=utf8mb4 -ucafeminsu -pcafeminsu cafeminsu < db/seed/seed_dev.sql
--
-- 로그인:
--   POST /api/dev/login     { "nickname": "시드점주", "role": "OWNER" }       → 시드 매장 소유 점주(로컬 전용)
--   POST /api/dev/login     { "nickname": "테스트고객" }                      → 일반 고객(추천/주문/스탬프)
--   POST /api/user/owner-login { "loginId": "owner01", "password": "owner1234" } → 점주 ID/PW 로그인(운영에서도 동작)
--
-- 점주 비밀번호는 BCrypt 해시로 저장한다(평문 금지). 아래 해시는 "owner1234".
--   새 비번 해시 생성 예) python -c "import bcrypt;print(bcrypt.hashpw(b'새비번',bcrypt.gensalt(10)).decode())"
--
-- 이 스크립트는 재실행 안전(idempotent): 실행 시 기존 시드(시드점주 소유 매장·메뉴)를 지우고 다시 만든다.
--   주의: 시드 매장에 '주문'이 있으면 FK(RESTRICT)로 삭제가 막힐 수 있다(주문은 매장 참조).
--         그 경우 해당 주문을 먼저 정리하거나, DB를 새로 띄우고 실행할 것.
--
-- 테이블이 JPA(ddl-auto)로 생성되어 created_at/updated_at/is_available에 DB 기본값이 없으므로
-- raw SQL에서는 명시적으로 채운다. 카테고리는 application.yml의 stamp.drink-categories와 일치(음료 적립).
-- =======================================================

USE cafeminsu;

-- 0) 점주 유저 (dev-login nickname '시드점주'와 동일 kakao_id) --------------------
--    login_id/password를 함께 심어 ID/PW 로그인(/api/user/owner-login)도 가능.
--    password 해시는 "owner1234"의 BCrypt 값.
INSERT INTO users (email, nickname, kakao_id, login_id, password, role, created_at, updated_at)
VALUES ('owner@seed.local', '시드점주', 'dev-시드점주', 'owner01',
        '$2b$10$NepyPgqOsOUjI/0RXq.GNeTtDCd/cd439XNcamThTnEOlLcLJLqFS',
        'OWNER', NOW(), NOW())
ON DUPLICATE KEY UPDATE
        role = 'OWNER',
        login_id = 'owner01',
        password = '$2b$10$NepyPgqOsOUjI/0RXq.GNeTtDCd/cd439XNcamThTnEOlLcLJLqFS';

SET @owner_id = (SELECT id FROM users WHERE kakao_id = 'dev-시드점주');

-- 1) 기존 시드 정리 (재실행 안전) ------------------------------------------------
DELETE m FROM menus m JOIN stores s ON s.id = m.store_id WHERE s.owner_id = @owner_id;
DELETE FROM stores WHERE owner_id = @owner_id;

-- 2) 매장 ----------------------------------------------------------------------
--    서울 2개 + 구미권 6개 (구미 좌표는 추천 기능 기본 좌표 36.1085/128.4182 인근)
INSERT INTO stores (owner_id, name, address, latitude, longitude, phone, business_hours, created_at, updated_at)
VALUES
  (@owner_id, '민수카페 강남점',     '서울 강남구 테헤란로 123',  37.4979000, 127.0276000, '02-111-1111',  '08:00-22:00', NOW(), NOW()),
  (@owner_id, '민수카페 홍대점',     '서울 마포구 양화로 45',    37.5571000, 126.9245000, '02-222-2222',  '09:00-23:00', NOW(), NOW()),
  (@owner_id, '민수카페 구미인동점',  '경북 구미시 인동가산로 10', 36.0966000, 128.4206000, '054-301-0001', '07:30-22:00', NOW(), NOW()),
  (@owner_id, '민수카페 구미옥계점',  '경북 구미시 옥계2공단로 5', 36.1432000, 128.4181000, '054-301-0002', '08:00-22:00', NOW(), NOW()),
  (@owner_id, '민수카페 금오공대점',  '경북 구미시 대학로 61',    36.1456000, 128.3927000, '054-301-0003', '08:00-21:00', NOW(), NOW()),
  (@owner_id, '민수카페 구미원평점',  '경북 구미시 원평로 20',    36.1290000, 128.3380000, '054-301-0004', '08:00-22:00', NOW(), NOW()),
  (@owner_id, '민수카페 구미산동점',  '경북 구미시 산동읍 첨단기업1로', 36.1280000, 128.4630000, '054-301-0005', '08:00-22:00', NOW(), NOW()),
  (@owner_id, '민수카페 구미봉곡점',  '경북 구미시 봉곡로 30',    36.1520000, 128.3700000, '054-301-0006', '08:00-22:00', NOW(), NOW());

SET @s_gangnam  = (SELECT id FROM stores WHERE name='민수카페 강남점'    AND owner_id=@owner_id);
SET @s_hongdae  = (SELECT id FROM stores WHERE name='민수카페 홍대점'    AND owner_id=@owner_id);
SET @s_indong   = (SELECT id FROM stores WHERE name='민수카페 구미인동점' AND owner_id=@owner_id);
SET @s_okgye    = (SELECT id FROM stores WHERE name='민수카페 구미옥계점' AND owner_id=@owner_id);
SET @s_kit      = (SELECT id FROM stores WHERE name='민수카페 금오공대점' AND owner_id=@owner_id);
SET @s_wonpyeong= (SELECT id FROM stores WHERE name='민수카페 구미원평점' AND owner_id=@owner_id);
SET @s_sandong  = (SELECT id FROM stores WHERE name='민수카페 구미산동점' AND owner_id=@owner_id);
SET @s_bonggok  = (SELECT id FROM stores WHERE name='민수카페 구미봉곡점' AND owner_id=@owner_id);

-- 3) 메뉴 ----------------------------------------------------------------------
-- 강남점 (음료 6 + 디저트 1)
INSERT INTO menus (store_id, name, description, price, category, is_available, created_at, updated_at) VALUES
  (@s_gangnam, '아메리카노', '진한 에스프레소에 물을 더한 기본 커피', 4500, '커피',   1, NOW(), NOW()),
  (@s_gangnam, '카페라떼',   '부드러운 우유가 어우러진 라떼',        5000, '라떼',   1, NOW(), NOW()),
  (@s_gangnam, '바닐라라떼', '달콤한 바닐라 향의 라떼',             5500, '라떼',   1, NOW(), NOW()),
  (@s_gangnam, '자몽에이드', '상큼한 자몽 과즙의 청량 에이드',       5500, '에이드', 1, NOW(), NOW()),
  (@s_gangnam, '딸기스무디', '생딸기를 갈아 만든 스무디',           6000, '스무디', 1, NOW(), NOW()),
  (@s_gangnam, '캐모마일티', '은은한 향의 허브티',                 4500, '티',     1, NOW(), NOW()),
  (@s_gangnam, '치즈케이크', '꾸덕한 뉴욕 스타일 치즈케이크',        6500, '디저트', 1, NOW(), NOW());

-- 홍대점 (음료 5 + 디저트 1)
INSERT INTO menus (store_id, name, description, price, category, is_available, created_at, updated_at) VALUES
  (@s_hongdae, '아메리카노', '진한 에스프레소에 물을 더한 기본 커피', 4500, '커피',   1, NOW(), NOW()),
  (@s_hongdae, '카페라떼',   '부드러운 우유가 어우러진 라떼',        5000, '라떼',   1, NOW(), NOW()),
  (@s_hongdae, '아이스티',   '시원한 복숭아 아이스티',             4000, '티',     1, NOW(), NOW()),
  (@s_hongdae, '레몬에이드', '새콤달콤 레몬 에이드',               5000, '에이드', 1, NOW(), NOW()),
  (@s_hongdae, '망고스무디', '달콤한 망고 스무디',                 6000, '스무디', 1, NOW(), NOW()),
  (@s_hongdae, '초코케이크', '진한 초콜릿 케이크',                6500, '디저트', 1, NOW(), NOW());

-- 구미인동점 (음료 5 + 디저트 1)
INSERT INTO menus (store_id, name, description, price, category, is_available, created_at, updated_at) VALUES
  (@s_indong, '아메리카노', '진한 에스프레소에 물을 더한 기본 커피', 4300, '커피',   1, NOW(), NOW()),
  (@s_indong, '카페라떼',   '부드러운 우유가 어우러진 라떼',        4800, '라떼',   1, NOW(), NOW()),
  (@s_indong, '청포도에이드','상큼한 청포도 에이드',               5300, '에이드', 1, NOW(), NOW()),
  (@s_indong, '녹차라떼',   '고소한 녹차 라떼',                  5300, '라떼',   1, NOW(), NOW()),
  (@s_indong, '유자차',     '따뜻한 유자차',                    4500, '티',     1, NOW(), NOW()),
  (@s_indong, '브라우니',   '진한 초코 브라우니',                5000, '디저트', 1, NOW(), NOW());

-- 구미옥계점 (음료 5)
INSERT INTO menus (store_id, name, description, price, category, is_available, created_at, updated_at) VALUES
  (@s_okgye, '아메리카노',  '진한 에스프레소에 물을 더한 기본 커피', 4300, '커피',   1, NOW(), NOW()),
  (@s_okgye, '카페라떼',    '부드러운 우유가 어우러진 라떼',        4800, '라떼',   1, NOW(), NOW()),
  (@s_okgye, '복숭아에이드', '향긋한 복숭아 에이드',               5200, '에이드', 1, NOW(), NOW()),
  (@s_okgye, '얼그레이티',  '향이 진한 홍차',                    4500, '티',     1, NOW(), NOW()),
  (@s_okgye, '블루베리스무디','블루베리 스무디',                 6000, '스무디', 1, NOW(), NOW());

-- 금오공대점 (음료 5)
INSERT INTO menus (store_id, name, description, price, category, is_available, created_at, updated_at) VALUES
  (@s_kit, '아메리카노',   '진한 에스프레소에 물을 더한 기본 커피', 4000, '커피',   1, NOW(), NOW()),
  (@s_kit, '바닐라라떼',   '달콤한 바닐라 라떼',                 5000, '라떼',   1, NOW(), NOW()),
  (@s_kit, '자몽에이드',   '상큼한 자몽 에이드',                 5000, '에이드', 1, NOW(), NOW()),
  (@s_kit, '캐모마일티',   '은은한 허브티',                     4300, '티',     1, NOW(), NOW()),
  (@s_kit, '딸기스무디',   '생딸기 스무디',                     5800, '스무디', 1, NOW(), NOW());

-- 구미원평점 (음료 5)
INSERT INTO menus (store_id, name, description, price, category, is_available, created_at, updated_at) VALUES
  (@s_wonpyeong, '아메리카노', '진한 에스프레소에 물을 더한 기본 커피', 4300, '커피',   1, NOW(), NOW()),
  (@s_wonpyeong, '카페라떼',   '부드러운 라떼',                    4800, '라떼',   1, NOW(), NOW()),
  (@s_wonpyeong, '라임에이드', '청량한 라임 에이드',               5200, '에이드', 1, NOW(), NOW()),
  (@s_wonpyeong, '루이보스티', '카페인 없는 루이보스티',            4500, '티',     1, NOW(), NOW()),
  (@s_wonpyeong, '바나나스무디','달콤한 바나나 스무디',             5800, '스무디', 1, NOW(), NOW());

-- 구미산동점 (음료 5)
INSERT INTO menus (store_id, name, description, price, category, is_available, created_at, updated_at) VALUES
  (@s_sandong, '아메리카노', '진한 에스프레소에 물을 더한 기본 커피', 4300, '커피',   1, NOW(), NOW()),
  (@s_sandong, '연유라떼',   '진한 연유가 들어간 라떼',            5300, '라떼',   1, NOW(), NOW()),
  (@s_sandong, '청귤에이드', '상큼한 청귤 에이드',                5300, '에이드', 1, NOW(), NOW()),
  (@s_sandong, '페퍼민트티', '시원한 향의 허브티',                4500, '티',     1, NOW(), NOW()),
  (@s_sandong, '키위스무디', '상큼한 키위 스무디',                6000, '스무디', 1, NOW(), NOW());

-- 구미봉곡점 (음료 5)
INSERT INTO menus (store_id, name, description, price, category, is_available, created_at, updated_at) VALUES
  (@s_bonggok, '아메리카노', '진한 에스프레소에 물을 더한 기본 커피', 4300, '커피',   1, NOW(), NOW()),
  (@s_bonggok, '카페모카',   '초콜릿이 더해진 모카',               5500, '라떼',   1, NOW(), NOW()),
  (@s_bonggok, '자몽에이드', '상큼한 자몽 에이드',                5300, '에이드', 1, NOW(), NOW()),
  (@s_bonggok, '캐모마일티', '은은한 허브티',                     4500, '티',     1, NOW(), NOW()),
  (@s_bonggok, '망고스무디', '달콤한 망고 스무디',                6000, '스무디', 1, NOW(), NOW());

-- 4) 메뉴 이미지: 시드 메뉴는 이미지 없이(빈값) 생성한다.
--    이미지는 등록/수정 시 업로드한 경우에만 image_url에 채워진다(번들 SVG 미사용).

-- 5) 메뉴 옵션 (카테고리 기반 자동 생성) ----------------------------------------
--    메뉴 id는 매장마다 auto_increment라 직접 못 박으므로, 카테고리로 매칭해 일괄 INSERT.
--    템플릿의 categories(콤마 목록)에 메뉴 category가 포함되면 해당 옵션을 단다.
--      음료(커피/라떼/에이드/티/스무디): 사이즈 + 온도, 커피·라떼는 샷, 라떼는 시럽까지.
--      디저트: 옵션 없음(의도된 빈 배열).
--    menu_options.created_at/updated_at도 DB 기본값이 없어 명시적으로 채운다.
--    menus 삭제 시 FK(ON DELETE CASCADE)로 옵션도 함께 지워지므로 재실행 안전.
INSERT INTO menu_options (menu_id, option_group, option_name, additional_price, is_default, created_at, updated_at)
SELECT m.id, t.option_group, t.option_name, t.additional_price, t.is_default, NOW(), NOW()
FROM menus m
JOIN stores s ON s.id = m.store_id AND s.owner_id = @owner_id
JOIN (
    -- 온도: 핫 음료군은 HOT 기본, 콜드 음료군(에이드/스무디)은 ICE 고정
    SELECT '커피,라떼,티'              AS categories, '온도'   AS option_group, 'HOT'     AS option_name, 0   AS additional_price, 1 AS is_default
    UNION ALL SELECT '커피,라떼,티',               '온도',   'ICE',     0,   0
    UNION ALL SELECT '에이드,스무디',              '온도',   'ICE',     0,   1
    -- 사이즈: 모든 음료
    UNION ALL SELECT '커피,라떼,에이드,티,스무디', '사이즈', 'Regular', 0,   1
    UNION ALL SELECT '커피,라떼,에이드,티,스무디', '사이즈', 'Large',   500, 0
    -- 샷: 커피·라떼
    UNION ALL SELECT '커피,라떼',                  '샷',     '기본',    0,   1
    UNION ALL SELECT '커피,라떼',                  '샷',     '샷 추가', 500, 0
    -- 시럽: 라떼
    UNION ALL SELECT '라떼',                       '시럽',   '없음',    0,   1
    UNION ALL SELECT '라떼',                       '시럽',   '바닐라',  300, 0
    UNION ALL SELECT '라떼',                       '시럽',   '헤이즐넛', 300, 0
) t ON FIND_IN_SET(m.category, t.categories);
