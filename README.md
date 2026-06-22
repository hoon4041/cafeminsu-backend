# 카페민수 백엔드

O2O 카페 플랫폼 — 음성 AI 주문 · 스탬프 · 기프티콘

## 기술 스택

- Spring Boot 3.3.5 / Java 17 / Gradle
- MySQL 8 (JPA)
- Spring Security + JWT (jjwt 0.12.x)
- OpenAPI 3 (Swagger UI)

## 빠른 시작

```bash
# 1) 인프라 띄우기 (MySQL)
cp ~/path/to/카페민수_DDL.sql db/init/01_schema.sql   # DDL 자동 실행용
docker compose up -d

# 2) 환경변수 설정
cp .env .env
# .env 값 채우기 (JWT_SECRET은 최소 32바이트)

# 3) 빌드 & 실행
./gradlew bootRun
# 또는 IntelliJ에서 CafeminsuApplication 실행

# 4) 확인
curl http://localhost:8080/health
# Swagger UI
open http://localhost:8080/swagger-ui.html
```

## 디렉터리 구조

```
src/main/java/com/cafeminsu/
├── CafeminsuApplication.java
├── global/
│   ├── common/         # BaseResponse, BaseResponseStatus, BaseEntity, HealthController
│   ├── config/         # Security, Web (CORS·Resolver), Swagger
│   ├── exception/      # BaseException, GlobalExceptionHandler
│   └── security/
│       ├── jwt/        # JwtTokenProvider, JwtAuthenticationFilter, EntryPoint
│       ├── LoginUserId.java
│       └── LoginUserIdArgumentResolver.java
└── domain/             # 도메인별 controller/service/repository/entity/dto
    ├── user/
    ├── store/
    ├── menu/
    ├── order/
    ├── payment/
    ├── gifticon/
    ├── stamp/
    └── notification/
```

## 응답 포맷

모든 응답은 `BaseResponse<T>`로 감싸서 반환합니다.

성공:
```json
{ "isSuccess": true, "code": 1000, "result": { ... } }
```

실패:
```json
{ "isSuccess": false, "code": 2100, "message": "인증이 필요합니다." }
```

코드 영역 (도메인별 4자리):
- `1000` 성공
- `2000~2099` 공통/시스템
- `2100~2199` 인증/인가
- `2200~2299` User
- `2300~2399` Store
- `2400~2499` Menu
- `2500~2599` Order
- `2600~2699` Payment
- `2700~2799` Gifticon
- `2800~2899` Stamp
- `2900~2999` Notification

## 인증

- `Authorization: Bearer <AccessToken>` 헤더로 인증
- Access Token: 1시간 / Refresh Token: 14일
- 로그아웃 시 서버는 Refresh Token만 삭제(재발급 차단). Access Token은 클라이언트가 폐기하며 만료시간이 지나면 자동 무효화됨
- 컨트롤러에서 인증된 사용자 ID 꺼내쓰기:
  ```java
  @GetMapping("/api/user/profile")
  public BaseResponse<UserProfileRes> getProfile(@LoginUserId Long userId) { ... }
  ```

## 다음 단계

도메인 구현 순서 (이전에 설계한 시나리오 A→B→C 기준):
1. User (12) — 카카오 로그인·JWT 발급·프로필
2. Store (7) — 등록·주변 검색
3. Menu (9) — CRUD·옵션
4. Order (10) — 생성·상태 전이
5. Payment (4) — 분할결제 prepare/verify
6. Gifticon (9) — 발행·QR 검증·차감
7. Stamp (2) + Notification (4)
