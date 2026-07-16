# Amazon RDS PostgreSQL 백업·복구

스키마 변경은 `docs/ops/database-migration-policy.md`의 expand/backfill/application-switch/contract 절차를 함께 따른다. 별도로 승인된 contract migration 전에는 백업을 생성하고 실제 복구 가능성을 검증한다.

## 운영 정책

- 복구 목표는 RPO 24시간, RTO 2시간이다. RTO는 서비스 중단을 인지한 시점부터 핵심 읽기·쓰기를 재개할 때까지로 측정한다.
- 운영 복구의 기준은 Amazon RDS 자동 백업과 특정 시점 복구(Point-in-Time Recovery, PITR)다.
- 자동 백업 보존 기간은 7일 이상으로 설정하고, 최근 복구 가능 시각이 계속 갱신되는지 확인한다.
- 스키마 변경, 대규모 데이터 보정, 주요 배포 전에는 수동 DB snapshot을 생성하고 `available` 상태를 확인한다.
- 운영 RDS는 스토리지 암호화와 삭제 방지를 활성화하고 Public access를 비활성화한다.
- 분기마다 격리된 RDS 인스턴스로 복구 리허설을 수행한다. 실제 복구하지 않은 백업은 검증된 백업으로 간주하지 않는다.
- S3 versioning 또는 별도 객체 백업 여부를 확인한다. RDS 백업만으로 업로드 객체를 복구할 수는 없다.

RDS 식별자, endpoint, 계정 ID와 자격증명은 이 저장소에 기록하지 않는다. 운영 값은 AWS 콘솔이나 권한이 제한된 secret 저장소에서 관리한다.

## 정기 확인

AWS RDS 콘솔 또는 읽기 전용 API로 다음을 확인한다.

1. DB 상태가 `available`이다.
2. 자동 백업 보존 기간이 7일 이상이다.
3. 최근 복구 가능 시각이 RPO 24시간 이내다.
4. 스토리지 암호화와 삭제 방지가 활성화되어 있다.
5. Public access가 비활성화되어 있다.
6. 할당 스토리지와 자동 확장 한도에 여유가 있다.
7. 백업 시간대와 주간 유지보수 시간대가 겹치지 않는다.
8. VPC, DB subnet group, 보안 그룹, parameter group, instance class, 스토리지·자동 확장, Multi-AZ, port, KMS key와 log export 구성을 권한이 제한된 운영 기록에서 재현할 수 있다.

Multi-AZ 사용 여부는 비용과 가용성 목표를 함께 검토한다. Single-AZ를 유지한다면 분기별 리허설에서 RTO 2시간 충족 여부를 반드시 측정한다.

## 주요 변경 전 수동 snapshot

1. 변경 범위, 현재 애플리케이션 배포 SHA와 RDS 구성 정보를 기록한다.
2. 운영 RDS의 수동 DB snapshot을 생성한다.
3. snapshot 상태가 `available`이 될 때까지 기다린다.
4. snapshot 생성 시각과 식별자, 담당자와 삭제 예정일을 권한이 제한된 운영 기록에 남긴다.
5. 자동 백업의 최근 복구 가능 시각도 확인한 뒤 변경을 시작한다.

수동 snapshot 생성 실패 또는 자동 백업 이상이 있으면 데이터 변경을 포함하는 배포를 진행하지 않는다.

## 복구 방식 선택

- 장애 또는 잘못된 데이터 변경 직전으로 돌아가야 하면 자동 백업의 PITR을 사용한다.
- 주요 변경 직전의 명확한 기준점으로 돌아가야 하면 수동 snapshot을 사용한다.
- 일부 테이블이나 행만 복구해야 하면 별도 RDS로 복구한 뒤 검토된 논리 데이터 이관 절차를 사용한다.

RDS 복구는 기존 인스턴스를 덮어쓰지 않고 새 DB 인스턴스를 생성하는 작업으로 취급한다.

## 격리 복구 절차

1. 장애 시각과 마지막 정상 시각을 UTC로 기록하고, 복구 시점이 RDS의 최근 복구 가능 시각보다 이전인지 확인한다.
2. 기존 운영 RDS를 변경하거나 삭제하지 않은 상태에서 PITR 또는 수동 snapshot으로 새 RDS를 생성한다.
3. 운영과 같은 PostgreSQL major version, VPC, DB subnet group, 보안 그룹, parameter group, instance class, 스토리지·자동 확장, Multi-AZ, port, KMS key와 log export 구성을 사용한다.
4. 삭제 방지를 활성화하고 Public access를 비활성화한다.
5. 보안 그룹은 복구 검증을 수행하는 EC2 또는 격리 검증 환경에서만 접속할 수 있게 제한한다.
6. 새 RDS가 `available` 상태가 되면 DB 크기, 주요 테이블 건수, `flyway_schema_history`, `pg_trgm`, `vector` 확장을 확인한다.
7. 복구 시점과 호환되는 애플리케이션 artifact로 먼저 검증한다. 최신 JAR는 시작할 때 복구 DB에 Flyway migration을 적용할 수 있으므로 검토 없이 실행하지 않는다.
8. `/actuator/health`와 보드·게시글·인증·파일 메타데이터 핵심 읽기 API를 확인한다.
9. 파일 메타데이터가 가리키는 S3 객체가 실제로 존재하는지 표본 검사한다. 누락 객체는 bucket versioning 또는 객체 백업으로 복구하고, 복구할 수 없으면 기능 제한이나 메타데이터 정리를 별도 승인한다.
10. RDS 생성과 애플리케이션 기동 시간은 별도 기록하고, 서비스 중단 인지부터 핵심 기능 재개까지의 전체 시간으로 RTO 2시간 충족 여부를 판단한다.

분기별 리허설은 운영 트래픽과 분리된 환경에서 수행한다. 검증이 끝나면 최종 snapshot 필요성을 검토하고, 삭제 방지를 해제한 뒤 리허설 RDS를 삭제한다.

## 운영 전환과 롤백

운영 전환 전에는 쓰기 요청을 중지해 기존 DB와 복구 DB 사이의 데이터 분기를 막는다. 점검 시간을 공지하고 애플리케이션을 중지한 뒤 활성 쓰기 세션이 남지 않았는지 확인한다.

1. `sudo systemctl stop app`으로 애플리케이션 쓰기를 중단하고 기존 RDS의 활성 쓰기 유입이 없는지 확인한다.
2. 운영 EC2의 보안 그룹에서 새 RDS의 5432 port에 접근할 수 있게 설정하고 EC2에서 연결을 확인한다.
3. `/etc/noviis/app.env`의 `DB_HOST`를 새 RDS endpoint로 변경한다.
4. 환경 파일 권한이 `root:root`, `0600`인지 확인한다.
5. 애플리케이션을 시작하고 health, Flyway 상태와 핵심 읽기 API를 먼저 확인한다.
6. 제한된 계정으로 로그인과 대표 쓰기 요청을 확인한 뒤 일반 운영 쓰기를 재개한다.
7. 기존 RDS는 즉시 삭제하지 않고 롤백 판단 기간 동안 쓰기를 차단한 채 보존한다.

새 RDS 검증에 실패하고 아직 쓰기를 재개하지 않았다면 `DB_HOST`를 기존 endpoint로 되돌리고 애플리케이션을 재시작한다. 새 RDS에서 쓰기가 발생했다면 데이터 분기 여부를 먼저 판단해야 하므로 자동으로 endpoint를 되돌리지 않는다.

배포 workflow의 JAR 롤백은 DB endpoint와 Flyway schema를 되돌리지 않는다. DB 복구 또는 전환 이후에는 workflow의 자동 롤백만으로 복구가 완료됐다고 판단하지 않는다.

## 선택적 논리 백업

`pg_dump` 논리 백업은 RDS 장애 복구의 필수 구성요소가 아니다. 다음 목적이 생길 때 별도 작업으로 도입한다.

- RDS 보존 기간보다 긴 독립 보관
- 다른 계정·리전·공급자로의 이전 가능성 확보
- 일부 스키마나 테이블 단위 복구

도입할 경우 `pg_dump` client major version은 운영 PostgreSQL server와 같거나 호환되는 버전을 사용하고, dump 암호화·SHA-256 검증·외부 보관·실제 복구 리허설을 함께 구현한다.

## 참고

- [Amazon RDS 자동 백업](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_WorkingWithAutomatedBackups.html)
- [Amazon RDS 백업 보존 기간](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_WorkingWithAutomatedBackups.BackupRetention.html)
- [Amazon RDS 특정 시점 복구](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_PIT.html)
