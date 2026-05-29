# 프론트 다크모드 색상 회귀 체크리스트

## 목적

색상 토큰 공통화 이후 일반 표면, hover, active, unread, dropdown 배경이 다시 파랑/인디고 계열로 튀는 회귀를 막는다. HTTP smoke는 라우트와 API 응답을 확인하고, 다크모드 시각 검증은 아래 체크리스트로 관리한다.

## 자동 가드

프론트 코드 변경 후 다음 명령을 실행한다.

```powershell
cd frontend
npm.cmd run check:colors
```

검사 대상은 `frontend/src`의 `.vue`, `.ts`, `.css` 파일이다. 현재 실패 처리하는 패턴은 다음과 같다.

- `dark:hover:bg-indigo-*`, `dark:active:bg-blue-*` 같은 다크모드 브랜드 배경
- `bg-white ... dark:bg-gray-*`, `bg-gray-50 ... dark:bg-gray-*` 표면 배경 쌍
- `border-gray-200 ... dark:border-gray-*` 경계 쌍
- `text-gray-900 ... dark:text-*`, `text-gray-500 ... dark:text-gray-*` 텍스트 쌍
- `hover:nv-*`, `sm:nv-*`처럼 Tailwind variant로 잘못 쓴 custom class

## 수동 다크모드 smoke

`npm.cmd run smoke:local`은 HTTP/API 검증 뒤 아래 라우트를 출력한다. 브라우저에서 다크모드로 전환한 뒤 카드, 모달, 테이블, 탭, 드롭다운, unread row 배경을 확인한다.

- `/mypage`
- `/mypage/messages`
- `/mypage/notifications`
- `/admin/dashboard`
- `/admin/boards`
- `/auth/login`
- `/boards`
- `/emoticons`

## 확인 기준

- 일반 카드/패널/드롭다운은 `nv-surface` 또는 `nv-surface-muted` 톤으로 보인다.
- hover/active 배경은 `nv-hover-surface`, `nv-active-surface`, `nv-press-surface` 계열로 보인다.
- unread, success, warning, danger, info는 `nv-status-*` 또는 의미가 분명한 토큰만 사용한다.
- 주요 CTA와 링크 accent는 유지하되, 일반 표면 배경으로 `dark:bg-blue-*`, `dark:bg-indigo-*`를 쓰지 않는다.
- 폼 오류, toast, checkbox, spinner는 공통 UI 토큰을 우선 사용한다.

## 다음 후보

- PostForm과 에디터 toolbar의 버튼 상태색을 토큰화한다.
- 에모티콘 등록/수정 화면의 업로드 슬롯 hover 색상을 `nv-hover-surface` 기준으로 정리한다.
- 브라우저 E2E를 도입하는 경우 위 라우트의 다크모드 screenshot diff를 자동화한다.
