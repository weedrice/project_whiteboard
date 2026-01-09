# 🚀 프로젝트 개선점 제안

이 문서는 NoviIs 프로젝트의 코드베이스를 분석한 결과를 바탕으로 한 개선점 제안입니다.

---

## 📝 참고사항 (Note)

> **참고:** 보안 관련 항목(민감 정보 하드코딩, 데이터베이스 설정)은 실제 서버에는 반영되어 있지 않으며, 서비스 안정화 이후 데이터베이스 설정 변경이 계획되어 있습니다.

---

## 🟡 중요 (Important) - 코드 품질 및 구조

### 1. **CORS 설정 보안 강화**

**현재 상태:**
```java
configuration.setAllowedHeaders(Collections.singletonList("*")); // 모든 헤더 허용
```

**개선 방안:**
```java
configuration.setAllowedHeaders(Arrays.asList(
    "Authorization", 
    "Content-Type", 
    "X-Requested-With"
));
configuration.setExposedHeaders(Arrays.asList("X-Total-Count"));
```

---

### 2. **Caffeine 캐시 설정 명시화**

**현재 상태:**
- `@EnableCaching`은 활성화되어 있으나 Caffeine 설정이 명시적으로 보이지 않음

**개선 방안:**
```java
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public CaffeineCacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .recordStats());
        return cacheManager;
    }
}
```

또는 `application.yml`에 설정:
```yaml
spring:
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=1000,expireAfterWrite=10m
```

---

### 3. **Validation 에러 응답 개선**

**현재 상태:**
```java
// 첫 번째 에러만 반환
String errorMessage = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
```

**개선 방안:**
```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ApiResponse<Map<String, List<String>>>> handleValidationExceptions(
    MethodArgumentNotValidException e, HttpServletRequest request) {
    
    Map<String, List<String>> errors = new HashMap<>();
    e.getBindingResult().getFieldErrors().forEach(error -> {
        String field = error.getField();
        errors.computeIfAbsent(field, k -> new ArrayList<>()).add(error.getDefaultMessage());
    });
    
    log.warn("[{}] Validation exception: {}", request.getRequestURI(), errors);
    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR.getCode(), "Validation failed", errors));
}
```

---

### 4. **TODO 주석 정리 및 구현**

**발견된 TODO 항목:**
- `ReportService.java`: targetId와 targetType 유효성 검사
- `FeedService.java`: 피드 생성 로직 구현
- `AdService.java`: 광고 선택 로직
- `SesEmailService.java`: ErrorCode.EMAIL_SEND_FAILED 추가
- `MqueueService.java`: 실제 이메일 발송 로직
- `TagService.java`: 인기 태그 조회 로직

**권장 사항:**
- 각 TODO에 우선순위와 마일스톤 할당
- 이슈 트래커에 등록하여 추적

---

## 🟢 권장 (Recommended) - 개발 경험 및 운영

### 5. **Docker 및 Docker Compose 추가**

**현재 상태:**
- Dockerfile 및 docker-compose.yml 없음

**제안 구조:**
```
project_whiteboard/
├── docker/
│   ├── Dockerfile.backend
│   ├── Dockerfile.frontend
│   └── docker-compose.yml
```

**예시 Dockerfile.backend:**
```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY backend/build/libs/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**예시 docker-compose.yml:**
```yaml
version: '3.8'
services:
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: whiteboard
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
  
  backend:
    build:
      context: .
      dockerfile: docker/Dockerfile.backend
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_HOST: postgres
    depends_on:
      - postgres
  
  frontend:
    build:
      context: ./frontend
      dockerfile: ../docker/Dockerfile.frontend
    ports:
      - "5173:80"
```

---

### 6. **CI/CD 파이프라인 구축**

**제안:**
- GitHub Actions 워크플로우 추가

**예시 `.github/workflows/ci.yml`:**
```yaml
name: CI

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main, develop ]

jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '21'
      - name: Run tests
        run: |
          cd backend
          ./gradlew test
      - name: Generate coverage report
        run: |
          cd backend
          ./gradlew jacocoTestReport
  
  frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-node@v3
        with:
          node-version: '20'
      - name: Install dependencies
        run: |
          cd frontend
          npm ci
      - name: Run tests
        run: |
          cd frontend
          npm run test:run
      - name: Type check
        run: |
          cd frontend
          npm run type-check
```

---

### 7. **환경 변수 관리 개선**

**제안:**
- `.env.example` 파일 생성
- 환경별 설정 가이드 문서화

**예시 `.env.example`:**
```env
# Backend
JWT_SECRET=your-secret-key-here
DB_HOST=localhost
DB_NAME=whiteboard
DB_USER=postgres
DB_PASSWORD=your-password
DB_MAX_POOL_SIZE=20
DB_MIN_IDLE=5

# AWS
AWS_ACCESS_KEY=your-access-key
AWS_SECRET_KEY=your-secret-key
AWS_REGION=ap-northeast-2
AWS_S3_BUCKET=noviis-s3

# OAuth
GITHUB_CLIENT_ID=your-client-id
GITHUB_CLIENT_SECRET=your-client-secret

# Frontend
VITE_API_BASE_URL=http://localhost:8080
```

---

### 8. ✅ **로깅 개선** (완료)

**구현 내용:**
- ✅ 민감 정보 자동 마스킹 (비밀번호, 토큰, API 키)
- ✅ MDC를 통한 요청 추적 (requestId, method, uri 등)
- ✅ 에러 로그 분리 (별도 파일로 관리)
- ✅ 환경별 로그 레벨 최적화 (dev/prod)
- ✅ 로그 패턴 개선 (MDC 정보 포함)
- ✅ 로깅 가이드 문서화 (`LOGGING_GUIDE.md`)

**주요 변경사항:**
- `SensitiveDataMaskingFilter`: 민감 정보 마스킹 필터
- `MaskedMessageConverter`: 로그 메시지 마스킹 컨버터
- `LoggingAspect`: MDC 활용 및 파라미터 마스킹
- `logback-spring.xml`: 환경별 로그 설정, 에러 로그 분리
- `GlobalExceptionHandler`: MDC를 통한 에러 로깅 개선

**참고 문서:** `backend/LOGGING_GUIDE.md`

---

### 9. ✅ **API 문서화 개선** (완료)

**구현 내용:**
- ✅ OpenAPI 설정 개선: 서버 정보, 태그, 연락처, 라이선스 정보 추가
- ✅ 공통 응답 어노테이션: `@ApiCommonResponses`로 공통 에러 응답 자동 추가
- ✅ 예시 응답 추가: `@ExampleObject`를 사용한 성공/에러 응답 예시
- ✅ 에러 코드 문서화: `ERROR_CODES.md`에 모든 에러 코드 정리
- ✅ API 버저닝 전략: `API_VERSIONING.md`에 버저닝 정책 문서화
- ✅ API 문서화 가이드: `API_DOCUMENTATION.md`에 사용 방법 정리
- ✅ 예시 컨트롤러: `AuthController`에 OpenAPI 어노테이션 추가 예시

**주요 변경사항:**
- `backend/src/main/java/com/weedrice/whiteboard/global/config/OpenApiConfig.java`: OpenAPI 설정 대폭 개선
- `backend/src/main/java/com/weedrice/whiteboard/global/common/annotation/ApiCommonResponses.java`: 공통 응답 어노테이션 (신규)
- `backend/src/main/java/com/weedrice/whiteboard/domain/auth/controller/AuthController.java`: OpenAPI 어노테이션 추가 예시
- `backend/src/main/resources/application-dev.yml`: SpringDoc 설정 추가

**참고 문서:**
- `backend/API_DOCUMENTATION.md`: API 문서화 가이드
- `backend/ERROR_CODES.md`: 에러 코드 문서
- `backend/API_VERSIONING.md`: API 버저닝 전략

---

### 10. ✅ **프론트엔드 에러 처리 개선** (완료)

**구현 내용:**
- ✅ 네트워크 오류 재시도 로직: `withRetry` 유틸리티, 지수 백오프
- ✅ 오프라인 상태 감지: `useNetworkStatus` composable, `NetworkStatus` 컴포넌트
- ✅ 에러 바운더리: `ErrorBoundary` 컴포넌트로 컴포넌트 레벨 에러 처리
- ✅ TanStack Query 재시도 개선: 스마트 재시도 로직, 네트워크 재연결 시 자동 재요청
- ✅ Axios 인터셉터 개선: 네트워크 오류 구분 및 명확한 메시지
- ✅ 에러 처리 가이드 문서화 (`ERROR_HANDLING.md`)

**주요 변경사항:**
- `frontend/src/utils/retry.ts`: 재시도 유틸리티 함수
- `frontend/src/composables/useNetworkStatus.ts`: 네트워크 상태 모니터링
- `frontend/src/components/common/ErrorBoundary.vue`: 에러 바운더리 컴포넌트
- `frontend/src/components/common/NetworkStatus.vue`: 네트워크 상태 표시
- `frontend/src/main.ts`: TanStack Query 재시도 설정 개선
- `frontend/src/api/index.ts`: 네트워크 오류 처리 개선

**참고 문서:** `frontend/ERROR_HANDLING.md`

---

### 11. **성능 모니터링 및 메트릭**

**제안:**
- Spring Boot Actuator 메트릭 확장
- Prometheus 메트릭 추가
- APM 도구 통합 (예: New Relic, Datadog)

**예시:**
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

---

### 12. **테스트 커버리지 개선**

**현재 상태:**
- JaCoCo 설정은 있으나 커버리지 목표 미설정

**개선 방안:**
```gradle
tasks.named('jacocoTestReport') {
    dependsOn tasks.named('test')
    reports {
        xml.required = true
        html.outputLocation = layout.buildDirectory.dir('reports/jacoco/html')
    }
    finalizedBy jacocoTestCoverageVerification
}

tasks.named('jacocoTestCoverageVerification') {
    violationRules {
        rule {
            limit {
                minimum = 0.70 // 70% 커버리지 목표
            }
        }
    }
}
```

---

### 13. **데이터베이스 인덱스 최적화**

**제안:**
- 자주 조회되는 컬럼에 인덱스 추가 확인
- QueryDSL을 활용한 쿼리 성능 분석
- N+1 쿼리 문제 점검

---

### 14. ✅ **프론트엔드 번들 크기 최적화** (완료)

**구현 내용:**
- ✅ 수동 청크 분할 (manualChunks): vendor-vue, vendor-query, vendor-editor, vendor-icons 등
- ✅ 라우터 동적 import: AdminLayout, AdminDashboard 등 정적 import를 동적 import로 변경
- ✅ 빌드 분석 도구: rollup-plugin-visualizer 추가
- ✅ 빌드 최적화: esbuild minify, CSS 압축, 소스맵 조건부 생성
- ✅ 번들 최적화 가이드 문서화 (`BUNDLE_OPTIMIZATION.md`)

**주요 변경사항:**
- `vite.config.ts`: manualChunks 설정, 빌드 최적화 옵션
- `frontend/src/router/index.ts`: AdminLayout, AdminDashboard 동적 import
- `package.json`: rollup-plugin-visualizer 추가, build:analyze 스크립트

**참고 문서:** `frontend/BUNDLE_OPTIMIZATION.md`

---

### 15. ✅ **환경별 설정 분리** (완료)

**구현 내용:**
- ✅ 환경 변수 검증 로직: 프로덕션 환경에서 필수 환경 변수 자동 검증
- ✅ 환경 변수 가이드: `ENVIRONMENT_VARIABLES.md`에 상세 가이드 작성
- ✅ 환경 변수 예시 파일: `env.example` 파일 생성
- ✅ 프로파일 제어: `SPRING_PROFILES_ACTIVE` 환경 변수로 프로파일 제어

**주요 변경사항:**
- `backend/src/main/java/com/weedrice/whiteboard/global/config/EnvironmentValidator.java`: 환경 변수 검증 로직 (신규)
- `backend/src/main/resources/application.yml`: 프로파일을 환경 변수로 제어
- `backend/ENVIRONMENT_VARIABLES.md`: 환경 변수 가이드 문서 (신규)
- `backend/env.example`: 환경 변수 예시 파일 (신규)

**검증 대상:**
- 데이터베이스 연결 정보 (DB_HOST, DB_NAME, DB_USER, DB_PASSWORD)
- JWT Secret
- OAuth 설정 (GitHub 필수, Google/Discord 선택적)
- AWS 자격 증명 (S3, SES)
- Frontend URL

**참고 문서:** `backend/ENVIRONMENT_VARIABLES.md`

---

## 📋 우선순위별 실행 계획

### Phase 1 (단기 - 1-2주) - 즉시 개선 가능
1. ✅ **Caffeine 캐시 설정 명시화** - 성능 개선
2. ✅ **Validation 에러 응답 개선** - 사용자 경험 개선
3. ✅ **CORS 설정 강화** - 보안 개선
4. ✅ **TODO 주석 정리** - 코드 품질 개선

### Phase 2 (중기 - 1개월) - 개발 환경 개선
5. ⏸️ **Docker 및 Docker Compose 추가** - 나중으로 미룸
6. ✅ **CI/CD 파이프라인 개선** - 배포 전 테스트 추가, CI 워크플로우 추가
7. ℹ️ **환경 변수 관리** - 운영 WAS 내부 properties로 관리 중 (문서화 완료)
8. ✅ **테스트 커버리지 목표 설정** - JaCoCo 커버리지 목표 설정 (50% 이상)

### Phase 3 (장기 - 지속적) - 운영 및 최적화
9. ✅ **로깅 개선** - 운영 모니터링 (완료)
10. ✅ **성능 모니터링 및 메트릭** - 성능 추적
11. ✅ **데이터베이스 인덱스 최적화** - 쿼리 성능
12. ✅ **프론트엔드 번들 크기 최적화** - 로딩 속도 (완료)
13. ✅ **API 문서화 개선** - 개발자 경험
14. ✅ **프론트엔드 에러 처리 개선** - 사용자 경험

---

## 📚 참고 자료

- [Spring Boot Security Best Practices](https://spring.io/guides/topicals/spring-security-architecture)
- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [Vue.js Performance Best Practices](https://vuejs.org/guide/best-practices/performance.html)
- [Docker Best Practices](https://docs.docker.com/develop/dev-best-practices/)

---

**작성일:** 2025-01-09  
**분석 기준:** 프로젝트 전체 코드베이스 리뷰
