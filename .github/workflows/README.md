# GitHub Actions 워크플로우

프로젝트의 CI/CD 파이프라인을 관리합니다.

## 현재 워크플로우

### 1. CI (Continuous Integration)
**파일**: `.github/workflows/ci.yml`

**트리거**:
- `main`, `develop` 브랜치에 push
- Pull Request 생성/업데이트

**작업**:
- **Backend**: 테스트 실행, 커버리지 리포트 생성
- **Frontend**: 테스트 실행, 타입 체크

**특징**:
- 커버리지 목표 미달 시에도 실패하지 않음 (경고만)
- 커버리지 리포트를 아티팩트로 저장 (30일 보관)

---

### 2. Deploy Backend
**파일**: `.github/workflows/deploy-backend.yml`

**트리거**:
- `main` 브랜치에 push
- 수동 실행 (workflow_dispatch)

**작업**:
1. 테스트 실행
2. 커버리지 리포트 생성
3. JAR 빌드
4. EC2 서버에 배포
   - 서비스 중지
   - JAR 업로드
   - 서비스 재시작

**특징**:
- 배포 전 테스트 실행으로 안정성 보장
- 커버리지 리포트는 선택적 (continue-on-error)

---

### 3. Deploy Frontend
**파일**: `.github/workflows/deploy-frontend.yml`

**트리거**:
- `main` 브랜치에 push
- 수동 실행 (workflow_dispatch)

**작업**:
1. 의존성 설치
2. 빌드
3. EC2 서버에 배포
   - 기존 파일 삭제
   - 빌드 결과 업로드
   - 배포 검증

---

## 개선 사항

### ✅ 완료된 개선
1. **배포 전 테스트 실행**: `deploy-backend.yml`에 테스트 단계 추가
2. **CI 워크플로우 추가**: PR 및 브랜치 push 시 자동 테스트
3. **커버리지 리포트**: 자동 생성 및 아티팩트 저장

### 🔄 향후 개선 가능 항목
1. **테스트 실패 시 배포 차단**: 현재는 continue-on-error로 설정
2. **커버리지 뱃지**: README에 커버리지 뱃지 추가
3. **성능 테스트**: 부하 테스트 통합
4. **보안 스캔**: 의존성 취약점 검사

---

## Secrets 설정

다음 GitHub Secrets가 필요합니다:

- `EC2_HOST`: EC2 서버 호스트 주소
- `EC2_SSH_KEY`: EC2 SSH 개인 키

---

## 워크플로우 실행 확인

GitHub 저장소의 **Actions** 탭에서 워크플로우 실행 상태를 확인할 수 있습니다.

---

**최종 업데이트**: 2025-01-09
