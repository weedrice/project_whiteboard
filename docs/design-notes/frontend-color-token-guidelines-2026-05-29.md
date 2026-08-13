# 프론트 색상 토큰 사용 기준

## 목적

프론트 UI 색상은 `frontend/src/styles/foundation.css`의 `--nv-*` CSS 변수와 `nv-*` utility class를 우선 사용한다. 컴포넌트 utility는 `frontend/src/styles/components.css`가 불러오는 스타일 모듈에서 관리하며, `frontend/src/style.css`는 Tailwind 진입점이다. 다크모드에서 일반 표면이 임의의 파랑, 인디고, 그레이 계열로 튀는 회귀를 막고, 마이페이지/관리자/인증/스페이스 화면의 색상 기준을 맞추기 위한 규칙이다.

## 기본 원칙

- 색온도 원칙: 라이트는 웜 페이퍼, 다크는 쿨 잉크를 사용한다.
- 라이트 브랜드 accent는 차분한 잉크 블루 `#2447b8`을 사용하며, 다크 accent는 별도 고대비 값을 유지한다.
- 페이지 배경: `nv-page`
- 카드, 모달, 드롭다운, 패널 표면: `nv-surface`
- 다크에서 한 단계 승격되는 카드·패널 표면: `nv-elevated-surface`
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

원색 상태 토큰 `--nv-danger`, `--nv-warning`, `--nv-success`는 아이콘, 보더, 배경에만 사용한다. 상태 텍스트에는 반드시 대응하는 `--nv-*-text` 토큰을 사용한다. [WCAG 2.2 대비 기준](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html)의 3:1 예외는 일반 24px 이상 또는 굵은 글자 약 18.66px 이상의 대형 텍스트와 아이콘·UI 그래픽에만 적용한다.

브랜드 CTA나 링크에는 `--nv-accent` 또는 기존 accent utility를 사용한다. 일반 표면, hover 배경, unread 배경, 카드 배경에는 직접 `blue`, `indigo`, `sky`, `cyan` 계열 dark background를 쓰지 않는다.

## 토큰 어휘와 레이어

CSS 변수의 주 어휘는 `ink`, `line`, `bg`, `muted`, `ink-soft`, `surface-2`, `accent-bg`다. 다음 기능 계열 변수는 하위 호환 선언으로만 남아 있으며 새 코드에서 사용하지 않는다.

```text
--nv-text        -> --nv-ink
--nv-border      -> --nv-line
--nv-page        -> --nv-bg
--nv-text-subtle -> --nv-muted
--nv-text-muted  -> --nv-ink-soft
--nv-surface-alt -> --nv-surface-2
--nv-accent-soft -> --nv-accent-bg
```

컴포넌트 class인 `nv-text`, `nv-border`, `nv-page`는 호환 가능한 공개 utility이므로 계속 사용할 수 있다.

전역 레이어는 아래 토큰을 사용한다. 컴포넌트 내부의 낮은 z-index만 지역 숫자로 유지한다.

```text
--nv-z-sticky: 60
--nv-z-nav: 70
--nv-z-floating: 80
--nv-z-overlay: 90
--nv-z-popup: 100
--nv-z-toast: 110
```

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
color: var(--nv-danger|warning|success)
text-[var(--nv-danger|warning|success)]
var(--nv-text|border|page|text-subtle|text-muted|surface-alt|accent-soft)
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

다크모드 수동 smoke 기준은 `docs/qa/frontend-dark-mode-smoke-checklist-2026-05-29.md`에 분리해 관리한다.
