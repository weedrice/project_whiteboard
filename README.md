# 📝 Project Whiteboard

**Project Whiteboard**는 Vue 3와 Spring Boot (Java 21)를 기반으로 구축된 모던 커뮤니티 플랫폼입니다. 사용자 친화적인 인터페이스와 강력한 관리 기능을 제공하며, 게시판, 게시글, 댓글, 알림 등 커뮤니티의 핵심 기능을 모두 갖추고 있습니다.

## 🚀 Tech Stack

### Frontend
-   **Framework**: Vue 3 (Composition API)
-   **Build Tool**: Vite
-   **State Management**: Pinia
-   **Routing**: Vue Router
-   **Styling**: TailwindCSS
-   **HTTP Client**: Axios
-   **Icons**: Lucide Vue Next

### Backend
-   **Framework**: Spring Boot 3.x
-   **Language**: Java 21
-   **Build Tool**: Gradle
-   **Database**: H2 (Dev), MySQL/MariaDB (Prod recommended)
-   **ORM**: JPA / Hibernate

---

## ✨ Key Features

### 1. 🔐 Authentication (인증)
-   **회원가입 & 로그인**: JWT 기반 인증 시스템.
-   **마이페이지**: 내 정보 조회 및 내가 쓴 글 관리.

### 2. 📋 Board & Post (게시판 & 게시글)
-   **게시판 목록**: 다양한 주제의 게시판 탐색.
-   **게시글 작성/수정/삭제**:
    -   WYSIWYG 에디터 (기본 텍스트 에리어 구현).
    -   **NSFW 설정**: 게시판 설정에 따라 NSFW(후방주의) 옵션 조건부 노출.
    -   태그 및 스포일러 설정.
-   **게시글 검색**: 게시판 내 제목/내용 검색 기능.
-   **좋아요**: 게시글 추천 기능.

### 3. 💬 Comment (댓글)
-   **댓글 작성/삭제**: 게시글에 의견 남기기.
-   **대댓글 (답글)**: 계층형 댓글 구조 지원.

### 4. 🔔 Notification (알림)
-   **실시간 알림**: 내 글에 댓글이 달리거나 좋아요를 받으면 알림 수신.
-   **알림 센터**: 헤더의 종 아이콘을 통해 읽지 않은 알림 확인 및 이동.

### 5. 🛡️ Admin (관리자)
-   **게시판 관리**: 게시판 생성, 수정, 삭제.
-   **카테고리 관리**: 게시판별 말머리(카테고리) 추가/수정/삭제.
-   **접근 제어**: 관리자 전용 메뉴 및 기능 보호.

---

## 🛠️ Getting Started

### Prerequisites
-   Node.js 18+
-   JDK 21
-   Git

### Installation & Run

#### 1. Clone the repository
```bash
git clone https://github.com/your-username/project_whiteboard.git
cd project_whiteboard
```

#### 2. Backend Setup
```bash
cd backend
# Build and Run
./gradlew bootRun
```
*Server runs on `http://localhost:8080`*

#### 3. Frontend Setup
```bash
cd frontend
# Install dependencies
npm install

# Run development server
npm run dev
```
*Client runs on `http://localhost:5173`*

---

## 📂 Project Structure

```
project_whiteboard/
├── backend/            # Spring Boot Application
│   ├── src/main/java/  # Java Source Code
│   └── build.gradle    # Gradle Config
│
└── frontend/           # Vue.js Application
    ├── src/
    │   ├── api/        # API Clients
    │   ├── components/ # Reusable Components
    │   ├── router/     # Route Definitions
    │   ├── stores/     # Pinia State Stores
    │   └── views/      # Page Components
    └── vite.config.js  # Vite Config
```

## 📝 License

This project is licensed under the MIT License.
