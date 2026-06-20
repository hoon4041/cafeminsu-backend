# 메뉴 이미지 업로드 설계

- **날짜:** 2026-06-20
- **대상:** cafeminsu 백엔드 (Spring Boot)
- **상태:** 승인됨 (설계 확정, 구현 계획 대기)

## 1. 배경 / 문제

현재 메뉴 등록은 `POST /api/stores/{storeId}/menus` 로 JSON을 받으며, `MenuCreateReq.imageUrl`
은 **문자열**이다. 점주는 서버 `static/imgs/menu/` 에 미리 배치된 svg 파일명을 가리킬 수만 있고,
자신의 이미지를 직접 올릴 수 없다.

목표: 점주가 메뉴를 등록/수정할 때 **이미지 파일을 직접 업로드**하고, 메뉴 삭제 시 해당 업로드
이미지 파일도 함께 정리되도록 한다. (안드로이드 앱 측 작업은 이번 범위에서 제외 — 백엔드만.)

## 2. 핵심 확인 사항 (조사 결과)

- 백엔드에 기존 파일 업로드/멀티파트 코드 없음. 멀티파트 설정도 없음.
- 메뉴 이미지는 Spring 기본 정적 핸들러로 classpath `static/imgs/menu/*.svg` 에서 `/imgs/menu/{파일명}`
  으로 서빙됨.
- `OrderItem` 은 `menuId` + `unitPrice`(스냅샷)만 보존하고 **메뉴 이미지를 스냅샷하지 않음**.
- 메뉴는 soft delete (`@SQLDelete` → `deleted_at`) + `@SQLRestriction("deleted_at IS NULL")`.
  삭제된 메뉴는 일반 조회에서 빠지므로, 업로드 이미지 파일을 물리 삭제해도 기존 주문 화면이 깨지지 않음.
- `SecurityConfig`: `/imgs/**` 는 public(비로그인 서빙 허용). 메뉴 POST/PATCH/DELETE 는 `hasRole("OWNER")`.
  서비스 레이어에서 menu→store→owner 체인 검증을 한 번 더 수행.

## 3. API 방식 결정

선택: **업로드 전용 엔드포인트 분리 (B)** + 등록/수정/삭제 라이프사이클 연계.

이유: 등록·수정 양쪽에서 재사용 가능하고, 메뉴 등록 JSON 계약을 그대로 유지한다. orphan 파일
가능성은 수정·삭제 시 정리 로직으로 보완한다.

## 4. 상세 설계

### 4.1 업로드 엔드포인트 (신규)

- `POST /api/images/menu`
- Content-Type: `multipart/form-data`, 파트명: `file`
- 권한: `OWNER`
- 응답: `BaseResponse<ImageUploadRes>` — `ImageUploadRes(String imageUrl)`
- `imageUrl` 형식: `/imgs/menu/uploads/{uuid}.{ext}` (서버 상대 경로)
- 검증:
  - 빈 파일 → 에러
  - 허용 확장자: `jpg`, `jpeg`, `png`, `webp` (그 외 거부) — 실제 content-type 도 함께 확인
  - 최대 용량 초과 → 에러
- DTO: `ImageUploadRes` (record, `imageUrl` 필드)
- 컨트롤러: `ImageController` 신설 (`@RequestParam("file") MultipartFile file`)

### 4.2 파일 저장 / 서빙

- `application.yml` 에 설정 추가:
  - `file.upload-dir: ./uploads` — 외부 디렉토리. classpath(jar 내부)는 런타임 쓰기 불가하므로 외부 사용. (환경변수 `FILE_UPLOAD_DIR` 로 override 가능하게 `${FILE_UPLOAD_DIR:./uploads}`)
  - `spring.servlet.multipart.max-file-size: 5MB`, `spring.servlet.multipart.max-request-size: 10MB`
  - 4.1 의 "최대 용량" 검증 기준값도 5MB 로 통일.
- 저장 위치: `${file.upload-dir}/menu/{uuid}.{ext}`
- `WebConfig` 에 ResourceHandler 추가:
  - `/imgs/menu/uploads/**` → `file:${file.upload-dir}/menu/`
  - 번들 svg 는 기존 classpath 정적 서빙 그대로 유지 (`/imgs/menu/{파일명}.svg`)
- 업로드 파일과 번들 기본 에셋은 **URL 접두로 구분**: 업로드 파일만 `/imgs/menu/uploads/` 접두를 가진다.

### 4.3 메뉴 등록 (변경 최소)

- `POST /api/stores/{storeId}/menus` JSON 계약 **변경 없음**.
- 클라이언트 흐름: ① 업로드 엔드포인트로 파일 전송 → `imageUrl` 수신 → ② 등록 요청 `imageUrl` 에 그대로 전달.

### 4.4 메뉴 수정 시 이미지 교체 + 기존 파일 정리

- `MenuService.updateMenu` 에서 `req.imageUrl()` 이 기존 값과 다르고,
  **기존 값이 업로드 파일(`/imgs/menu/uploads/` 접두)** 이면 이전 물리 파일 삭제.
- 번들 svg(접두 불일치)는 절대 삭제하지 않음.

### 4.5 메뉴 삭제 시 이미지 삭제

- `MenuService.deleteMenu`(soft delete) 에서 해당 메뉴 `imageUrl` 이 업로드 파일이면 물리 파일 삭제.
- 번들 svg 면 건드리지 않음.

### 4.6 공통 컴포넌트

- `FileStorageService` (신규):
  - `String store(MultipartFile file)` → 저장 후 `imageUrl`(`/imgs/menu/uploads/{uuid}.{ext}`) 반환
  - `void delete(String imageUrl)` → `/imgs/menu/uploads/` 접두인 경우에만 물리 삭제, 그 외 no-op
  - 경로 traversal 방지(파일명은 서버 생성 UUID만 사용, 사용자 입력 파일명 미사용), 디렉토리 자동 생성
- `BaseResponseStatus` 에 업로드 관련 에러 코드 추가:
  - 빈 파일 / 허용되지 않은 확장자 / 용량 초과 / 저장(IO) 실패
- `SecurityConfig`: `POST /api/images/**` → `hasRole("OWNER")` 추가 (기존 `/imgs/**` public 서빙과 경로가 달라 충돌 없음).

## 5. 영향 범위 (수정/신규 파일)

신규:
- `domain/image/controller/ImageController.java`
- `domain/image/dto/ImageUploadRes.java`
- `global/storage/FileStorageService.java` (또는 `domain/image/service/`)

수정:
- `global/config/WebConfig.java` — ResourceHandler 추가
- `global/config/SecurityConfig.java` — 업로드 엔드포인트 권한
- `global/common/BaseResponseStatus.java` — 에러 코드 추가
- `domain/menu/service/MenuService.java` — update/delete 시 파일 정리
- `src/main/resources/application.yml` — `file.upload-dir`, multipart 설정

## 6. 비범위 (Out of Scope)

- 안드로이드(앱) 클라이언트 UI/연동 코드.
- 번들 기본 svg 에셋 마이그레이션/삭제.
- 이미지 리사이징/썸네일/CDN.
- 등록(POST) 엔드포인트를 멀티파트로 바꾸는 것 (JSON 유지).

## 7. 테스트 관점

- 업로드: 정상(허용 확장자) / 빈 파일 / 비허용 확장자 / 용량 초과 / 비OWNER 권한 거부.
- 서빙: 업로드된 파일이 `/imgs/menu/uploads/{name}` 로 GET 가능.
- 수정: imageUrl 교체 시 이전 업로드 파일 삭제, 번들 svg 였으면 미삭제.
- 삭제: 메뉴 삭제 시 업로드 파일 삭제, 번들 svg 였으면 미삭제.
- 회귀: 기존 메뉴 등록/조회 JSON 계약 정상.
