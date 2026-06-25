# 프론트 색상 토큰 사용 기준

## 목적

프론트 UI 색상은 `frontend/src/style.css`의 `--nv-*` CSS 변수와 `nv-*` utility class를 우선 사용한다. 다크모드에서 일반 표면이 임의의 파랑, 인디고, 그레이 계열로 튀는 회귀를 막고, 마이페이지/관리자/인증/노드 화면의 색상 기준을 맞추기 위한 규칙이다.

## 기본 원칙

- 페이지 배경: `nv-page`
- 카드, 모달, 드롭다운, 패널 표면: `nv-surface`
- 보조 표면, 스켈레톤, 비활성 배경: `nv-surface-muted`
- hover 표면: `nv-hover-surface`
- active/selected 표면: `nv-active-surface`
- 일반 경계: `border nv-border`
- 강한 경계가 필요한 입력류: `border-[var(--nv-border-strong)]` 또는 기존 `input-base`
- 제목/주요 텍스트: `nv-title` 또는 `nv-text`
- 보조 텍스트: `nv-text-muted`
- 더 약한 설명/메타 텍스트: `nv-text-subtle`
- focus ring: `nv-focus-ring` 또는 `input-base`의 기본 focus 처리

## 상태색

상태 의미가 명확한 경우만 status token을 사용한다.

- 정보/안내/agent badge/tag badge: `nv-status-info`
- 성공/완료: `nv-status-success`
- 경고/주의: `nv-status-warning`
- 오류/위험: `nv-status-danger`

브랜드 CTA나 링크의 accent 색상은 기존 `text-indigo-*`, `bg-indigo-*`를 제한적으로 유지할 수 있다. 다만 일반 표면, hover 배경, unread 배경, 카드 배경에는 직접 `blue`, `indigo`, `sky`, `cyan` 계열 dark background를 쓰지 않는다.

## 비허용 패턴

새 코드에서 다음 패턴은 추가하지 않는다.

```text
bg-white dark:bg-gray-800
bg-gray-50 dark:bg-gray-800
border-gray-200 dark:border-gray-700
text-gray-900 dark:text-white
text-gray-500 dark:text-gray-400
dark:bg-blue-*
dark:bg-indigo-*
dark:bg-sky-*
dark:bg-cyan-*
```

위 패턴이 필요해 보이면 먼저 토큰 class로 바꾼다.

```text
nv-surface
nv-surface-muted
border nv-border
nv-title
nv-text-subtle
nv-status-info
```

## 허용 예외

- 사용자 생성 콘텐츠나 에디터 내부에서 사용자가 직접 선택한 색상
- SVG 브랜드 로고의 고유 색상
- 주요 CTA 버튼의 브랜드 accent
- 명확한 의미를 가진 status token으로 표현하기 어려운 차트/그래프 색상

예외를 추가할 때는 색상이 표면/텍스트/경계 역할인지 먼저 확인한다. 표면/텍스트/경계라면 예외로 두지 말고 토큰을 추가하거나 기존 토큰을 재사용한다.

## 점검 명령

변경 후 아래 검색 결과를 확인한다.

```powershell
npm.cmd run check:colors
rg "dark:bg-(blue|indigo|sky|cyan)" frontend/src -g "*.vue"
rg "bg-white dark:bg|bg-gray-50 dark:bg|border-gray-200 dark:border|text-gray-900 dark:text|text-gray-500 dark:text" frontend/src -g "*.vue"
rg "hover:nv|dark:hover:nv|focus:nv|dark:focus:nv" frontend/src -g "*.vue"
```

세 번째 명령은 Tailwind variant처럼 잘못 작성한 custom class를 잡기 위한 보조 검사다. `hover:nv-surface-muted` 같은 형태는 유효하지 않으므로 `nv-hover-surface`를 사용한다.

다크모드 수동 smoke 기준은 `docs/frontend-dark-mode-smoke-checklist-2026-05-29.md`에 분리해 관리한다.
