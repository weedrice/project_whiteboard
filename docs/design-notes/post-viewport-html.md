# 게시글 전체 화면 HTML 장식

상태: `feat/post-viewport-html` 구현 브랜치. 운영 배포 여부와 최종 검증 결과는 변경 PR/작업 보고를 기준으로 한다.

## 작성 계약

기존 `contents` HTML과 원본 HTML 보존 블록을 사용한다. API 필드, 엔드포인트, DB 컬럼은 추가하지 않는다. HTML 모드에서 다음 영역을 명시하면 상세 화면의 재생 버튼이 장식을 표시한다. 일반 본문의 HTML은 기존 정제·iframe 경로를 유지한다.

```html
<p>본문</p>
<template data-noviis-layer="viewport">
  <style>
    .sparkle {
      position: fixed;
      top: 15vh;
      left: 70vw;
      font-size: 3rem;
      animation: float 3s ease-in-out infinite alternate;
    }
    @keyframes float {
      to { transform: translateY(30vh) rotate(90deg); }
    }
  </style>
  <span class="sparkle">✦</span>
</template>
```

작성기는 HTML 모드의 접을 수 있는 도움말에서 예시를 복사할 수 있다. 도움말을 여는 것만으로 본문에 삽입하거나 효과를 실행하지 않는다. 일반 편집 모드에서는 기존 RawHtml 보존 경로를 재사용한다. 장식 선언만 있는 블록은 빈 iframe 대신 간단한 안내를 표시하고, 상세 본문과 게시글 미리보기에도 불필요한 빈 영역을 만들지 않는다. 이 처리는 표시용 복사본에만 적용하며 저장한 원본은 변경하지 않는다.

게시글 미리보기와 상세 화면은 재생 전에 장식 영역 중복, HTML 길이·요소 수, CSS 리소스, 이미지 개수·주소를 검사한다. 문제가 있으면 원인과 수정 안내를 표시하고 효과 재생 버튼을 비활성화한다. 이미지를 내려받은 뒤 확인할 수 있는 MIME, 응답 크기, 반복 이미지 데이터 상한도 각각 구분해 안내하며, 네트워크 실패는 별도로 표시한다. 이 검사는 효과 재생만 제한하고 게시글 저장을 차단하지 않는다.

업로드 이미지는 일반 편집기에서 먼저 올린 다음 `<img src="/api/v1/files/123" data-file-id="123">`와 같은 태그를 장식 영역으로 옮긴다. 서비스 내부 원본 파일 경로만 지원하며 이미지의 파일 ID와 URL이 일치해야 한다. 원본 다운로드를 사용해 GIF/WebP 프레임을 보존한다. CSS 리소스를 참조하는 `url()`, `src()`, `image()`, `image-set()` 계열과 `@import`는 허용하지 않으며 이스케이프된 토큰도 검사한다. CSS 주석·문자열·이스케이프를 구분하므로 `content: "url(...)"`처럼 인용된 일반 텍스트는 허용한다. 이미지 배치는 `<img>`와 CSS를 사용한다. 외부 이미지, 외부 스타일시트와 폰트는 첫 버전에서 지원하지 않는다. 프레임 재생 여부는 브라우저 회귀 테스트로 검증한다.

## 표시와 격리

- 상세 화면에서 방문자가 직접 재생한다. 작성 미리보기에서는 제한된 크기의 영역으로 재생한다.
- 전체 화면 레이어는 호스트가 배치하는 별도 `sandbox=""` iframe이다. 작성자 HTML을 사이트 DOM에 삽입하지 않는다.
- iframe 안의 일반 장식 HTML, 인라인 CSS, `style`, CSS 애니메이션을 지원한다. 스크립트·폼·입력·링크 동작·동영상·오디오·SVG·중첩 프레임은 제공하지 않는다.
- CSP는 스크립트·통신·외부 스타일·폰트·중첩 프레임을 차단한다. 부모는 기존 인증 API로 원본 래스터 파일을 받은 뒤 base64 data URL로 변환해 HTML 작성기와 별도의 이미지 매핑으로 전달한다. 작성자가 지정한 `src`를 그대로 실행하지 않으며 CSP 이미지 출처는 `data:`로 제한한다. Chromium 검증에서 origin이 격리된 iframe의 부모 blob URL 접근이 거부되어 이 방식을 사용한다.
- 부모가 iframe의 위치·크기·opacity 0.7·입력 차단·겹침 순서를 결정한다. 작성자 CSS는 부모의 설정과 사이트의 메뉴·모달·효과 종료 버튼을 변경할 수 없다.
- 입력 차단에는 `pointer-events: none`, `inert`, `tabindex="-1"`, `aria-hidden="true"`를 함께 적용한다. 클릭과 Tab 이동은 실제 브라우저에서 확인한다.
- 재생 요청 후 최대 30초에 종료하며, 종료 버튼·Escape·탭 숨김·모션 감소 설정 변경·본문/게시글 변경·인증 세션 변경·컴포넌트 해제로 종료한다. 대기 중 요청을 취소하고 `srcdoc`을 비워 포함된 이미지 데이터를 해제한다. 전체 화면 장식용 object URL은 생성하지 않는다.
- 모션 감소 설정이면 시작하지 않는다. GIF/WebP는 CSS 애니메이션 중지로 정지하지 않으므로 레이어 자체를 제거한다.
- 스포일러 공개 전, 블라인드 콘텐츠, 읽기 권한이 없는 상태에서는 장식을 시작하지 않는다.

전체 화면 표현을 허용하면 시각적 가림·가짜 안내 표현·과도한 CSS/GPU 비용의 위험은 남는다. DOM·스크립트 격리를 유지하는 것이며, 이전의 본문 영역 한정 표시와 모든 위험이 동일하다는 의미는 아니다.

## 자원과 수명주기

- 게시글당 명시적 전체 화면 template 하나만 허용한다.
- 작성한 장식 HTML은 JavaScript 문자열 길이 65,536 이하, 요소는 300개 이하, 이미지 태그는 8개 이하로 제한한다. 검증 후 주입하는 이미지 data URL은 작성 HTML 길이 상한에 포함하지 않는다.
- 중복 이미지 파일은 한 번만 다운로드한다. 내려받은 이미지 응답의 합계가 8 MiB를 넘으면 표시하지 않는다. 응답 이후 검사이므로 네트워크 다운로드량의 사전 상한을 보장하지 않는다. base64 인코딩 후 메모리 사용량은 원본 바이트보다 커질 수 있다.
- 최종 문서에 삽입하는 이미지 data URL의 문자열 길이 합계는 12 × 1,024 × 1,024자 이하로 제한한다. 같은 이미지를 여러 img 요소에 넣으면 각각 계산하여 중복 직렬화로 메모리가 증가하는 경우도 제한한다.
- 이미지 MIME은 PNG/JPEG/WebP/GIF로 제한한다. 인증 세션·본문 변경 뒤 도착한 응답으로 효과를 되살리지 않는다.
- 장식 영역의 지원 이미지도 기존 게시글·초안·예약의 파일 연결 검사를 거친다. 원본 HTML 보존 블록 안의 명시적 장식 영역까지 검사하며 일반·중첩 template, 제거되는 form/SVG 하위 요소, 장식 영역의 첨부 링크는 대상에 포함하지 않는다. 사용하지 않는 참조 제거와 편집용 blob URL의 서버 경로 복원에도 같은 순회를 사용한다.
- 장식의 텍스트·이미지는 본문 요약과 대표 이미지에서 제외한다. 본문에서 같은 이미지가 쓰이면 본문 참조는 유지한다.
- 의미 검색에 전달하는 일반 텍스트에서도 장식을 제외한다. 단, 기존 SQL `noviis_expand_preserved_post_html`을 사용하는 키워드 검색은 장식 텍스트와 일치할 수 있다. 이번 변경에는 검색 함수·인덱스·DB 마이그레이션 변경이 없으며, 키워드 검색까지 제외하는 작업은 별도 후속 범위다.

## 구현 경로

- `frontend/src/components/board/PostViewportHtmlHelp.vue`: HTML 작성 도움말과 예시 복사.
- `frontend/src/components/board/PostViewportEffect.vue`: 재생·종료·세션 및 모션 제어, 상세/미리보기 배치.
- `frontend/src/utils/postViewportEffect.ts`: template 추출과 이미지 원본 수화.
- `frontend/src/utils/postViewportHtml.ts`: HTML 정제와 전용 CSP 문서 생성.
- `frontend/src/utils/postHtmlDisplay.ts`: 저장 원본을 유지하면서 표시용 본문에서 장식 선언과 빈 장식 블록 제거.
- `frontend/src/utils/postForm.ts`, `frontend/src/utils/fileUrl.ts`: 내부 template 파일 참조와 편집 URL 처리.
- `backend/src/main/java/com/weedrice/whiteboard/domain/post/support/PostContentCodec.java`: 보존 HTML 및 검색용 텍스트 처리.
- `backend/src/main/java/com/weedrice/whiteboard/domain/post/service/PostContentSummaryExtractor.java`: 장식 제외 요약/대표 이미지.

## 검증과 되돌리기

확인 대상은 작성→일반/HTML 모드 전환→임시저장→발행→재편집의 원본 유지, 파일 연결/초안 정리, CSS 애니메이션과 GIF/WebP, 클릭·Tab 통과, 메뉴·모달 우선순위, 스포일러·세션 변경·본문 변경·늦은 응답·모션 감소·종료 타이머, 외부 URL·스크립트·meta refresh·중첩 문서 차단이다. 테스트 실행 여부와 수치는 최종 작업 보고에 기록한다.

기존 글은 명시적 template가 없으면 현재 동작을 유지한다. 기능을 되돌릴 때 전체 화면 렌더러만 제거하면 template는 본문에 보이지 않는 데이터로 남는다. 이미 저장한 원본과 파일 참조를 손실시키지 않도록 보존·파일 연결 처리는 유지한다. 스키마 롤백은 필요하지 않다.
