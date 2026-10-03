# 프론트엔드 정적 자산과 PWA 캐시

## 적용된 계약

운영 `deploy/nginx/noviis.conf`와 Docker `frontend/nginx.conf`는 `/assets/`, `/js/`, `/css/`, `/img/`, `/fonts/`의 없는 파일을 SPA HTML로 대체하지 않는다. 없는 자산은 보안 헤더와 `404`, `Cache-Control: no-store`를 반환한다. 정상 자산의 기존 immutable 캐시 기간, HTML의 재검증 정책, API·OAuth·SSE 프록시는 유지한다.

서비스 워커의 `noviis-static-v1`은 HTTP 200인 비 HTML 자산만 저장·재사용한다. 새 워커가 활성화될 때 기존 캐시의 HTML·오류 응답만 삭제한다. 정상 JS·CSS·이미지·폰트, precache, 다른 캐시는 보존하며 기존 업데이트 대기와 입력 보호 정책을 따른다. Nginx 수정 전에 저장된 HTML은 새 워커 활성화 뒤 제거되고 실제 JS를 다시 받아 복구된다. 복구할 실제 자산이 서버에도 없고 오프라인이면 해당 자산을 새로 만들어 제공할 수는 없다.

## 로컬 검증

Node 24와 설치된 frontend 의존성, Docker 엔진이 필요하다. 저장소 루트의 Windows PowerShell에서:

```powershell
npm.cmd --prefix frontend run test:run -- src/utils/__tests__/staticAssetCache.spec.ts
& 'C:/Program Files/Git/bin/bash.exe' deploy/scripts/test-nginx-config.sh
```

macOS/Linux에서는 `bash deploy/scripts/test-nginx-config.sh`를 실행한다. 이 검증은 임시 파일과 고정 Nginx 이미지에서 정상 자산 5개·없는 자산 5개·HTML/SPA 3개·PWA/release 3개의 HTTP 상태·캐시·보안 헤더를 검사하고 종료 시 정리한다. 실제 운영 호스트에는 접속하지 않는다.

`frontend/e2e/pwa.spec.ts`는 새 frontend 빌드 후 Chromium에서 오염 캐시 정리, 늦게 저장된 HTML 거부, 정상 JS 복구, 기존 정상 자산의 오프라인 재사용을 검사한다. 기존 cold-offline app shell 검증도 유지한다.

## 운영 적용과 복구

[현재 배포 workflow](../../.github/workflows/README.md)는 frontend artifact만 교체하며 Nginx 템플릿을 자동 설치하거나 reload하지 않는다. 운영 변경 권한이 있는 담당자가 현재 호스트 설정을 백업하고 수정된 location을 반영한 뒤 `nginx -t` 성공을 확인하여 reload해야 한다. 별도 작업 없이 저장소 파일만 수정한 상태를 운영 반영 완료로 취급하지 않는다. 이 과정은 API·인증 설정이나 데이터 저장소를 바꾸지 않는다.

적용 후 실제 JS URL이 정상 MIME과 immutable 정책으로 200을 반환하고, 존재하지 않는 `/js/` 및 `/assets/` URL이 HTML app shell 없이 404·no-store를 반환하는지 확인한다. 일반 SPA URL과 release endpoint도 기존 응답을 유지해야 한다. 새 frontend artifact 배포 및 워커 활성화 후 기존 사용자의 오염 캐시가 제거되고 정상 자산은 오프라인에서 읽히는지 확인한다.

Nginx 검사·reload가 실패하면 백업 설정으로 복구하고 기존 프로세스를 유지한다. frontend rollback 시에도 이 정적 캐시 수정을 포함한 artifact를 사용하는 것이 좋다. 이전 artifact는 워커의 오염 캐시 방어를 포함하지 않으며, Nginx 설정만 되돌려 HTML 200 fallback을 복원하면 재오염 조건이 돌아온다.
