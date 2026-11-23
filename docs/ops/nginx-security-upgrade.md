# 운영 Nginx 보안 업데이트

## 2026-11-19 적용 상태

운영 Ubuntu 22.04의 native Nginx를 `1.18.0-6ubuntu14.22`에서 공식 nginx.org stable 패키지 `1.30.7-1~jammy`로 교체했다. 마스터와 모든 워커의 실행 파일 inode가 설치된 새 바이너리와 일치하며, `nginx -v`에서 1.30.7을 확인했다. 프론트엔드 artifact 배포와 별도로 수행한 호스트 변경이다.

[공식 보안 공지](https://nginx.org/en/security_advisories.html)와 [1.30.7 변경 내역](https://nginx.org/en/CHANGES-1.30)은 TLS 요청 본문 처리, HTTP/2, resolver 등의 수정 버전을 명시한다. 적용 전 운영 HTTP/2는 비활성이었지만 TLS 1.3은 사용 중이었다. [Ubuntu CVE-2026-118899 상태](https://ubuntu.com/security/CVE-2026-118899)도 당시 jammy를 취약 상태로 표시했다.

## 패키지와 설정 계약

- 공식 저장소는 `https://nginx.org/packages/ubuntu`, 배포판은 `jammy`다. `/etc/apt/sources.list.d/noviis-nginx-stable.list`와 별도 `signed-by` 키링을 사용한다. 공식 문서의 키 fingerprint를 확인하고 서명된 패키지 메타데이터의 SHA-256으로 설치 파일을 검증했다.
- `/etc/apt/preferences.d/noviis-nginx-stable`은 nginx 및 nginx-module 패키지만 공식 저장소 우선순위 900으로 지정한다. 저장소 추가만으로 unattended-upgrades의 자동 적용 대상에 포함됐다고 간주하지 않는다.
- Ubuntu 기본 동적 모듈 6개는 실제 설정에서 사용되지 않아 패키지 전환 시 제거했다. `nginx.conf`의 `/etc/nginx/modules-enabled/*.conf` include만 비활성화했으며, 다른 설정 파일과 `www-data` 워커 계정은 보존했다.
- 사이트 설정, 프록시, 캐시, 보안 헤더, 인증서, Certbot nginx 플러그인을 유지했다. 공식 바이너리에 필요한 `/var/cache/nginx` 상위 디렉터리를 준비했다.
- Dockerfile, CI와 Nginx HTTP fixture의 기준 이미지도 `nginx:1.30.7-alpine3.24-slim`으로 맞췄다. 멀티아키텍처 digest는 `sha256:8c950211a641ec5b23f265450517001f6bcca533666120ebe36cb4cff3d00a55`다. 컨테이너 이미지 변경은 native 호스트를 자동 업데이트하지 않는다.

## 검증과 다음 업데이트

호스트 변경은 사용자 승인 후 수행한다. 교체 전에 설정과 기존 패키지를 백업하고, 새 바이너리를 loopback의 별도 HTTP/HTTPS 포트에서 기동해 실제 설정과 응답을 비교한다. 패키지 설치 계획에서 의도하지 않은 패키지 변경이 없는지도 확인한다.

이번 적용에서는 홈페이지, API, release marker, 정상·누락 정적 자산, SPA fallback, MCP 스트림의 응답 상태와 Content-Type·Cache-Control·HSTS를 비교했다. 교체 후 공개 HTTPS로 재검증했으며 `nginx -t`와 `certbot renew --dry-run --cert-name noviis.kr`가 통과했다. MCP의 `text/event-stream` 연결은 제한 시간에 끝나지 않는 것이 정상일 수 있으므로 일반 응답과 구분한다.

설치 버전만 확인하지 말고 `systemctl show nginx -p MainPID`와 `/proc/<pid>/exe`를 통해 마스터·워커가 새 바이너리를 실행하는지 확인한다. 인증서 갱신 모의 실행 후에도 설정 검사와 실제 HTTPS 응답을 확인한다.

## 복구

이번 작업의 설정·인증서 및 이전 패키지 9개는 서버의 root 전용 `/var/lib/noviis/nginx-update-20261119`에 보존했다. 비공개 백업이나 로그를 저장소에 복사하지 않는다.

교체 중 패키지의 자동 서비스 제어를 일시 차단하고, 서비스 중지를 시도하기 전에 복구 필요 상태를 기록한다. 중지 명령이 실패하거나 시간 초과여도 실제 프로세스는 종료됐을 수 있으므로 복구를 생략하지 않는다. 새 버전 기동 검증에는 전체 경과시간 제한을 둔다.

설치·기동·응답 검증이 실패하면 이전 패키지와 정확한 설정을 복원하고 daemon reload, 서비스 시작, 이전 버전 및 HTTPS 응답 확인을 수행한다. 일시적으로 만든 `policy-rc.d`는 성공·실패 모두 정리한다. 이전 취약 버전 복원은 서비스 복구를 위한 임시 조치이며, 수정 버전 재적용이 필요하다. 백업과 현재 서비스 상태를 확인하지 않은 채 이 절차를 자동 재실행하지 않는다.
