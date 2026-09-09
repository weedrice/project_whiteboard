# Password reset token retention contract

## 배경

`V65`에서 `password_reset_tokens.verification_id`가 인증 코드와 연결됐지만, 기존 외래 키는 인증 코드 삭제를 제한한다. 만료 인증 코드 batch에 연결된 토큰이 하나라도 포함되면 전체 삭제가 롤백되어 retention 작업이 같은 오래된 행에서 정체될 수 있다.

## 변경

`V70`은 기존 외래 키를 `ON DELETE SET NULL` 외래 키로 교체한다. 인증 코드가 보존 기간 이후 삭제돼도 이미 발급된 비밀번호 재설정 토큰의 감사·만료 정보는 유지되며, 비밀번호 재설정 토큰 자체는 별도 30일 기본 보존 batch로 정리한다.

외래 키 삭제·재생성이 포함되므로 migration 정책상 contract 단계로 분류한다. HTTP API와 토큰 검증 의미는 바뀌지 않는다.

## 적용과 롤백

- 적용 전 PostgreSQL backup과 외래 키 이름을 확인한다.
- V70 적용 후 애플리케이션은 만료 비밀번호 재설정 토큰을 먼저 정리하고 인증 코드를 정리한다.
- 이전 애플리케이션도 nullable `verification_id`를 허용하므로 읽기 호환성은 유지된다.
- 기존 제한 외래 키로 롤백하려면 `verification_id IS NULL` 토큰이 더 이상 참조 복원을 필요로 하지 않는지 먼저 검토해야 한다.

## 당시 검증 기준

- 연결된 토큰이 남아 있는 만료 인증 코드 삭제가 성공하고 토큰의 `verification_id`가 null이 되는지 확인한다.
- 토큰 retention batch가 cutoff와 batch size를 지키는지 확인한다.
- V70은 contract 승인 없이는 자동 배포되지 않아야 한다.

## 현재 배포 계약 (2026-09-09 소스 확인)

위의 별도 contract 수동 승인 조건은 당시 기록이다. 현재 workflow는 `main`의 통합 CI와 production environment 보호 규칙을 통과하면 contract migration도 자동 배포 대상으로 선택할 수 있다. 별도 contract 승인 입력은 없으며, contract migration을 포함한 새 JAR의 시작 실패 시 이전 JAR로 자동 롤백하지 않는다. 실제 GitHub environment 보호 설정은 저장소 파일만으로 확인할 수 없다. [CI 운영 계약](../../.github/workflows/README.md)과 [마이그레이션 정책](../ops/database-migration-policy.md)을 현재 기준으로 따른다.
