# SEO 정적 콘텐츠 정책

## 적용 범위

정적 파일은 빌드 이후 게시글 삭제·비밀글 전환·블라인드·게시판 비공개 변경을 감지하지 못한다. 따라서 `frontend/scripts/prerender-posts.mjs`는 게시글 제목·본문·작성자·작성 시각, 게시판 이름·설명, 목록의 본문 요약을 배포 HTML에 저장하지 않는다. JSON-LD도 작성자와 발행 시각을 포함한 `Article` 대신 공통 `WebPage`를 사용한다. OG PNG에는 사이트 브랜드만 넣는다.

게시글·게시판 URL, canonical, 공통 페이지 안내와 목록 링크는 유지한다. 사이트맵은 빌드 시점의 공개 URL 목록이므로 이후 삭제된 URL이 다음 빌드 전까지 남을 수 있지만, 해당 정적 페이지에는 변경 가능한 콘텐츠가 없다. Vue 화면에서 실제 내용을 읽는 기존 API와 권한 검사는 유지된다.

이 정책은 JavaScript를 실행하지 않는 검색·공유 봇의 게시글별 제목·본문 미리보기를 공통 안내로 바꾼다. 게시글별 미리보기를 다시 제공하려면 요청 시 공개 상태를 검증하는 렌더링 경로와 캐시 무효화 설계가 먼저 필요하다.

## 배포와 확인

현재 배포 경로는 [GitHub Actions 운영 계약](../../.github/workflows/README.md)을 따른다. 새 frontend artifact 전체를 활성화해야 기존 본문 HTML과 게시글 제목 OG 파일도 공개 디렉터리에서 교체된다. 부분 파일 덮어쓰기나 이전 `dist`를 합치는 방식으로 배포하지 않는다. Nginx 설정·백엔드 API·DB 스키마 변경은 필요 없다.

`SEO_STRICT=true`의 API 실패, URL 0건, 산출물 개수, release SHA와 사이트맵 digest 검증은 유지한다. 로컬 Windows에서 Node 24와 설치된 frontend 의존성으로 다음 검증을 실행한다.

```powershell
cd frontend
npm.cmd run test:run -- scripts/__tests__
npm.cmd run type-check
```

`prerender-privacy.spec.mjs`는 공개 콘텐츠를 반환하는 로컬 API로 실제 prerender·manifest·산출물 검증 명령을 실행하고, 생성된 게시글·게시판·전체 게시판 HTML에 원문이 없는지 확인한다. 이 fixture 통과는 운영 배포 확인과 별개다.

운영 적용 후 release SHA와 SEO monitor 결과를 확인하고, 글을 비공개로 바꾼 뒤 해당 URL의 원본 HTTP 응답에도 원문이 없는지 확인한다. 이전 artifact로 rollback하면 기존 노출 방식이 돌아오므로 콘텐츠 정책을 포함한 artifact로 복구해야 한다. 이미 외부 검색·공유 서비스가 저장한 사본은 새 배포만으로 회수되지 않는다.
