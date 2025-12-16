# 📝 NoviIs

**NoviIs**는 Vue 3와 Spring Boot (Java 21)를 기반으로 구축된 모던 커뮤니티 플랫폼입니다. 사용자 친화적인 인터페이스와 강력한 관리 기능을 제공하며, 게시판, 게시글, 댓글, 알림 등 커뮤니티의 핵심 기능을 모두 갖추고 있습니다.

이 프로젝트는 **Monorepo** 구조로 구성되어 있으며, 백엔드와 프론트엔드가 각각 독립적인 디렉토리에서 관리됩니다.

## 📂 프로젝트 구성 (Project Components)

### [Backend](./backend/README.md)
Spring Boot 기반의 강력한 RESTful API 서버입니다.
-   **Tech**: Java 21, Spring Boot 3, JPA, Gradle
-   **Features**: 인증, 게시판/게시글/댓글 관리, 알림(SSE), 관리자 기능 등

### [Frontend](./frontend/README.md)
Vue 3 기반의 반응형 웹 애플리케이션입니다.
-   **Tech**: Vue 3, Vite, TypeScript, Pinia, TailwindCSS
-   **Features**: 사용자 UI, 게시판 탐색, 에디터, 실시간 알림, 다크 모드 등

## 🚀 빠른 시작 (Quick Start)

각 프로젝트의 상세 설정 및 실행 방법은 해당 디렉토리의 `README.md`를 참조하세요.

### 1. 저장소 클론 (Clone)
```bash
git clone https://github.com/weedrice/project_whiteboard.git
cd project_whiteboard
```

### 2. 백엔드 실행 (Backend)
```bash
cd backend
./gradlew bootRun
```

### 3. 프론트엔드 실행 (Frontend)
```bash
cd frontend
npm install
npm run dev
```

## 📝 라이선스 (License)

This project is licensed under the MIT License.
