# 프론트엔드 다크모드 색상 회귀 체크리스트

## 목적

색상 token 공통화 이후 일반 표면, hover, active, unread, dropdown 배경이 다시 파랑/인디고/회색 계열의 임의 Tailwind 색상으로 돌아가는 회귀를 막는다. 자동 색상 검사와 수동 smoke 기준을 함께 관리한다.

## 자동 검사

프론트엔드 코드 변경 후 다음 명령을 실행한다.

```powershell
cd frontend
npm.cmd run check:colors
```

검사 대상은 `frontend/src`의 `.vue`, `.ts`, `.css` 파일이다. 현재 실패 처리하는 패턴은 다음과 같다.

- `dark:hover:bg-indigo-*`, `dark:active:bg-blue-*`, `dark:bg-cyan-*` 같은 다크모드 브랜드 배경
- `bg-white ... dark:bg-gray-*`, `bg-gray-50 ... dark:bg-gray-*` 같은 일반 표면 배경 쌍
- `border-gray-200 ... dark:border-gray-*` 같은 일반 경계 쌍
- `text-gray-900 ... dark:text-*`, `text-gray-500 ... dark:text-gray-*` 같은 일반 텍스트 쌍
- `hover:nv-*`, `sm:nv-*`처럼 Tailwind variant로 잘못 작성한 custom class
- 텍스트에 `color: var(--nv-danger)` 또는 `text-[var(--nv-success)]`처럼 상태 원색을 직접 사용하는 패턴. 텍스트에는 대응하는 `--nv-*-text` token을 사용한다.
- `--nv-text`, `--nv-border`, `--nv-page`, `--nv-text-subtle`, `--nv-text-muted`, `--nv-surface-alt`, `--nv-accent-soft`처럼 호환 선언으로만 남은 deprecated token 사용
- 공통 foundation에 정의되지 않은 `--nv-*` token을 명시적인 `var()` fallback 없이 사용하는 패턴

## 수동 다크모드 Smoke

`npm.cmd run smoke:local`은 HTTP/API 검증과 함께 아래 route를 출력한다. 브라우저에서 다크모드로 전환한 뒤 카드, 모달, 테이블, dropdown, unread row 배경을 확인한다.

- `/mypage`
- `/mypage/messages`
- `/mypage/notifications`
- `/admin/dashboard`
- `/admin/boards`
- `/auth/login`
- `/boards`
- `/emoticons`

## 확인 기준

- 일반 카드, 패널, dropdown, 모달 표면은 `nv-surface` 또는 `nv-surface-muted` 계열로 보인다.
- hover/active 배경은 `nv-hover-surface`, `nv-active-surface`, `nv-press-surface` 계열로 보인다.
- unread, success, warning, danger, info 상태는 `nv-status-*` 또는 의미가 분명한 token만 사용한다.
- 주요 CTA는 브랜드 accent를 유지하되, 일반 표면 배경으로 `dark:bg-blue-*`, `dark:bg-indigo-*`를 쓰지 않는다.
- 오류, toast, checkbox, spinner는 공통 UI token을 우선 사용한다.

## 현재 반영 상태

- PostForm과 게시글 editor toolbar는 `nv-*`, `var(--nv-*)` token 계열을 사용하도록 정리되어 있다.
- 노비콘 등록/수정 화면의 upload affordance와 hover 색상은 `nv-*`, `var(--nv-*)` token 계열을 사용하도록 정리되어 있다.
- 위 항목은 새 TODO가 아니라 회귀 방지 대상이다. 관련 파일을 수정할 때 자동 색상 검사와 수동 smoke 기준으로 유지 여부를 확인한다.

## 남은 후보

- 브라우저 E2E를 도입하는 경우 위 route의 다크모드 screenshot diff를 자동화한다.
- 새 shared component를 추가할 때 `check:colors` 패턴에 누락된 색상 회귀 후보가 없는지 함께 점검한다.
