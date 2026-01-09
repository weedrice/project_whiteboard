# CORS 호환성 가이드

백엔드 CORS 설정 강화에 따른 프론트엔드 영향도 및 호환성 확인 가이드입니다.

## ✅ 호환성 확인

현재 프론트엔드에서 사용하는 모든 헤더와 메서드가 백엔드에서 허용되어 있습니다.

### 허용된 헤더 (프론트엔드에서 사용 중)

| 헤더 | 사용 위치 | 용도 |
|------|----------|------|
| `Authorization` | `api/index.ts` | JWT 토큰 인증 |
| `Content-Type: application/json` | `api/index.ts` | 일반 API 요청 |
| `Content-Type: multipart/form-data` | `api/file.ts` | 파일 업로드 |

### 허용된 HTTP 메서드 (프론트엔드에서 사용 중)

- ✅ `GET`: 모든 조회 API
- ✅ `POST`: 생성 API (게시글, 댓글, 파일 업로드 등)
- ✅ `PUT`: 수정 API (프로필, 게시글 등)
- ✅ `DELETE`: 삭제 API
- ✅ `PATCH`: 부분 수정 (현재 사용하지 않지만 허용됨)

## 변경 사항 요약

### Before (이전)
```java
// 모든 헤더 허용 (보안 위험)
configuration.setAllowedHeaders(Collections.singletonList("*"));
```

### After (개선)
```java
// 필요한 헤더만 명시적으로 허용
configuration.setAllowedHeaders(Arrays.asList(
    "Authorization",
    "Content-Type",
    "Accept"
));
```

## 영향도 분석

### ✅ 영향 없음 (정상 동작)

1. **일반 API 요청**
   ```typescript
   // ✅ 정상 동작
   api.get('/users/me')
   api.post('/boards', data)
   ```

2. **인증 요청**
   ```typescript
   // ✅ Authorization 헤더 정상 전송
   api.get('/users/me') // 자동으로 Authorization 헤더 추가
   ```

3. **파일 업로드**
   ```typescript
   // ✅ multipart/form-data 정상 동작
   api.post('/files/upload', formData, {
     headers: { 'Content-Type': 'multipart/form-data' }
   })
   ```

### ⚠️ 주의사항

#### 새로운 커스텀 헤더 추가 시

프론트엔드에서 새로운 커스텀 헤더를 사용하려면 백엔드 설정에 추가해야 합니다:

1. **백엔드에 헤더 추가 요청**
   - `SecurityConfig.java`의 `setAllowedHeaders`에 새 헤더 추가 필요

2. **예시: X-Custom-Header 추가**
   ```typescript
   // 프론트엔드에서 사용하려면
   api.get('/endpoint', {
     headers: {
       'X-Custom-Header': 'value'  // 백엔드에 먼저 추가 필요
     }
   })
   ```

## 테스트 체크리스트

다음 항목들이 정상 동작하는지 확인하세요:

- [ ] 로그인/회원가입
- [ ] JWT 토큰을 사용한 인증 요청
- [ ] 파일 업로드
- [ ] 모든 CRUD 작업 (생성, 조회, 수정, 삭제)
- [ ] OAuth 로그인 (GitHub 등)

## 문제 발생 시

### CORS 에러 메시지 예시
```
Access to XMLHttpRequest at 'http://localhost:8080/api/v1/...' 
from origin 'http://localhost:5173' has been blocked by CORS policy: 
Request header field X-Custom-Header is not allowed by Access-Control-Allow-Headers in preflight response.
```

### 해결 방법

1. **브라우저 콘솔 확인**
   - Network 탭에서 실패한 요청 확인
   - OPTIONS 요청(Preflight)의 응답 헤더 확인

2. **헤더 확인**
   - 사용 중인 헤더가 허용 목록에 있는지 확인
   - 새로운 헤더를 사용하는 경우 백엔드에 추가 요청

3. **Origin 확인**
   - 프론트엔드 URL이 백엔드 설정과 일치하는지 확인
   - 개발 환경: `http://localhost:5173`
   - 프로덕션: 설정된 `app.frontend-url`

## 개발 가이드

### 새로운 헤더가 필요한 경우

1. **백엔드 팀과 협의**
   - 보안 검토 후 필요한 헤더만 추가
   - `backend/CORS_CONFIGURATION.md` 참고

2. **임시 해결책 (개발 환경만)**
   - 개발 환경에서는 백엔드 설정을 임시로 완화할 수 있지만, 프로덕션에서는 권장하지 않음

### Best Practices

1. **표준 헤더 사용**
   - 가능하면 표준 HTTP 헤더 사용
   - 커스텀 헤더는 최소화

2. **헤더 통일**
   - 프로젝트 전반에서 일관된 헤더 사용
   - 문서화 유지

## 관련 문서

- `backend/CORS_CONFIGURATION.md`: 백엔드 CORS 설정 상세 가이드
- `backend/src/main/java/com/weedrice/whiteboard/global/config/SecurityConfig.java`: 실제 설정 코드
