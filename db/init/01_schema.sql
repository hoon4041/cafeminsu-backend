-- =======================================================
-- 카페민수 MySQL DDL
-- =======================================================
-- 권위 기준: src/main/java 의 JPA 엔티티 (이 파일은 엔티티와 1:1로 맞춘다).
-- charset: utf8mb4 / engine: InnoDB
--
-- 감사 컬럼 규칙:
--   - BaseEntity 상속 엔티티 → created_at + updated_at
--   - @CreatedDate만 있는 엔티티 → created_at(또는 used_at)만
--   - 감사 없음(OrderItem/OrderItemOption) → 타임스탬프 컬럼 없음
-- 운영에서는 ddl-auto=validate + 이 파일을 단일 소스로 관리하는 것을 권장.
-- =======================================================

CREATE DATABASE IF NOT EXISTS cafeminsu
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE cafeminsu;

-- -------------------------------------------------------
-- 1. USERS (회원 / 점주)  [BaseEntity]
-- -------------------------------------------------------
CREATE TABLE users (
  id                  BIGINT        NOT NULL AUTO_INCREMENT,
  email               VARCHAR(100)  NULL,
  nickname            VARCHAR(20)   NULL UNIQUE,
  kakao_id            VARCHAR(50)   NULL UNIQUE,
  login_id            VARCHAR(50)   NULL UNIQUE  COMMENT '점주 ID/PW 로그인용. 카카오 유저는 NULL',
  password            VARCHAR(100)  NULL         COMMENT 'BCrypt 해시. 평문 저장 금지. 카카오 유저는 NULL',
  profile_image_url   VARCHAR(500)  NULL,
  role                ENUM('CUSTOMER','OWNER') NOT NULL DEFAULT 'CUSTOMER',
  latitude            DECIMAL(10,7) NULL,
  longitude           DECIMAL(10,7) NULL,
  fcm_token           VARCHAR(255)  NULL,
  created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX idx_users_kakao (kakao_id)
) ENGINE=InnoDB;

-- -------------------------------------------------------
-- 2. REFRESH_TOKENS (JWT 갱신용)  [created_at만]
-- -------------------------------------------------------
CREATE TABLE refresh_tokens (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  user_id      BIGINT       NOT NULL,
  token        VARCHAR(500) NOT NULL,
  expires_at   DATETIME     NOT NULL,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX idx_refresh_user (user_id),
  INDEX idx_refresh_token (token(255)),
  CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- -------------------------------------------------------
-- 3. STORES (매장)  [BaseEntity + soft delete]
-- -------------------------------------------------------
CREATE TABLE stores (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  owner_id        BIGINT        NOT NULL,
  name            VARCHAR(100)  NOT NULL,
  address         VARCHAR(200)  NOT NULL,
  latitude        DECIMAL(10,7) NULL,
  longitude       DECIMAL(10,7) NULL,
  phone           VARCHAR(30)   NULL,
  business_hours  VARCHAR(100)  NULL,
  image_url       VARCHAR(500)  NULL,
  deleted_at      DATETIME      NULL  COMMENT 'soft delete — NULL이면 활성',
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX idx_store_owner (owner_id),
  INDEX idx_store_name (name),
  INDEX idx_store_deleted (deleted_at),
  CONSTRAINT fk_stores_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- -------------------------------------------------------
-- 4. MENUS (메뉴)  [BaseEntity + soft delete]
-- -------------------------------------------------------
CREATE TABLE menus (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  store_id      BIGINT       NOT NULL,
  name          VARCHAR(100) NOT NULL,
  description   VARCHAR(500) NULL,
  price         INT          NOT NULL,
  category      VARCHAR(50)  NULL,
  image_url     VARCHAR(500) NULL,
  is_available  BOOLEAN      NOT NULL DEFAULT TRUE,
  deleted_at    DATETIME     NULL  COMMENT 'soft delete — NULL이면 활성',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX idx_menu_store (store_id),
  INDEX idx_menu_category (category),
  INDEX idx_menu_deleted (deleted_at),
  CONSTRAINT fk_menus_store FOREIGN KEY (store_id) REFERENCES stores(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- -------------------------------------------------------
-- 5. MENU_OPTIONS (메뉴 옵션)  [BaseEntity]
-- -------------------------------------------------------
CREATE TABLE menu_options (
  id                BIGINT      NOT NULL AUTO_INCREMENT,
  menu_id           BIGINT      NOT NULL,
  option_group      VARCHAR(30) NOT NULL  COMMENT '온도, 사이즈, 샷, 시럽 등',
  option_name       VARCHAR(50) NOT NULL  COMMENT 'HOT, ICE, Large 등',
  additional_price  INT         NOT NULL DEFAULT 0,
  is_default        BOOLEAN     NOT NULL DEFAULT FALSE,
  created_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX idx_menuopt_menu (menu_id),
  INDEX idx_menuopt_group (option_group),
  CONSTRAINT fk_menu_options_menu FOREIGN KEY (menu_id) REFERENCES menus(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- -------------------------------------------------------
-- 6. ORDERS (주문)  [BaseEntity]
-- -------------------------------------------------------
CREATE TABLE orders (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  user_id       BIGINT       NULL  COMMENT '키오스크 비회원 주문 시 NULL',
  store_id      BIGINT       NOT NULL,
  order_number  VARCHAR(10)  NOT NULL  COMMENT '픽업용 표시 번호',
  order_type    ENUM('MOBILE','KIOSK') NOT NULL,
  status        ENUM('PENDING','ACCEPTED','READY','DONE','CANCELLED') NOT NULL DEFAULT 'PENDING',
  total_amount  INT          NOT NULL,
  cancel_reason VARCHAR(200) NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_store_number (store_id, order_number),
  INDEX idx_order_user (user_id),
  INDEX idx_order_store (store_id),
  INDEX idx_order_status (status),
  CONSTRAINT fk_orders_user  FOREIGN KEY (user_id)  REFERENCES users(id)  ON DELETE SET NULL,
  CONSTRAINT fk_orders_store FOREIGN KEY (store_id) REFERENCES stores(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- -------------------------------------------------------
-- 7. ORDER_ITEMS (주문 아이템)  [감사 컬럼 없음]
-- -------------------------------------------------------
CREATE TABLE order_items (
  id          BIGINT  NOT NULL AUTO_INCREMENT,
  order_id    BIGINT  NOT NULL,
  menu_id     BIGINT  NOT NULL  COMMENT '메뉴 PK 스냅샷(메뉴 soft delete돼도 보존)',
  quantity    INT     NOT NULL DEFAULT 1,
  unit_price  INT     NOT NULL  COMMENT '주문 시점 단가 스냅샷',
  PRIMARY KEY (id),
  INDEX idx_oitem_order (order_id),
  INDEX idx_oitem_menu  (menu_id),
  CONSTRAINT fk_oitem_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
  CONSTRAINT fk_oitem_menu  FOREIGN KEY (menu_id)  REFERENCES menus(id)  ON DELETE RESTRICT
) ENGINE=InnoDB;

-- -------------------------------------------------------
-- 8. ORDER_ITEM_OPTIONS (주문 시 선택된 옵션)  [감사 컬럼 없음]
-- -------------------------------------------------------
-- menu_option_id는 의도적으로 FK를 걸지 않는다(옵션 삭제돼도 historical 스냅샷 보존).
CREATE TABLE order_item_options (
  id                      BIGINT NOT NULL AUTO_INCREMENT,
  order_item_id           BIGINT NOT NULL,
  menu_option_id          BIGINT NOT NULL  COMMENT '옵션 PK 스냅샷(FK 없음)',
  option_price_snapshot   INT    NOT NULL  COMMENT '옵션 가격이 나중에 바뀌어도 안전',
  PRIMARY KEY (id),
  INDEX idx_oiopt_item (order_item_id),
  CONSTRAINT fk_oiopt_order_item FOREIGN KEY (order_item_id) REFERENCES order_items(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- -------------------------------------------------------
-- 9. PAYMENTS (결제)  [BaseEntity]
-- -------------------------------------------------------
-- 분할결제(기프티콘+카드)는 한 주문에 row 2개가 생길 수 있다 → order_id는 UNIQUE가 아니다.
CREATE TABLE payments (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  order_id          BIGINT       NOT NULL,
  merchant_uid      VARCHAR(100) NULL UNIQUE  COMMENT '카드 결제분 식별자. GIFTICON 분은 NULL',
  amount            INT          NOT NULL,
  method            ENUM('CARD','GIFTICON') NOT NULL,
  status            ENUM('READY','PAID','FAILED','REFUNDED') NOT NULL DEFAULT 'READY',
  gifticon_id       BIGINT       NULL  COMMENT 'GIFTICON 결제분의 기프티콘 ID. CARD는 NULL',
  kakaopay_tid      VARCHAR(100) NULL  COMMENT '카카오페이 ready tid',
  kakaopay_aid      VARCHAR(100) NULL  COMMENT '카카오페이 approve aid(승인번호)',
  paid_at           DATETIME     NULL,
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX idx_pay_order  (order_id),
  INDEX idx_pay_status (status),
  INDEX idx_pay_paidat (paid_at),
  CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- -------------------------------------------------------
-- 10. GIFTICONS (기프티콘 - 링크/claim 방식)  [BaseEntity]
-- -------------------------------------------------------
CREATE TABLE gifticons (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  sender_id       BIGINT       NOT NULL,
  receiver_id     BIGINT       NULL  COMMENT '귀속(claim) 완료 시 수신자. 미귀속이면 NULL',
  receiver_phone  VARCHAR(20)  NULL  COMMENT '비회원 수신 시(선택)',
  amount          INT          NOT NULL  COMMENT '액면가',
  balance         INT          NOT NULL  COMMENT '남은 잔액',
  claim_token     VARCHAR(100) NOT NULL  COMMENT '등록(claim)용 1회성 코드 GFT-XXXX-XXXX',
  status          ENUM('UNUSED','PARTIAL','USED','EXPIRED') NOT NULL DEFAULT 'UNUSED',
  message         VARCHAR(200) NULL,
  expires_at      DATETIME     NOT NULL,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY idx_gift_claim_token (claim_token),
  INDEX idx_gift_sender   (sender_id),
  INDEX idx_gift_receiver (receiver_id),
  INDEX idx_gift_status   (status),
  CONSTRAINT fk_gifticons_sender   FOREIGN KEY (sender_id)   REFERENCES users(id) ON DELETE RESTRICT,
  CONSTRAINT fk_gifticons_receiver FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- -------------------------------------------------------
-- 11. GIFTICON_USAGES (기프티콘 사용 내역)  [used_at만]
-- -------------------------------------------------------
CREATE TABLE gifticon_usages (
  id              BIGINT   NOT NULL AUTO_INCREMENT,
  gifticon_id     BIGINT   NOT NULL,
  order_id        BIGINT   NOT NULL,
  used_amount     INT      NOT NULL,
  balance_after   INT      NOT NULL  COMMENT '사용 직후 잔액',
  used_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX idx_gu_gifticon (gifticon_id),
  INDEX idx_gu_order    (order_id),
  CONSTRAINT fk_gu_gifticon FOREIGN KEY (gifticon_id) REFERENCES gifticons(id) ON DELETE RESTRICT,
  CONSTRAINT fk_gu_order    FOREIGN KEY (order_id)    REFERENCES orders(id)    ON DELETE RESTRICT
) ENGINE=InnoDB;

-- -------------------------------------------------------
-- 12. STAMPS (사용자 × 매장 스탬프 누적)  [BaseEntity]
-- -------------------------------------------------------
CREATE TABLE stamps (
  id          BIGINT   NOT NULL AUTO_INCREMENT,
  user_id     BIGINT   NOT NULL,
  store_id    BIGINT   NOT NULL,
  count       INT      NOT NULL DEFAULT 0,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_stamp_user_store (user_id, store_id),
  INDEX idx_stamp_user  (user_id),
  INDEX idx_stamp_store (store_id),
  CONSTRAINT fk_stamps_user  FOREIGN KEY (user_id)  REFERENCES users(id)  ON DELETE CASCADE,
  CONSTRAINT fk_stamps_store FOREIGN KEY (store_id) REFERENCES stores(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- -------------------------------------------------------
-- 13. STAMP_HISTORIES (스탬프 적립 내역)  [created_at만]
-- -------------------------------------------------------
CREATE TABLE stamp_histories (
  id             BIGINT   NOT NULL AUTO_INCREMENT,
  stamp_id       BIGINT   NOT NULL,
  order_id       BIGINT   NOT NULL,
  earned_count   INT      NOT NULL  COMMENT '음료 1잔당 1개',
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX idx_sh_stamp (stamp_id),
  INDEX idx_sh_order (order_id),
  CONSTRAINT fk_sh_stamp FOREIGN KEY (stamp_id) REFERENCES stamps(id) ON DELETE CASCADE,
  CONSTRAINT fk_sh_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- -------------------------------------------------------
-- 14. NOTIFICATIONS (FCM 알림 내역)  [BaseEntity]
-- -------------------------------------------------------
CREATE TABLE notifications (
  id                 BIGINT       NOT NULL AUTO_INCREMENT,
  user_id            BIGINT       NOT NULL,
  title              VARCHAR(100) NOT NULL,
  body               VARCHAR(500) NULL,
  type               ENUM('ORDER','GIFTICON','STAMP') NOT NULL,
  is_read            BOOLEAN      NOT NULL DEFAULT FALSE,
  related_entity_id  BIGINT       NULL  COMMENT 'order_id, stamp_id 등 — 알림 클릭 시 진입',
  created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX idx_noti_user (user_id),
  INDEX idx_noti_user_unread (user_id, is_read),
  CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;
