-- =======================================================
-- [운영용] 초기 샘플 매장·메뉴 데이터
-- =======================================================
-- 성격: 서비스 오픈 시 한 번 넣는 '초기 데이터'. dev 시드와 다르게
--   - 비파괴: 기존 데이터를 절대 DELETE 하지 않는다.
--   - 멱등(idempotent): 여러 번 실행해도 같은 매장/메뉴가 중복 생성되지 않는다.
--
-- 적용(예):
--   mysql -h <운영DB호스트> -u <user> -p <DB명> < db/prod/init_sample_stores.sql
--   (로컬 docker로 검증: docker exec -i cafeminsu-mysql mysql --default-character-set=utf8mb4 -ucafeminsu -pcafeminsu cafeminsu < db/prod/init_sample_stores.sql)
--
-- 점주(owner) 지정:
--   - 이미 가입한 실제 점주에게 매장을 귀속시키려면 아래 @owner_id에 그 user_id를 넣으세요.
--   - 비워두면(기본) 샘플 플랫폼 점주 계정(nickname '민수카페')을 자동 생성해 사용합니다.
--
-- 주의(운영 스키마):
--   - 운영에서는 ddl-auto=validate + 스키마는 마이그레이션으로 관리하는 것을 권장합니다.
--   - 본 스크립트는 데이터(매장/메뉴)만 다룹니다. 테이블이 JPA로 생성되어
--     created_at/updated_at/is_available에 DB 기본값이 없으므로 명시적으로 채웁니다.
--   - 카테고리는 application.yml의 stamp.drink-categories와 일치(음료만 스탬프 적립).
-- =======================================================

USE cafeminsu;

-- 0) 점주 지정 ------------------------------------------------------------------
-- 실제 점주 user_id가 있으면 아래 한 줄을 그 값으로 바꾸세요. (예: SET @owner_id = 42;)
SET @owner_id = NULL;

-- @owner_id가 비어 있으면 샘플 플랫폼 점주 계정을 생성/사용
INSERT INTO users (email, nickname, kakao_id, role, created_at, updated_at)
SELECT 'platform@cafeminsu.local', '민수카페', 'platform-owner', 'OWNER', NOW(), NOW()
FROM DUAL
WHERE @owner_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM users WHERE kakao_id = 'platform-owner');

SET @owner_id = COALESCE(@owner_id, (SELECT id FROM users WHERE kakao_id = 'platform-owner'));

-- 1) 매장 (없을 때만 삽입) ------------------------------------------------------
INSERT INTO stores (owner_id, name, address, latitude, longitude, phone, business_hours, created_at, updated_at)
SELECT @owner_id, '민수카페 강남본점', '서울 강남구 테헤란로 152', 37.5006000, 127.0366000, '02-501-0001', '08:00-22:00', NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM stores WHERE name='민수카페 강남본점' AND owner_id=@owner_id);

INSERT INTO stores (owner_id, name, address, latitude, longitude, phone, business_hours, created_at, updated_at)
SELECT @owner_id, '민수카페 홍대점', '서울 마포구 양화로 160', 37.5559000, 126.9237000, '02-501-0002', '09:00-23:00', NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM stores WHERE name='민수카페 홍대점' AND owner_id=@owner_id);

INSERT INTO stores (owner_id, name, address, latitude, longitude, phone, business_hours, created_at, updated_at)
SELECT @owner_id, '민수카페 판교점', '경기 성남시 분당구 판교역로 235', 37.3947000, 127.1112000, '031-501-0003', '08:00-22:00', NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM stores WHERE name='민수카페 판교점' AND owner_id=@owner_id);

INSERT INTO stores (owner_id, name, address, latitude, longitude, phone, business_hours, created_at, updated_at)
SELECT @owner_id, '민수카페 구미금오공대점', '경북 구미시 대학로 61', 36.1456000, 128.3927000, '054-501-0004', '08:00-21:00', NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM stores WHERE name='민수카페 구미금오공대점' AND owner_id=@owner_id);

INSERT INTO stores (owner_id, name, address, latitude, longitude, phone, business_hours, created_at, updated_at)
SELECT @owner_id, '민수카페 대전둔산점', '대전 서구 둔산로 100', 36.3515000, 127.3850000, '042-501-0005', '08:00-22:00', NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM stores WHERE name='민수카페 대전둔산점' AND owner_id=@owner_id);

INSERT INTO stores (owner_id, name, address, latitude, longitude, phone, business_hours, created_at, updated_at)
SELECT @owner_id, '민수카페 부산서면점', '부산 부산진구 중앙대로 681', 35.1577000, 129.0594000, '051-501-0006', '08:00-23:00', NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM stores WHERE name='민수카페 부산서면점' AND owner_id=@owner_id);

-- 매장 id 변수 바인딩 (이미 있던 매장이든 방금 만든 매장이든 동일)
SET @s_gangnam = (SELECT id FROM stores WHERE name='민수카페 강남본점'     AND owner_id=@owner_id);
SET @s_hongdae = (SELECT id FROM stores WHERE name='민수카페 홍대점'       AND owner_id=@owner_id);
SET @s_pangyo  = (SELECT id FROM stores WHERE name='민수카페 판교점'       AND owner_id=@owner_id);
SET @s_gumi    = (SELECT id FROM stores WHERE name='민수카페 구미금오공대점' AND owner_id=@owner_id);
SET @s_daejeon = (SELECT id FROM stores WHERE name='민수카페 대전둔산점'    AND owner_id=@owner_id);
SET @s_busan   = (SELECT id FROM stores WHERE name='민수카페 부산서면점'    AND owner_id=@owner_id);

-- 2) 메뉴 (매장별, 같은 이름이 없을 때만 삽입) ----------------------------------
--    각 매장: 음료 5 + 디저트 1. 음료 카테고리는 적립 대상.
INSERT INTO menus (store_id, name, description, price, category, is_available, created_at, updated_at)
SELECT v.store_id, v.name, v.description, v.price, v.category, 1, NOW(), NOW()
FROM (
            SELECT @s_gangnam AS store_id, '아메리카노' AS name, '진한 에스프레소에 물을 더한 기본 커피' AS description, 4500 AS price, '커피' AS category
  UNION ALL SELECT @s_gangnam, '카페라떼',   '부드러운 우유가 어우러진 라떼',        5000, '라떼'
  UNION ALL SELECT @s_gangnam, '자몽에이드', '상큼한 자몽 과즙의 청량 에이드',       5500, '에이드'
  UNION ALL SELECT @s_gangnam, '캐모마일티', '은은한 향의 허브티',                 4500, '티'
  UNION ALL SELECT @s_gangnam, '딸기스무디', '생딸기를 갈아 만든 스무디',           6000, '스무디'
  UNION ALL SELECT @s_gangnam, '치즈케이크', '꾸덕한 뉴욕 스타일 치즈케이크',        6500, '디저트'
) v
WHERE NOT EXISTS (SELECT 1 FROM menus m WHERE m.store_id=v.store_id AND m.name=v.name);

INSERT INTO menus (store_id, name, description, price, category, is_available, created_at, updated_at)
SELECT v.store_id, v.name, v.description, v.price, v.category, 1, NOW(), NOW()
FROM (
            SELECT @s_hongdae AS store_id, '아메리카노' AS name, '진한 에스프레소에 물을 더한 기본 커피' AS description, 4500 AS price, '커피' AS category
  UNION ALL SELECT @s_hongdae, '카페라떼',   '부드러운 우유가 어우러진 라떼',  5000, '라떼'
  UNION ALL SELECT @s_hongdae, '레몬에이드', '새콤달콤 레몬 에이드',         5000, '에이드'
  UNION ALL SELECT @s_hongdae, '아이스티',   '시원한 복숭아 아이스티',       4000, '티'
  UNION ALL SELECT @s_hongdae, '망고스무디', '달콤한 망고 스무디',          6000, '스무디'
  UNION ALL SELECT @s_hongdae, '초코케이크', '진한 초콜릿 케이크',          6500, '디저트'
) v
WHERE NOT EXISTS (SELECT 1 FROM menus m WHERE m.store_id=v.store_id AND m.name=v.name);

INSERT INTO menus (store_id, name, description, price, category, is_available, created_at, updated_at)
SELECT v.store_id, v.name, v.description, v.price, v.category, 1, NOW(), NOW()
FROM (
            SELECT @s_pangyo AS store_id, '아메리카노' AS name, '진한 에스프레소에 물을 더한 기본 커피' AS description, 4500 AS price, '커피' AS category
  UNION ALL SELECT @s_pangyo, '바닐라라떼', '달콤한 바닐라 향의 라떼',   5500, '라떼'
  UNION ALL SELECT @s_pangyo, '청포도에이드','상큼한 청포도 에이드',     5300, '에이드'
  UNION ALL SELECT @s_pangyo, '루이보스티', '카페인 없는 루이보스티',    4500, '티'
  UNION ALL SELECT @s_pangyo, '바나나스무디','달콤한 바나나 스무디',     5800, '스무디'
  UNION ALL SELECT @s_pangyo, '브라우니',   '진한 초코 브라우니',       5000, '디저트'
) v
WHERE NOT EXISTS (SELECT 1 FROM menus m WHERE m.store_id=v.store_id AND m.name=v.name);

INSERT INTO menus (store_id, name, description, price, category, is_available, created_at, updated_at)
SELECT v.store_id, v.name, v.description, v.price, v.category, 1, NOW(), NOW()
FROM (
            SELECT @s_gumi AS store_id, '아메리카노' AS name, '진한 에스프레소에 물을 더한 기본 커피' AS description, 4300 AS price, '커피' AS category
  UNION ALL SELECT @s_gumi, '카페라떼',   '부드러운 우유가 어우러진 라떼', 4800, '라떼'
  UNION ALL SELECT @s_gumi, '자몽에이드', '상큼한 자몽 에이드',          5000, '에이드'
  UNION ALL SELECT @s_gumi, '유자차',     '따뜻한 유자차',              4500, '티'
  UNION ALL SELECT @s_gumi, '키위스무디', '상큼한 키위 스무디',          5800, '스무디'
  UNION ALL SELECT @s_gumi, '치즈케이크', '꾸덕한 치즈케이크',           6000, '디저트'
) v
WHERE NOT EXISTS (SELECT 1 FROM menus m WHERE m.store_id=v.store_id AND m.name=v.name);

INSERT INTO menus (store_id, name, description, price, category, is_available, created_at, updated_at)
SELECT v.store_id, v.name, v.description, v.price, v.category, 1, NOW(), NOW()
FROM (
            SELECT @s_daejeon AS store_id, '아메리카노' AS name, '진한 에스프레소에 물을 더한 기본 커피' AS description, 4300 AS price, '커피' AS category
  UNION ALL SELECT @s_daejeon, '연유라떼',   '진한 연유가 들어간 라떼', 5300, '라떼'
  UNION ALL SELECT @s_daejeon, '청귤에이드', '상큼한 청귤 에이드',      5300, '에이드'
  UNION ALL SELECT @s_daejeon, '페퍼민트티', '시원한 향의 허브티',      4500, '티'
  UNION ALL SELECT @s_daejeon, '딸기스무디', '생딸기 스무디',          5800, '스무디'
  UNION ALL SELECT @s_daejeon, '마들렌',     '버터 풍미의 마들렌',      3500, '디저트'
) v
WHERE NOT EXISTS (SELECT 1 FROM menus m WHERE m.store_id=v.store_id AND m.name=v.name);

INSERT INTO menus (store_id, name, description, price, category, is_available, created_at, updated_at)
SELECT v.store_id, v.name, v.description, v.price, v.category, 1, NOW(), NOW()
FROM (
            SELECT @s_busan AS store_id, '아메리카노' AS name, '진한 에스프레소에 물을 더한 기본 커피' AS description, 4300 AS price, '커피' AS category
  UNION ALL SELECT @s_busan, '카페모카',   '초콜릿이 더해진 모카',  5500, '라떼'
  UNION ALL SELECT @s_busan, '라임에이드', '청량한 라임 에이드',    5200, '에이드'
  UNION ALL SELECT @s_busan, '얼그레이티', '향이 진한 홍차',       4500, '티'
  UNION ALL SELECT @s_busan, '망고스무디', '달콤한 망고 스무디',    6000, '스무디'
  UNION ALL SELECT @s_busan, '스콘',       '담백한 버터 스콘',     3800, '디저트'
) v
WHERE NOT EXISTS (SELECT 1 FROM menus m WHERE m.store_id=v.store_id AND m.name=v.name);

-- 3) 메뉴 이미지 (외부 공개 플레이스홀더 LoremFlickr — 카테고리 기반) ------------
--    아직 이미지가 없는 메뉴에만 채운다(기존 값 보존). 운영에선 실제 사진 URL로 교체.
UPDATE menus
SET image_url = CASE category
    WHEN '커피'   THEN 'https://loremflickr.com/400/400/coffee?lock=21'
    WHEN '라떼'   THEN 'https://loremflickr.com/400/400/cafe,latte?lock=22'
    WHEN '에이드' THEN 'https://loremflickr.com/400/400/lemonade?lock=23'
    WHEN '티'     THEN 'https://loremflickr.com/400/400/tea,cup?lock=24'
    WHEN '스무디' THEN 'https://loremflickr.com/400/400/smoothie?lock=25'
    WHEN '디저트' THEN 'https://loremflickr.com/400/400/cake,dessert?lock=26'
    ELSE image_url
END
WHERE store_id IN (SELECT id FROM stores WHERE owner_id = @owner_id)
  AND (image_url IS NULL OR image_url = '');
