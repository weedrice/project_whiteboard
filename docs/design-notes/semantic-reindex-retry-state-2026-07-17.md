# Semantic reindex retry state contract

## 배경

`V69`의 cursor job은 `PENDING`, `PROCESSING`, `COMPLETED` 상태만 지원한다. 페이지 처리 중 예외가 발생하면 claim 트랜잭션도 함께 롤백되어 같은 오래된 job이 즉시 다시 선택되고, 뒤의 정상 job이 계속 밀릴 수 있다.

## 변경

`V71`은 cursor job에 retry count, 다음 실행 시각, 제한된 오류 요약, lease token과 실패 시각을 추가한다. 최대 5회 실패한 job을 격리하기 위해 `FAILED` 상태를 추가하며, 슈퍼관리자만 명시적으로 redrive할 수 있다.

기존 status check constraint는 `FAILED`를 허용하지 않으므로 이를 확장한 constraint로 교체한다. 기존 애플리케이션이 기록하는 세 상태와 기존 행의 의미는 유지되지만 constraint 교체가 포함되므로 migration 정책상 contract 단계로 분류한다.

## 적용과 롤백

- 적용 전 PostgreSQL backup을 생성하고 복구 가능성을 검증한다.
- 새 애플리케이션은 `next_attempt_at`이 도래한 `PENDING` job만 claim한다.
- 처리 실패는 지수 backoff 후 재시도하며 5회째 실패는 `FAILED`로 격리한다.
- rollback window 동안 이전 애플리케이션은 기존 세 상태를 계속 처리할 수 있다. 단, 이전 애플리케이션은 `FAILED`를 처리하지 않으므로 V71 적용은 새 애플리케이션 전환과 승인된 contract 절차를 함께 따른다.
- schema rollback이 필요하면 새 애플리케이션을 중지하고 `FAILED` 행을 운영 검토 후 `PENDING` 또는 `COMPLETED`로 정리한 다음 이전 check constraint를 복원한다.

## 검증

- 반복 실패 job이 backoff되고 최대 횟수에서 격리되는지 확인한다.
- 실패 job 뒤의 정상 job이 같은 scheduler 실행에서 처리되는지 확인한다.
- lease 만료 job이 재시도 횟수를 증가시키며 회수되는지 확인한다.
- redrive가 `FAILED` job만 cursor를 유지한 채 `PENDING`으로 되돌리는지 확인한다.

