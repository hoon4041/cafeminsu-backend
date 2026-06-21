-- =======================================================
-- 카페민수 ERD v2 - MySQL DDL
-- =======================================================
-- 업로드된 ERD 기준 (PAYMENTS 1:1, method=CARD/GIFTICON)
-- charset: utf8mb4 / engine: InnoDB
-- =======================================================

CREATE DATABASE IF NOT EXISTS cafeminsu
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE cafeminsu;

-- -------------------------------------------------------
-- 1. USERS (회원 / 점주)
-- -------------------------------------------------------
CREATE TABLE users (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,
  email               VARCHAR(100) NULL,
  nickname            VARCHAR(20)  NULL UNIQUE,
  kakao_id            VARCHAR(50)  NULL UNIQUE,
  login_id            VARCHAR(50)  NULL UNIQUE  COMMENT '점주 ID/PW 로그인용. 카카오 유저는 NULL',
  password            VARCHAR(100) NULL         COMMENT 'BCrypt 해시. 평문 저장 금지. 카카오 유저는 NULL',
  profile_image_url   VARCHAR(500) NULL,
  role                ENUM('CUSTOMER','OWNER') NOT NULL DEFAULT 'CUSTOMER',
  latitude            DECIMAL(10,7) NULL,
  longitude           DECIMAL(10,7) NULL,
  fcm_token           VARCHAR(255) NULL,
  created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX idx_users_kakao (kakao_id)
) ENGINE=InnoDB;

-- -------------------------------------------------------
-- 2. REFRESH_TOKENS (JWT 갱신용)
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
-- 3. STORES (매장)
-- -------------------------------------------------------
CREATE TABLE stores (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  owner_id        BIGINT        NOT NULL,
  name            VARCHAR(100)  NOT NULL,
  address         VARCHAR(200)  NULL,
  latitude        DECIMAL(10,7) NULL,
  longitude       DECIMAL(10,7) NULL,
  phone           VARCHAR(20)   NULL,
  business_hours  VARCHAR(100)  NULL,
  image_url       VARCHAR(500)  NULL,
  is_active       BOOLEAN       NOT NULL DEFAULT TRUE,
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX idx_stores_owner (owner_id),
  INDEX idx_stores_location (latitude, longitude),
  CONSTRAINT fk_stores_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- -------------------------------------------------------
-- 4. MENUS (메뉴)
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
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX idx_menus_store (store_id),
  CONSTRAINT fk_menus_store FOREIGN KEY (store_id) REFERENCES stores(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- -------------------------------------------------------
-- 5. MENU_OPTIONS (메뉴 옵션)
-- -------------------------------------------------------
CREATE TABLE menu_options (
  id                BIGINT      NOT NULL AUTO_INCREMENT,
  menu_id           BIGINT      NOT NULL,
  option_group      VARCHAR(30) NOT NULL  COMMENT 'size, temp, shot, syrup 등',
  option_name       VARCHAR(50) NOT NULL  COMMENT 'L, ICE, +1 등',
  additional_price  INT         NOT NULL DEFAULT 0,
  is_default        BOOLEAN     NOT NULL DEFAULT FALSE,
  PRIMARY KEY (id),
  INDEX idx_menu_options_menu (menu_id),
  CONSTRAINT fk_menu_options_menu FOREIGN KEY (menu_id) REFERENCES menus(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- -------------------------------------------------------
-- 6. ORDERS (주문)
-- -------------------------------------------------------
CREATE TABLE orders (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  user_id       BIGINT       NULL  COMMENT '키오스크 비회원 주문 시 NULL',
  store_id      BIGINT       NOT NULL,
  order_number  VARCHAR(20)  NOT NULL  COMMENT '픽업용 표시 번호',
  order_type    ENUM('MOBILE','KIOSK') NOT NULL,
  status        ENUM('PENDING','ACCEPTED','READY','DONE','CANCELLED') NOT NULL DEFAULT 'PENDING',
  order_method  ENUM('VOICE','MANUAL') NOT NULL DEFAULT 'MANUAL',
  total_amount  INT          NOT NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX idx_orders_user (user_id),
  INDEX idx_orders_store_status (store_id, status),
  INDEX idx_orders_created (created_at),
  CONSTRAINT fk_orders_user  FOREIGN KEY (user_id)  REFERENCES users(id)  ON DELETE SET NULL,
  CONSTRAINT fk_orders_store FOREIGN KEY (store_id) REFERENCES stores(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- -------------------------------------------------------
-- 7. ORDER_ITEMS (주문 아이템)
-- -------------------------------------------------------
CREATE TABLE order_items (
  id          BIGINT  NOT NULL AUTO_INCREMENT,
  order_id    BIGINT  NOT NULL,
  menu_id     BIGINT  NOT NULL,
  quantity    INT     NOT NULL DEFAULT 1,
  unit_price  INT     NOT NULL  COMMENT '주문 시점 가격 스냅샷',
  PRIMARY KEY (id),
  INDEX idx_order_items_order (order_id),
  INDEX idx_order_items_menu  (menu_id),
  CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
  CONSTRAINT fk_order_items_menu  FOREIGN KEY (menu_id)  REFERENCES menus(id)  ON DELETE RESTRICT
) ENGINE=InnoDB;

-- -------------------------------------------------------
-- 8. ORDER_ITEM_OPTIONS (주문 시 선택된 옵션)
-- -------------------------------------------------------
CREATE TABLE order_item_options (
  id                      BIGINT NOT NULL AUTO_INCREMENT,
  order_item_id           BIGINT NOT NULL,
  menu_option_id          BIGINT NOT NULL,
  option_price_snapshot   INT    NOT NULL  COMMENT '옵션 가격이 나중에 바뀌어도 안전',
  PRIMARY KEY (id),
  INDEX idx_oio_item   (order_item_id),
  INDEX idx_oio_option (menu_option_id),
  CONSTRAINT fk_oio_order_item  FOREIGN KEY (order_item_id)  REFERENCES order_items(id)  ON DELETE CASCADE,
  CONSTRAINT fk_oio_menu_option FOREIGN KEY (menu_option_id) REFERENCES menu_options(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- -------------------------------------------------------
-- 9. PAYMENTS (결제 - 1:1 with ORDERS)
-- -------------------------------------------------------
CREATE TABLE payments (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  order_id          BIGINT       NOT NULL,
  portone_imp_uid   VARCHAR(50)  NULL UNIQUE,
  amount            INT          NOT NULL,
  method            ENUM('CARD','GIFTICON') NOT NULL,
  status            ENUM('READY','PAID','FAILED','REFUNDED') NOT NULL DEFAULT 'READY',
  paid_at           DATETIME     NULL,
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_payments_order (order_id),  -- 1:1 보장
  CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- -------------------------------------------------------
-- 10. GIFTICONS (기프티콘 - 플랫폼 전체 사용)
-- -------------------------------------------------------
CREATE TABLE gifticons (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  sender_id       BIGINT       NOT NULL,
  receiver_id     BIGINT       NULL  COMMENT '회원 수신 시',
  receiver_phone  VARCHAR(20)  NULL  COMMENT '비회원 수신 시',
  amount          INT          NOT NULL  COMMENT '액면가',
  balance         INT          NOT NULL  COMMENT '남은 잔액',
  qr_code         VARCHAR(100) NOT NULL UNIQUE,
  status          ENUM('UNUSED','PARTIAL','USED','EXPIRED') NOT NULL DEFAULT 'UNUSED',
  message         VARCHAR(200) NULL,
  expires_at      DATETIME     NULL,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX idx_gifticons_sender   (sender_id),
  INDEX idx_gifticons_receiver (receiver_id),
  INDEX idx_gifticons_status   (status),
  CONSTRAINT fk_gifticons_sender   FOREIGN KEY (sender_id)   REFERENCES users(id) ON DELETE RESTRICT,
  CONSTRAINT fk_gifticons_receiver FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- -------------------------------------------------------
-- 11. GIFTICON_USAGES (기프티콘 사용 내역)
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
-- 12. STAMPS (사용자 × 매장 스탬프 누적)
-- -------------------------------------------------------
CREATE TABLE stamps (
  id          BIGINT   NOT NULL AUTO_INCREMENT,
  user_id     BIGINT   NOT NULL,
  store_id    BIGINT   NOT NULL,
  count       INT      NOT NULL DEFAULT 0,
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_stamps_user_store (user_id, store_id),
  CONSTRAINT fk_stamps_user  FOREIGN KEY (user_id)  REFERENCES users(id)  ON DELETE CASCADE,
  CONSTRAINT fk_stamps_store FOREIGN KEY (store_id) REFERENCES stores(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- -------------------------------------------------------
-- 13. STAMP_HISTORIES (스탬프 적립 내역)
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
-- 14. NOTIFICATIONS (FCM 알림 내역)
-- -------------------------------------------------------
CREATE TABLE notifications (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  user_id      BIGINT       NOT NULL,
  title        VARCHAR(100) NOT NULL,
  body         VARCHAR(300) NOT NULL,
  type         ENUM('ORDER','STAMP') NOT NULL,
  related_id   BIGINT       NULL  COMMENT 'order_id, stamp_id 등',
  is_read      BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX idx_notif_user_unread (user_id, is_read),
  CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;