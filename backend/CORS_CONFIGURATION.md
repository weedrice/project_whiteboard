# CORS 설정 가이드

## 개요

백엔드의 CORS(Cross-Origin Resource Sharing) 설정이 보안을 강화하면서도 프론트엔드와의 호환성을 유지하도록 구성되어 있습니다.

## 현재 설정

### 허용된 Origin
- 개발 환경: `http://localhost:5173` (Vite 기본 포트)
- 프로덕션 환경: `https://noviis.kr` (또는 설정된 `app.frontend-url`)

### 허용된 HTTP 메서드
- `GET`: 데이터 조회
- `POST`: 데이터 생성
- `PUT`: 데이터 수정
- `DELETE`: 데이터 삭제
- `PATCH`: 부분 수정
- `OPTIONS`: Preflight 요청

### 허용된 요청 헤더
- `Authorization`: JWT 토큰 인증 (Bearer 토큰)
- `Content-Type`: 요청 본문 타입
  - `application/json`: 일반 API 요청
  - `multipart/form-data`: 파일 업로드
- `Accept`: 응답 형식 지정 (선택적)

### 노출된 응답 헤더
- `Content-Type`: 응답 본문 타입
- `Content-Length`: 응답 본문 크기

### 기타 설정
- `AllowCredentials: true`: 인증 정보(쿠키, Authorization 헤더) 허용
- `MaxAge: 3600`: Preflight 요청 캐시 시간 (1시간)

## 프론트엔드 호환성

현재 프론트엔드에서 사용하는 모든 헤더와 메서드가 허용되어 있습니다:

### ✅ 지원되는 요청
- 일반 API 요청 (`Content-Type: application/json`)
- 파일 업로드 (`Content-Type: multipart/form-data`)
- JWT 인증 요청 (`Authorization: Bearer <token>`)
- 모든 CRUD 작업 (GET, POST, PUT, DELETE, PATCH)

## 커스텀 헤더 추가 방법

프론트엔드에서 새로운 커스텀 헤더를 사용해야 하는 경우:

### 1. 백엔드 설정 수정

`SecurityConfig.java`의 `corsConfigurationSource()` 메서드에서 허용 헤더 목록에 추가:

```java
configuration.setAllowedHeaders(Arrays.asList(
    "Authorization",
    "Content-Type",
    "Accept",
    "X-Custom-Header"  // 새 헤더 추가
));
```

### 2. 응답 헤더 노출 (필요한 경우)

클라이언트가 읽어야 하는 커스텀 응답 헤더가 있다면:

```java
configuration.setExposedHeaders(Arrays.asList(
    "Content-Type",
    "Content-Length",
    "X-Total-Count"  // 예: 페이지네이션 총 개수
));
```

### 3. 프론트엔드에서 사용

```typescript
// Axios 요청에 헤더 추가
api.get('/some-endpoint', {
  headers: {
    'X-Custom-Header': 'value'
  }
})
```

## 환경별 설정

### 개발 환경
- Origin: `http://localhost:5173`
- 설정 파일: `application-dev.yml`

### 프로덕션 환경
- Origin: 환경 변수 `FRONTEND_URL` 또는 `app.frontend-url`
- 설정 파일: `application-prod.yml`

## 보안 고려사항

### ✅ 현재 적용된 보안 조치
1. **명시적 헤더 허용**: 모든 헤더(`*`) 대신 필요한 헤더만 허용
2. **Origin 제한**: 특정 프론트엔드 URL만 허용
3. **Preflight 캐시**: 불필요한 OPTIONS 요청 감소

### ⚠️ 주의사항
- **Origin 설정**: 프로덕션 환경에서 올바른 프론트엔드 URL이 설정되어 있는지 확인
- **새 헤더 추가**: 보안 검토 후 필요한 헤더만 추가
- **다중 Origin**: 여러 프론트엔드 도메인을 지원해야 하는 경우, 환경 변수로 관리

## 문제 해결

### CORS 에러 발생 시

1. **브라우저 콘솔 확인**
   ```
   Access to XMLHttpRequest at '...' from origin '...' has been blocked by CORS policy
   ```

2. **확인 사항**
   - 프론트엔드 URL이 `app.frontend-url`과 일치하는가?
   - 사용하는 헤더가 허용 목록에 있는가?
   - HTTP 메서드가 허용 목록에 있는가?

3. **디버깅**
   - 브라우저 개발자 도구의 Network 탭에서 OPTIONS 요청 확인
   - 응답 헤더에서 `Access-Control-Allow-*` 확인

### 일반적인 문제

#### 문제: "Authorization 헤더가 전송되지 않음"
**해결**: 프론트엔드에서 `Authorization` 헤더가 올바르게 설정되어 있는지 확인

#### 문제: "multipart/form-data 요청 실패"
**해결**: `Content-Type` 헤더가 허용 목록에 포함되어 있는지 확인 (이미 포함됨)

#### 문제: "Preflight 요청 실패"
**해결**: OPTIONS 메서드가 허용 목록에 포함되어 있는지 확인 (이미 포함됨)

## 테스트

### 수동 테스트
```bash
# Preflight 요청 테스트
curl -X OPTIONS http://localhost:8080/api/v1/users/me \
  -H "Origin: http://localhost:5173" \
  -H "Access-Control-Request-Method: GET" \
  -H "Access-Control-Request-Headers: Authorization" \
  -v
```

### 예상 응답 헤더
```
Access-Control-Allow-Origin: http://localhost:5173
Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS, PATCH
Access-Control-Allow-Headers: Authorization, Content-Type, Accept
Access-Control-Allow-Credentials: true
Access-Control-Max-Age: 3600
```

## 관련 파일

- `backend/src/main/java/com/weedrice/whiteboard/global/config/SecurityConfig.java`
- `backend/src/main/resources/application-dev.yml`
- `backend/src/main/resources/application-prod.yml`
- `frontend/src/api/index.ts`
