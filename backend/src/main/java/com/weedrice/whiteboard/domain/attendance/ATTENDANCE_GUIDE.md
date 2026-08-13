# Attendance 도메인 가이드

`attendance` 도메인은 KST 기준 일일 출석 기록, 연속 출석 계산, 포인트 보상과 출석 뱃지 평가를
담당한다.

## 1. 주요 기능과 로직

- 출석 체크: 활성 사용자를 잠근 뒤 BAN 여부를 검사하고 KST 오늘 날짜의 출석을 기록한다.
- 중복 처리: 같은 날짜의 기록이 이미 있으면 새 기록이나 보상을 만들지 않고
  `alreadyCheckedIn: true`, `earnedPoints: 0`을 반환한다.
- 연속 출석: 가장 최근 기록이 어제이면 이전 `streakCount + 1`, 아니면 `1`부터 다시 시작한다.
- 포인트 보상: 기본 출석 보상과 정확히 7일·30일째의 추가 보상을 지급한다. 기본값은 각각
  `10`, `30`, `100`포인트이며 `POINT_ATTENDANCE_CREATE_REWARD`,
  `POINT_ATTENDANCE_STREAK_7_REWARD`, `POINT_ATTENDANCE_STREAK_30_REWARD` 전역 설정으로
  조정할 수 있다.
- 뱃지 평가: 연속 출석이 7일 이상이면 `ATTENDANCE_7`, 30일 이상이면 `ATTENDANCE_30`
  미보유 뱃지를 평가한다.
- 월별 조회: `month`를 생략하면 KST 현재 월을 사용한다. 해당 월의 출석일을 오름차순으로
  반환하고, 최신 출석이 오늘이나 어제인 경우에만 현재 연속 일수를 유지한다.

## 2. API Endpoints

| Method | URI | 설명 |
| :-- | :-- | :-- |
| `POST` | `/api/v1/attendance/check-in` | 오늘 출석 체크와 포인트·뱃지 보상 처리 |
| `GET` | `/api/v1/attendance/me?month=yyyy-MM` | 지정 월 또는 현재 월의 내 출석 현황 조회 |

## 3. 주요 응답

- 체크인 응답: `attendanceDate`, `streakCount`, `checkedIn`, `alreadyCheckedIn`, `earnedPoints`
- 월별 응답: `month`, `today`, `checkedInToday`, `currentStreakCount`, `days`
- `attendanceDate`와 `today`는 시각이 없는 `LocalDate`이므로 `yyyy-MM-dd` 형식이다.

## 4. 관련 DB 테이블

| 테이블명 | 엔티티 | 설명 |
| :-- | :-- | :-- |
| `user_attendance` | `UserAttendance` | 사용자별 출석 날짜와 해당 날짜의 연속 출석 수 |
| `user_points` | `UserPoint` | 출석 보상이 반영되는 사용자 포인트 잔액 |
| `point_histories` | `PointHistory` | 출석과 연속 출석 보상 이력 |

`user_attendance`는 `(user_id, attendance_date)`를 유일 키로 사용해 사용자별 하루 한 건만
저장한다.
