# 커뮤니티 서비스 API 명세서

## 문서 정보
| 항목 | 내용 |
|-----|------|
| 버전 | v1.0 |
| 작성일 | 2025-11-25 |
| 기반 문서 | FEATURE_SPEC.md |
| Base URL | `/api/v1` |

---

## 공통 사항

### 인증
- JWT Bearer Token 사용
- Header: `Authorization: Bearer {access_token}`
- Access Token 만료: 30분
- Refresh Token 만료: 14일

### 응답 형식

**성공 응답**
```json
{
  "success": true,
  "data": { ... },
  "error": null
}
```

**에러 응답**
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "ERROR_CODE",
    "message": "에러 메시지"
  }
}
```

### 페이징 응답
```json
{
  "success": true,
  "data": {
    "content": [ ... ],
    "page": 0,
    "size": 20,
    "totalElements": 100,
    "totalPages": 5,
    "hasNext": true,
    "hasPrevious": false
  },
  "error": null
}
```

### 공통 에러 코드
| 코드 | HTTP Status | 설명 |
|-----|-------------|------|
| UNAUTHORIZED | 401 | 인증 필요 |
| FORBIDDEN | 403 | 권한 없음 |
| NOT_FOUND | 404 | 리소스 없음 |
| VALIDATION_ERROR | 400 | 입력값 오류 |
| INTERNAL_ERROR | 500 | 서버 오류 |

---

## 목차
1. [회원 (Auth/Users)](#1-회원)
2. [게시판 (Boards)](#2-게시판)
3. [게시글 (Posts)](#3-게시글)
4. [댓글 (Comments)](#4-댓글)
5. [알림 (Notifications)](#5-알림)
6. [쪽지 (Messages)](#6-쪽지)
7. [검색 (Search)](#7-검색)
8. [포인트/상점 (Points/Shop)](#8-포인트상점)
9. [관리자 (Admin)](#9-관리자)
10. [기타 (Etc)](#10-기타)

---

## 1. 회원

### 1.1 회원가입
```
POST /auth/signup
```

**Request Body**
```json
{
  "loginId": "string (4-30자, 영문+숫자)",
  "password": "string (8-20자)",
  "email": "string (이메일 형식)",
  "displayName": "string (2-50자)"
}
```

**Response** `201 Created`
```json
{
  "success": true,
  "data": {
    "userId": 1,
    "loginId": "testuser",
    "email": "test@example.com",
    "displayName": "테스트유저"
  },
  "error": null
}
```

**에러 코드**
| 코드 | 설명 |
|-----|------|
| DUPLICATE_LOGIN_ID | 중복된 로그인 ID |
| DUPLICATE_EMAIL | 중복된 이메일 |
| INVALID_PASSWORD | 비밀번호 형식 오류 |

---

### 1.2 로그인
```
POST /auth/login
```

**Request Body**
```json
{
  "loginId": "string",
  "password": "string"
}
```

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJ...",
    "refreshToken": "eyJ...",
    "expiresIn": 1800,
    "user": {
      "userId": 1,
      "loginId": "testuser",
      "displayName": "테스트유저",
      "profileImageUrl": "https://...",
      "isEmailVerified": false
    }
  },
  "error": null
}
```

**에러 코드**
| 코드 | 설명 |
|-----|------|
| INVALID_CREDENTIALS | 로그인 정보 불일치 |
| ACCOUNT_SUSPENDED | 정지된 계정 |
| ACCOUNT_DELETED | 탈퇴한 계정 |

---

### 1.3 로그아웃
```
POST /auth/logout
```
`🔒 인증 필요`

**Request Body**
```json
{
  "refreshToken": "string"
}
```

**Response** `200 OK`
```json
{
  "success": true,
  "data": null,
  "error": null
}
```

---

### 1.4 토큰 갱신
```
POST /auth/refresh
```

**Request Body**
```json
{
  "refreshToken": "string"
}
```

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJ...",
    "expiresIn": 1800
  },
  "error": null
}
```

**에러 코드**
| 코드 | 설명 |
|-----|------|
| INVALID_REFRESH_TOKEN | 유효하지 않은 토큰 |
| EXPIRED_REFRESH_TOKEN | 만료된 토큰 |

---

### 1.5 이메일 인증
```
POST /auth/verify-email
```

**Request Body**
```json
{
  "token": "string"
}
```

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "message": "이메일 인증이 완료되었습니다."
  },
  "error": null
}
```

---

### 1.6 이메일 인증 재발송
```
POST /auth/resend-verification
```
`🔒 인증 필요`

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "message": "인증 메일이 발송되었습니다."
  },
  "error": null
}
```

---

### 1.7 비밀번호 변경
```
PUT /auth/password
```
`🔒 인증 필요`

**Request Body**
```json
{
  "currentPassword": "string",
  "newPassword": "string"
}
```

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "message": "비밀번호가 변경되었습니다."
  },
  "error": null
}
```

**에러 코드**
| 코드 | 설명 |
|-----|------|
| INVALID_CURRENT_PASSWORD | 현재 비밀번호 불일치 |
| PASSWORD_RECENTLY_USED | 최근 사용한 비밀번호 |

---

### 1.8 비밀번호 찾기 (재설정 메일 요청)
```
POST /auth/forgot-password
```

**Request Body**
```json
{
  "email": "string"
}
```

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "message": "비밀번호 재설정 메일이 발송되었습니다."
  },
  "error": null
}
```

---

### 1.9 비밀번호 재설정
```
POST /auth/reset-password
```

**Request Body**
```json
{
  "token": "string",
  "newPassword": "string"
}
```

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "message": "비밀번호가 재설정되었습니다."
  },
  "error": null
}
```

---

### 1.10 내 정보 조회
```
GET /users/me
```
`🔒 인증 필요`

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "userId": 1,
    "loginId": "testuser",
    "email": "test@example.com",
    "displayName": "테스트유저",
    "profileImageUrl": "https://...",
    "status": "ACTIVE",
    "isEmailVerified": true,
    "createdAt": "2025-01-01T00:00:00Z"
  },
  "error": null
}
```

---

### 1.11 프로필 조회
```
GET /users/{userId}
```

**Path Parameters**
| 파라미터 | 타입 | 설명 |
|---------|------|------|
| userId | Long | 사용자 ID |

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "userId": 1,
    "displayName": "테스트유저",
    "profileImageUrl": "https://...",
    "createdAt": "2025-01-01T00:00:00Z",
    "postCount": 42,
    "commentCount": 128
  },
  "error": null
}
```

---

### 1.12 프로필 수정
```
PUT /users/me
```
`🔒 인증 필요`

**Request Body**
```json
{
  "displayName": "string (2-50자, 선택)",
  "profileImageUrl": "string (선택)"
}
```

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "userId": 1,
    "displayName": "새닉네임",
    "profileImageUrl": "https://..."
  },
  "error": null
}
```

---

### 1.13 회원 탈퇴
```
DELETE /users/me
```
`🔒 인증 필요`

**Request Body**
```json
{
  "password": "string"
}
```

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "message": "회원 탈퇴가 완료되었습니다."
  },
  "error": null
}
```

---

### 1.14 설정 조회
```
GET /users/me/settings
```
`🔒 인증 필요`

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "theme": "LIGHT",
    "language": "ko",
    "timezone": "Asia/Seoul",
    "hideNsfw": true
  },
  "error": null
}
```

---

### 1.15 설정 수정
```
PUT /users/me/settings
```
`🔒 인증 필요`

**Request Body**
```json
{
  "theme": "DARK",
  "language": "ko",
  "timezone": "Asia/Seoul",
  "hideNsfw": false
}
```

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "theme": "DARK",
    "language": "ko",
    "timezone": "Asia/Seoul",
    "hideNsfw": false
  },
  "error": null
}
```

---

### 1.16 알림 설정 조회
```
GET /users/me/notification-settings
```
`🔒 인증 필요`

**Response** `200 OK`
```json
{
  "success": true,
  "data": [
    { "notificationType": "LIKE", "isEnabled": true },
    { "notificationType": "COMMENT", "isEnabled": true },
    { "notificationType": "REPLY", "isEnabled": true }
  ],
  "error": null
}
```

---

### 1.17 알림 설정 일괄 수정
```
PUT /users/me/notification-settings/bulk
```
`🔒 인증 필요`

**Request Body**
```json
{
  "settings": [
    { "notificationType": "LIKE", "isEnabled": true },
    { "notificationType": "COMMENT", "isEnabled": false },
    { "notificationType": "REPLY", "isEnabled": true }
  ]
}
```

**Response** `200 OK`
```json
{
  "success": true,
  "data": [
    { "notificationType": "LIKE", "isEnabled": true },
    { "notificationType": "COMMENT", "isEnabled": false },
    { "notificationType": "REPLY", "isEnabled": true }
  ],
  "error": null
}
```

---

### 1.18 사용자 차단
```
POST /users/{userId}/block
```
`🔒 인증 필요`

**Path Parameters**
| 파라미터 | 타입 | 설명 |
|---------|------|------|
| userId | Long | 차단할 사용자 ID |

**Response** `201 Created`
```json
{
  "success": true,
  "data": {
    "message": "차단되었습니다."
  },
  "error": null
}
```

**에러 코드**
| 코드 | 설명 |
|-----|------|
| CANNOT_BLOCK_SELF | 자기 자신 차단 불가 |
| ALREADY_BLOCKED | 이미 차단됨 |

---

### 1.19 사용자 차단 해제
```
DELETE /users/{userId}/block
```
`🔒 인증 필요`

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "message": "차단이 해제되었습니다."
  },
  "error": null
}
```

---

### 1.20 차단 목록 조회
```
GET /users/me/blocks
```
`🔒 인증 필요`

**Query Parameters**
| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|-------|------|
| page | int | N | 0 | 페이지 번호 |
| size | int | N | 20 | 페이지 크기 |

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "userId": 5,
        "loginId": "blockeduser",
        "displayName": "차단된유저",
        "blockedAt": "2025-01-15T10:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false,
    "hasPrevious": false
  },
  "error": null
}
```

---

## 2. 게시판

### 2.1 게시판 목록 조회
```
GET /boards
```

**Response** `200 OK`
```json
{
  "success": true,
  "data": [
    {
      "boardId": 1,
      "boardName": "자유게시판",
      "description": "자유롭게 이야기하는 공간",
      "iconUrl": "https://...",
      "bannerUrl": "https://...",
      "allowNsfw": false,
      "subscriberCount": 1500
    },
    {
      "boardId": 2,
      "boardName": "유머게시판",
      "description": "웃긴 글 모음",
      "iconUrl": null,
      "bannerUrl": null,
      "allowNsfw": false,
      "subscriberCount": 2300
    }
  ],
  "error": null
}
```

---

### 2.2 게시판 상세 조회
```
GET /boards/{boardId}
```

**Path Parameters**
| 파라미터 | 타입 | 설명 |
|---------|------|------|
| boardId | Long | 게시판 ID |

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "boardId": 1,
    "boardName": "자유게시판",
    "description": "자유롭게 이야기하는 공간",
    "iconUrl": "https://...",
    "bannerUrl": "https://...",
    "allowNsfw": false,
    "subscriberCount": 1500,
    "createdAt": "2025-01-01T00:00:00Z",
    "creator": {
      "userId": 1,
      "displayName": "관리자"
    },
    "categories": [
      { "categoryId": 1, "name": "일반" },
      { "categoryId": 2, "name": "질문" }
    ],
    "isSubscribed": false
  },
  "error": null
}
```

---

### 2.3 게시판 생성
```
POST /boards
```
`🔒 인증 필요`

**Request Body**
```json
{
  "boardName": "string (2-100자)",
  "description": "string (선택, 최대 255자)",
  "iconUrl": "string (선택)",
  "bannerUrl": "string (선택)",
  "allowNsfw": false
}
```

**Response** `201 Created`
```json
{
  "success": true,
  "data": {
    "boardId": 3,
    "boardName": "새게시판",
    "description": "새로운 게시판입니다",
    "iconUrl": null,
    "bannerUrl": null,
    "allowNsfw": false
  },
  "error": null
}
```

**에러 코드**
| 코드 | 설명 |
|-----|------|
| DUPLICATE_BOARD_NAME | 중복된 게시판 이름 |

---

### 2.4 게시판 수정
```
PUT /boards/{boardId}
```
`🔒 인증 필요 (게시판 관리자)`

**Request Body**
```json
{
  "description": "string (선택)",
  "iconUrl": "string (선택)",
  "bannerUrl": "string (선택)",
  "allowNsfw": true
}
```

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "boardId": 1,
    "boardName": "자유게시판",
    "description": "수정된 설명",
    "iconUrl": "https://...",
    "bannerUrl": "https://...",
    "allowNsfw": true
  },
  "error": null
}
```

---

### 2.5 게시판 구독
```
POST /boards/{boardId}/subscribe
```
`🔒 인증 필요`

**Response** `201 Created`
```json
{
  "success": true,
  "data": {
    "message": "구독되었습니다.",
    "currentSubscriberCount": 1501
  },
  "error": null
}
```

---

### 2.6 게시판 구독 취소
```
DELETE /boards/{boardId}/subscribe
```
`🔒 인증 필요`

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "message": "구독이 취소되었습니다."
  },
  "error": null
}
```

---

### 2.7 내 구독 게시판 목록
```
GET /users/me/subscriptions
```
`🔒 인증 필요`

**Query Parameters**
| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|-------|------|
| page | int | N | 0 | 페이지 번호 |
| size | int | N | 20 | 페이지 크기 |

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "boardId": 1,
        "boardName": "자유게시판",
        "iconUrl": "https://...",
        "role": "MEMBER",
        "subscribedAt": "2025-01-10T00:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 5,
    "totalPages": 1,
    "hasNext": false,
    "hasPrevious": false
  },
  "error": null
}
```

---

### 2.8 카테고리 목록 조회
```
GET /boards/{boardId}/categories
```

**Response** `200 OK`
```json
{
  "success": true,
  "data": [
    { "categoryId": 1, "name": "일반", "sortOrder": 1 },
    { "categoryId": 2, "name": "질문", "sortOrder": 2 },
    { "categoryId": 3, "name": "정보", "sortOrder": 3 }
  ],
  "error": null
}
```

---

### 2.9 카테고리 생성
```
POST /boards/{boardId}/categories
```
`🔒 인증 필요 (게시판 관리자)`

**Request Body**
```json
{
  "name": "string (2-100자)",
  "sortOrder": 1
}
```

**Response** `201 Created`
```json
{
  "success": true,
  "data": {
    "categoryId": 4,
    "name": "새카테고리",
    "sortOrder": 4
  },
  "error": null
}
```

---

### 2.10 카테고리 수정
```
PUT /boards/{boardId}/categories/{categoryId}
```
`🔒 인증 필요 (게시판 관리자)`

**Request Body**
```json
{
  "name": "string",
  "sortOrder": 2,
  "isActive": true
}
```

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "categoryId": 4,
    "name": "수정된카테고리",
    "sortOrder": 2,
    "isActive": true
  },
  "error": null
}
```

---

### 2.11 카테고리 삭제
```
DELETE /boards/{boardId}/categories/{categoryId}
```
`🔒 인증 필요 (게시판 관리자)`

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "message": "카테고리가 삭제되었습니다."
  },
  "error": null
}
```

---

## 3. 게시글

### 3.1 게시글 목록 조회
```
GET /boards/{boardId}/posts
```

**Query Parameters**
| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|-------|------|
| categoryId | Long | N | - | 카테고리 필터 |
| sort | String | N | latest | 정렬 (latest/popular) |
| page | int | N | 0 | 페이지 번호 |
| size | int | N | 20 | 페이지 크기 |

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "postId": 100,
        "title": "게시글 제목",
        "author": {
          "userId": 1,
          "displayName": "작성자",
          "profileImageUrl": "https://..."
        },
        "category": {
          "categoryId": 1,
          "name": "일반"
        },
        "viewCount": 150,
        "likeCount": 10,
        "commentCount": 5,
        "isNotice": false,
        "isNsfw": false,
        "isSpoiler": false,
        "createdAt": "2025-01-20T10:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 150,
    "totalPages": 8,
    "hasNext": true,
    "hasPrevious": false
  },
  "error": null
}
```

---

### 3.2 게시글 상세 조회
```
GET /posts/{postId}
```

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "postId": 100,
    "title": "게시글 제목",
    "contents": "게시글 내용입니다...",
    "author": {
      "userId": 1,
      "displayName": "작성자",
      "profileImageUrl": "https://..."
    },
    "board": {
      "boardId": 1,
      "boardName": "자유게시판"
    },
    "category": {
      "categoryId": 1,
      "name": "일반"
    },
    "tags": ["태그1", "태그2"],
    "viewCount": 151,
    "likeCount": 10,
    "commentCount": 5,
    "isNotice": false,
    "isNsfw": false,
    "isSpoiler": false,
    "isLiked": false,
    "isScraped": false,
    "createdAt": "2025-01-20T10:00:00Z",
    "modifiedAt": "2025-01-20T10:00:00Z"
  },
  "error": null
}
```

---

### 3.3 게시글 작성
```
POST /boards/{boardId}/posts
```
`🔒 인증 필요`

**Request Body**
```json
{
  "categoryId": 1,
  "title": "string (2-200자)",
  "contents": "string",
  "tags": ["태그1", "태그2"],
  "isNsfw": false,
  "isSpoiler": false,
  "fileIds": [1, 2, 3]
}
```

**Response** `201 Created`
```json
{
  "success": true,
  "data": {
    "postId": 101,
    "title": "새 게시글",
    "createdAt": "2025-01-21T10:00:00Z"
  },
  "error": null
}
```
**참고**
- 게시글 작성 실패 시 `fileIds`로 업로드된 파일들은 `related_id`가 연결되지 않은 상태로 남습니다.
- 이러한 파일들은 매일 새벽 `BATCH-002` 작업에 의해 자동 삭제됩니다.

---

### 3.4 게시글 수정
```
PUT /posts/{postId}
```
`🔒 인증 필요 (작성자)`

**Request Body**
```json
{
  "categoryId": 2,
  "title": "string",
  "contents": "string",
  "tags": ["수정태그"],
  "isNsfw": false,
  "isSpoiler": true
}
```

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "postId": 100,
    "title": "수정된 제목",
    "modifiedAt": "2025-01-21T15:00:00Z"
  },
  "error": null
}
```

---

### 3.5 게시글 삭제
```
DELETE /posts/{postId}
```
`🔒 인증 필요 (작성자 또는 관리자)`

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "message": "게시글이 삭제되었습니다."
  },
  "error": null
}
```

---

### 3.6 게시글 좋아요
```
POST /posts/{postId}/like
```
`🔒 인증 필요`

**Response** `201 Created`
```json
{
  "success": true,
  "data": {
    "likeCount": 11
  },
  "error": null
}
```

---

### 3.7 게시글 좋아요 취소
```
DELETE /posts/{postId}/like
```
`🔒 인증 필요`

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "likeCount": 10
  },
  "error": null
}
```

---

### 3.8 게시글 스크랩
```
POST /posts/{postId}/scrap
```
`🔒 인증 필요`

**Request Body**
```json
{
  "remark": "string (선택, 메모)"
}
```

**Response** `201 Created`
```json
{
  "success": true,
  "data": {
    "message": "스크랩되었습니다."
  },
  "error": null
}
```

---

### 3.9 게시글 스크랩 취소
```
DELETE /posts/{postId}/scrap
```
`🔒 인증 필요`

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "message": "스크랩이 취소되었습니다."
  },
  "error": null
}
```

---

### 3.10 내 스크랩 목록
```
GET /users/me/scraps
```
`🔒 인증 필요`

**Query Parameters**
| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|-------|------|
| page | int | N | 0 | 페이지 번호 |
| size | int | N | 20 | 페이지 크기 |

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "scrapId": 1,
        "post": {
          "postId": 100,
          "title": "스크랩한 글",
          "boardName": "자유게시판"
        },
        "remark": "나중에 읽기",
        "createdAt": "2025-01-20T10:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 10,
    "totalPages": 1,
    "hasNext": false,
    "hasPrevious": false
  },
  "error": null
}
```

---

### 3.11 임시저장 목록
```
GET /users/me/drafts
```
`🔒 인증 필요`

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "draftId": 1,
        "title": "작성중인 글",
        "boardId": 1,
        "boardName": "자유게시판",
        "modifiedAt": "2025-01-20T10:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 2,
    "totalPages": 1,
    "hasNext": false,
    "hasPrevious": false
  },
  "error": null
}
```

---

### 3.12 임시저장
```
POST /drafts
```
`🔒 인증 필요`

**Request Body**
```json
{
  "draftId": null,
  "boardId": 1,
  "categoryId": 1,
  "title": "string",
  "contents": "string"
}
```

**Response** `201 Created` / `200 OK`
```json
{
  "success": true,
  "data": {
    "draftId": 1,
    "title": "임시저장된 글",
    "modifiedAt": "2025-01-21T10:00:00Z"
  },
  "error": null
}
```

---

### 3.13 임시저장 상세 조회
```
GET /drafts/{draftId}
```
`🔒 인증 필요`

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "draftId": 1,
    "boardId": 1,
    "categoryId": 1,
    "title": "임시저장 제목",
    "contents": "임시저장 내용...",
    "modifiedAt": "2025-01-21T10:00:00Z"
  },
  "error": null
}
```

---

### 3.14 임시저장 삭제
```
DELETE /drafts/{draftId}
```
`🔒 인증 필요`

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "message": "임시저장이 삭제되었습니다."
  },
  "error": null
}
```

---

### 3.15 인기글 목록
```
GET /posts/popular
```

**Query Parameters**
| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|-------|------|
| type | String | N | DAILY | 기간 (DAILY/WEEKLY/MONTHLY) |
| page | int | N | 0 | 페이지 번호 |
| size | int | N | 20 | 페이지 크기 |

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "rank": 1,
        "post": {
          "postId": 50,
          "title": "인기글 1위",
          "boardName": "자유게시판",
          "likeCount": 500,
          "commentCount": 120
        },
        "score": 1500.5
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 100,
    "totalPages": 5,
    "hasNext": true,
    "hasPrevious": false
  },
  "error": null
}
```

---

### 3.16 내가 작성한 게시글
```
GET /users/me/posts
```
`🔒 인증 필요`

**Query Parameters**
| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|-------|------|
| page | int | N | 0 | 페이지 번호 |
| size | int | N | 20 | 페이지 크기 |

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "postId": 100,
        "title": "내가 쓴 글",
        "boardName": "자유게시판",
        "viewCount": 50,
        "likeCount": 5,
        "commentCount": 3,
        "createdAt": "2025-01-20T10:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 42,
    "totalPages": 3,
    "hasNext": true,
    "hasPrevious": false
  },
  "error": null
}
```

---

### 3.17 게시글 수정 이력
```
GET /posts/{postId}/versions
```

**Response** `200 OK`
```json
{
  "success": true,
  "data": [
    {
      "versionId": 3,
      "actionType": "UPDATE",
      "title": "수정된 제목",
      "createdAt": "2025-01-21T15:00:00Z"
    },
    {
      "versionId": 2,
      "actionType": "UPDATE",
      "title": "이전 제목",
      "createdAt": "2025-01-20T12:00:00Z"
    },
    {
      "versionId": 1,
      "actionType": "CREATE",
      "title": "원래 제목",
      "createdAt": "2025-01-20T10:00:00Z"
    }
  ],
  "error": null
}
```

---

## 4. 댓글

### 4.1 댓글 목록 조회
```
GET /posts/{postId}/comments
```

**Query Parameters**
| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|-------|------|
| sort | String | N | latest | 정렬 (latest/popular) |
| page | int | N | 0 | 페이지 번호 |
| size | int | N | 20 | 페이지 크기 |

**참고**
- 기본 조회 시 `depth`는 1(대댓글)까지만 포함됩니다.
- 그 하위 댓글이나 더 많은 대댓글은 `4.9 대댓글 더보기` API를 사용하세요.

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "commentId": 1,
        "content": "댓글 내용입니다",
        "author": {
          "userId": 2,
          "displayName": "댓글작성자",
          "profileImageUrl": "https://..."
        },
        "depth": 0,
        "likeCount": 5,
        "isLiked": false,
        "isDeleted": false,
        "createdAt": "2025-01-20T11:00:00Z",
        "children": [
          {
            "commentId": 2,
            "content": "대댓글입니다",
            "author": {
              "userId": 3,
              "displayName": "대댓글작성자",
              "profileImageUrl": null
            },
            "depth": 1,
            "likeCount": 1,
            "isLiked": false,
            "isDeleted": false,
            "createdAt": "2025-01-20T11:30:00Z",
            "children": []
          }
        ]
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 5,
    "totalPages": 1,
    "hasNext": false,
    "hasPrevious": false
  },
  "error": null
}
```

---

### 4.2 댓글 작성
```
POST /posts/{postId}/comments
```
`🔒 인증 필요`

**Request Body**
```json
{
  "parentId": null,
  "content": "string (1-5000자)"
}
```

**Response** `201 Created`
```json
{
  "success": true,
  "data": {
    "commentId": 10,
    "content": "새 댓글입니다",
    "depth": 0,
    "createdAt": "2025-01-21T10:00:00Z"
  },
  "error": null
}
```

---

### 4.3 대댓글 작성
```
POST /posts/{postId}/comments
```
`🔒 인증 필요`

**Request Body**
```json
{
  "parentId": 1,
  "content": "string"
}
```

**Response** `201 Created`
```json
{
  "success": true,
  "data": {
    "commentId": 11,
    "content": "대댓글입니다",
    "depth": 1,
    "parentId": 1,
    "createdAt": "2025-01-21T10:30:00Z"
  },
  "error": null
}
```

---

### 4.4 댓글 수정
```
PUT /comments/{commentId}
```
`🔒 인증 필요 (작성자)`

**Request Body**
```json
{
  "content": "string"
}
```

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "commentId": 10,
    "content": "수정된 댓글입니다",
    "modifiedAt": "2025-01-21T11:00:00Z"
  },
  "error": null
}
```

---

### 4.5 댓글 삭제
```
DELETE /comments/{commentId}
```
`🔒 인증 필요 (작성자 또는 관리자)`

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "message": "댓글이 삭제되었습니다."
  },
  "error": null
}
```

---

### 4.6 댓글 좋아요
```
POST /comments/{commentId}/like
```
`🔒 인증 필요`

**Response** `201 Created`
```json
{
  "success": true,
  "data": {
    "likeCount": 6
  },
  "error": null
}
```

---

### 4.7 댓글 좋아요 취소
```
DELETE /comments/{commentId}/like
```
`🔒 인증 필요`

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "likeCount": 5
  },
  "error": null
}
```

---

### 4.8 내가 작성한 댓글
```
GET /users/me/comments
```
`🔒 인증 필요`

**Query Parameters**
| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|-------|------|
| page | int | N | 0 | 페이지 번호 |
| size | int | N | 20 | 페이지 크기 |

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "commentId": 10,
        "content": "내가 쓴 댓글",
        "post": {
          "postId": 100,
          "title": "원글 제목"
        },
        "likeCount": 2,
        "createdAt": "2025-01-21T10:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 128,
    "totalPages": 7,
    "hasNext": true,
    "hasPrevious": false
  },
  "error": null
}
```

---

### 4.9 대댓글 더보기
```
GET /comments/{commentId}/replies
```

**Query Parameters**
| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|-------|------|
| page | int | N | 0 | 페이지 번호 |
| size | int | N | 10 | 페이지 크기 |

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "commentId": 15,
        "content": "추가 대댓글",
        "author": {
          "userId": 5,
          "displayName": "사용자5"
        },
        "depth": 1,
        "likeCount": 0,
        "createdAt": "2025-01-21T12:00:00Z"
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 25,
    "totalPages": 3,
    "hasNext": true,
    "hasPrevious": false
  },
  "error": null
}
```

---

## 5. 알림

### 5.1 알림 목록 조회
```
GET /notifications
```
`🔒 인증 필요`

**Query Parameters**
| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|-------|------|
| isRead | Boolean | N | - | 읽음 여부 필터 |
| page | int | N | 0 | 페이지 번호 |
| size | int | N | 20 | 페이지 크기 |

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "notificationId": 1,
        "notificationType": "COMMENT",
        "message": "새 댓글이 달렸습니다.",
        "actor": {
          "userId": 2,
          "displayName": "댓글작성자"
        },
        "sourceType": "POST",
        "sourceId": 100,
        "isRead": false,
        "createdAt": "2025-01-21T10:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 50,
    "totalPages": 3,
    "hasNext": true,
    "hasPrevious": false
  },
  "error": null
}
```

---

### 5.2 알림 읽음 처리
```
PUT /notifications/{notificationId}/read
```
`🔒 인증 필요`

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "message": "읽음 처리되었습니다."
  },
  "error": null
}
```

---

### 5.3 알림 전체 읽음 처리
```
PUT /notifications/read-all
```
`🔒 인증 필요`

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "message": "전체 읽음 처리되었습니다."
  },
  "error": null
}
```

---

### 5.4 읽지 않은 알림 수
```
GET /notifications/unread-count
```
`🔒 인증 필요`

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "count": 15
  },
  "error": null
}
```

---

## 6. 쪽지

### 6.1 쪽지 발송
```
POST /messages
```
`🔒 인증 필요`

**Request Body**
```json
{
  "receiverId": 5,
  "content": "string (1-5000자)"
}
```

**Response** `201 Created`
```json
{
  "success": true,
  "data": {
    "messageId": 1,
    "receiverId": 5,
    "content": "안녕하세요",
    "createdAt": "2025-01-21T10:00:00Z"
  },
  "error": null
}
```

**에러 코드**
| 코드 | 설명 |
|-----|------|
| USER_NOT_FOUND | 수신자 없음 |
| BLOCKED_BY_USER | 상대방에게 차단됨 |

---

### 6.2 받은 쪽지 목록
```
GET /messages/received
```
`🔒 인증 필요`

**Query Parameters**
| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|-------|------|
| page | int | N | 0 | 페이지 번호 |
| size | int | N | 20 | 페이지 크기 |

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "messageId": 1,
        "sender": {
          "userId": 2,
          "displayName": "발신자"
        },
        "content": "안녕하세요...",
        "isRead": false,
        "createdAt": "2025-01-21T10:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 30,
    "totalPages": 2,
    "hasNext": true,
    "hasPrevious": false
  },
  "error": null
}
```

---

### 6.3 보낸 쪽지 목록
```
GET /messages/sent
```
`🔒 인증 필요`

**Query Parameters**
| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|-------|------|
| page | int | N | 0 | 페이지 번호 |
| size | int | N | 20 | 페이지 크기 |

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "messageId": 5,
        "receiver": {
          "userId": 3,
          "displayName": "수신자"
        },
        "content": "보낸 메시지...",
        "isRead": true,
        "createdAt": "2025-01-20T15:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 10,
    "totalPages": 1,
    "hasNext": false,
    "hasPrevious": false
  },
  "error": null
}
```

---

### 6.4 쪽지 상세 조회
```
GET /messages/{messageId}
```
`🔒 인증 필요 (발신자 또는 수신자)`

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "messageId": 1,
    "sender": {
      "userId": 2,
      "displayName": "발신자"
    },
    "receiver": {
      "userId": 1,
      "displayName": "수신자"
    },
    "content": "쪽지 전체 내용입니다...",
    "isRead": true,
    "createdAt": "2025-01-21T10:00:00Z"
  },
  "error": null
}
```

**에러 코드**
| 코드 | 설명 |
|-----|------|
| NOT_FOUND | 쪽지가 없거나, 접근할 수 없거나, 차단된 대화의 쪽지 |

---

### 6.5 쪽지 읽음 처리
```
POST /messages/{messageId}/read
```
`🔒 인증 필요 (수신자)`

**Response** `200 OK`
```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**에러 코드**
| 코드 | 설명 |
|-----|------|
| NOT_FOUND | 쪽지가 없거나, 접근할 수 없거나, 차단된 대화의 쪽지 |
| FORBIDDEN | 수신자가 아닌 사용자의 읽음 처리 요청 |

---

### 6.6 쪽지 삭제
```
DELETE /messages/{messageId}
```
`🔒 인증 필요 (발신자 또는 수신자)`

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "message": "쪽지가 삭제되었습니다."
  },
  "error": null
}
```

---

### 6.7 읽지 않은 쪽지 수
```
GET /messages/unread-count
```
`🔒 인증 필요`

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "count": 5
  },
  "error": null
}
```

---

## 7. 검색

### 7.1 통합 검색
```
GET /search
```

**Query Parameters**
| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|-------|------|
| q | String | Y | - | 검색어 |
| type | String | N | all | 검색 대상 (all/post/comment/user) |
| page | int | N | 0 | 페이지 번호 |
| size | int | N | 20 | 페이지 크기 |

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "posts": {
      "content": [
        {
          "postId": 100,
          "title": "검색 결과 게시글",
          "boardName": "자유게시판",
          "createdAt": "2025-01-20T10:00:00Z"
        }
      ],
      "totalElements": 50
    },
    "users": {
      "content": [
        {
          "userId": 5,
          "displayName": "검색된 사용자",
          "profileImageUrl": "https://..."
        }
      ],
      "totalElements": 3
    }
  },
  "error": null
}
```

---

### 7.2 게시글 검색
```
GET /search/posts
```

**Query Parameters**
| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|-------|------|
| q | String | Y | - | 검색어 |
| boardId | Long | N | - | 게시판 필터 |
| page | int | N | 0 | 페이지 번호 |
| size | int | N | 20 | 페이지 크기 |

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "postId": 100,
        "title": "검색 결과",
        "contents": "...검색어가 포함된 내용...",
        "boardName": "자유게시판",
        "author": {
          "userId": 1,
          "displayName": "작성자"
        },
        "createdAt": "2025-01-20T10:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 50,
    "totalPages": 3,
    "hasNext": true,
    "hasPrevious": false
  },
  "error": null
}
```

---

### 7.3 인기 검색어
```
GET /search/popular
```

**Query Parameters**
| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|-------|------|
| period | String | N | daily | 기간 (daily/weekly) |
| limit | int | N | 10 | 개수 |

**Response** `200 OK`
```json
{
  "success": true,
  "data": [
    { "rank": 1, "keyword": "인기검색어1", "count": 1500 },
    { "rank": 2, "keyword": "인기검색어2", "count": 1200 },
    { "rank": 3, "keyword": "인기검색어3", "count": 900 }
  ],
  "error": null
}
```

---

### 7.4 최근 검색어
```
GET /search/recent
```
`🔒 인증 필요`

**Query Parameters**
| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|-------|------|
| limit | int | N | 10 | 개수 |

**Response** `200 OK`
```json
{
  "success": true,
  "data": [
    { "logId": 1, "keyword": "최근검색어1", "searchedAt": "2025-01-21T10:00:00Z" },
    { "logId": 2, "keyword": "최근검색어2", "searchedAt": "2025-01-21T09:00:00Z" }
  ],
  "error": null
}
```

---

### 7.5 최근 검색어 삭제
```
DELETE /search/recent/{logId}
```
`🔒 인증 필요`

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "message": "삭제되었습니다."
  },
  "error": null
}
```

---

### 7.6 최근 검색어 전체 삭제
```
DELETE /search/recent
```
`🔒 인증 필요`

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "message": "전체 삭제되었습니다."
  },
  "error": null
}
```

---

## 8. 포인트/상점

### 8.1 내 포인트 조회
```
GET /points
```
`🔒 인증 필요`

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "currentPoint": 5000,
    "totalEarned": 10000,
    "totalSpent": 5000
  },
  "error": null
}
```

---

### 8.2 포인트 이력 조회
```
GET /points/history
```
`🔒 인증 필요`

**Query Parameters**
| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|-------|------|
| type | String | N | - | 타입 필터 (EARN/SPEND/EXPIRE) |
| page | int | N | 0 | 페이지 번호 |
| size | int | N | 20 | 페이지 크기 |

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "historyId": 100,
        "type": "EARN",
        "amount": 10,
        "balanceAfter": 5000,
        "description": "게시글 작성 보상",
        "createdAt": "2025-01-21T10:00:00Z"
      },
      {
        "historyId": 99,
        "type": "SPEND",
        "amount": -100,
        "balanceAfter": 4990,
        "description": "아이템 구매: 프리미엄 이모티콘",
        "createdAt": "2025-01-20T15:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 200,
    "totalPages": 10,
    "hasNext": true,
    "hasPrevious": false
  },
  "error": null
}
```

---

### 8.3 상점 아이템 목록
```
GET /shop/items
```

**Query Parameters**
| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|-------|------|
| itemType | String | N | - | 아이템 타입 필터 |
| page | int | N | 0 | 페이지 번호 |
| size | int | N | 20 | 페이지 크기 |

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "itemId": 1,
        "itemName": "프리미엄 이모티콘",
        "description": "특별한 이모티콘 세트",
        "price": 100,
        "itemType": "EMOTICON",
        "imageUrl": "https://..."
      },
      {
        "itemId": 2,
        "itemName": "닉네임 색상 변경",
        "description": "닉네임 색상을 변경합니다",
        "price": 500,
        "itemType": "DECORATION",
        "imageUrl": "https://..."
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 15,
    "totalPages": 1,
    "hasNext": false,
    "hasPrevious": false
  },
  "error": null
}
```

---

### 8.4 아이템 상세 조회
```
GET /shop/items/{itemId}
```

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "itemId": 1,
    "itemName": "프리미엄 이모티콘",
    "description": "특별한 이모티콘 세트입니다. 10종의 이모티콘이 포함되어 있습니다.",
    "price": 100,
    "itemType": "EMOTICON",
    "imageUrl": "https://...",
    "metadata": {
      "emoticons": ["😀", "😎", "🎉"]
    }
  },
  "error": null
}
```

---

### 8.5 아이템 구매
```
POST /shop/items/{itemId}/purchase
```
`🔒 인증 필요`

**Response** `201 Created`
```json
{
  "success": true,
  "data": {
    "purchaseId": 50,
    "itemName": "프리미엄 이모티콘",
    "price": 100,
    "remainingPoint": 4900,
    "purchasedAt": "2025-01-21T10:00:00Z"
  },
  "error": null
}
```

**에러 코드**
| 코드 | 설명 |
|-----|------|
| INSUFFICIENT_POINTS | 포인트 부족 |
| ITEM_NOT_AVAILABLE | 판매 중단된 아이템 |

---

### 8.6 구매 이력 조회
```
GET /shop/purchases
```
`🔒 인증 필요`

**Query Parameters**
| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|-------|------|
| page | int | N | 0 | 페이지 번호 |
| size | int | N | 20 | 페이지 크기 |

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "purchaseId": 50,
        "item": {
          "itemId": 1,
          "itemName": "프리미엄 이모티콘",
          "imageUrl": "https://..."
        },
        "price": 100,
        "purchasedAt": "2025-01-21T10:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 5,
    "totalPages": 1,
    "hasNext": false,
    "hasPrevious": false
  },
  "error": null
}
```

---

## 9. 관리자

### 9.1 신고 목록 조회
```
GET /admin/reports
```
`🔒 관리자 인증 필요`

**Query Parameters**
| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|-------|------|
| status | String | N | PENDING | 상태 (PENDING/RESOLVED/REJECTED) |
| targetType | String | N | - | 대상 타입 (POST/COMMENT/USER) |
| page | int | N | 0 | 페이지 번호 |
| size | int | N | 20 | 페이지 크기 |

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "reportId": 1,
        "reporter": {
          "userId": 5,
          "displayName": "신고자"
        },
        "targetType": "POST",
        "targetId": 100,
        "reasonType": "SPAM",
        "contents": "스팸 게시글입니다",
        "status": "PENDING",
        "createdAt": "2025-01-21T10:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 25,
    "totalPages": 2,
    "hasNext": true,
    "hasPrevious": false
  },
  "error": null
}
```

---

### 9.2 신고 처리
```
PUT /admin/reports/{reportId}
```
`🔒 관리자 인증 필요`

**Request Body**
```json
{
  "status": "RESOLVED",
  "remark": "처리 완료"
}
```

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "reportId": 1,
    "status": "RESOLVED",
    "processedAt": "2025-01-21T11:00:00Z"
  },
  "error": null
}
```

---

### 9.3 사용자 제재
```
POST /admin/sanctions
```
`🔒 관리자 인증 필요`

**Request Body**
```json
{
  "targetUserId": 10,
  "type": "BAN",
  "endDate": "2025-02-21T00:00:00Z",
  "remark": "규칙 위반으로 인한 7일 정지"
}
```

**Response** `201 Created`
```json
{
  "success": true,
  "data": {
    "sanctionId": 1,
    "targetUserId": 10,
    "type": "BAN",
    "endDate": "2025-02-21T00:00:00Z",
    "createdAt": "2025-01-21T10:00:00Z"
  },
  "error": null
}
```

---

### 9.4 제재 이력 조회
```
GET /admin/sanctions
```
`🔒 관리자 인증 필요`

**Query Parameters**
| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|-------|------|
| userId | Long | N | - | 특정 사용자 필터 |
| page | int | N | 0 | 페이지 번호 |
| size | int | N | 20 | 페이지 크기 |

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "sanctionId": 1,
        "targetUser": {
          "userId": 10,
          "displayName": "제재대상"
        },
        "type": "BAN",
        "endDate": "2025-02-21T00:00:00Z",
        "remark": "규칙 위반",
        "admin": {
          "adminId": 1,
          "displayName": "관리자"
        },
        "createdAt": "2025-01-21T10:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 50,
    "totalPages": 3,
    "hasNext": true,
    "hasPrevious": false
  },
  "error": null
}
```

---

### 9.5 IP 차단 목록 조회
```
GET /admin/ip-blocks
```
`🔒 관리자 인증 필요`

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "ipAddress": "192.168.1.100",
        "reason": "스팸 활동",
        "endDate": "2025-12-31T23:59:59Z",
        "createdAt": "2025-01-21T10:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 10,
    "totalPages": 1,
    "hasNext": false,
    "hasPrevious": false
  },
  "error": null
}
```

---

### 9.6 IP 차단 등록
```
POST /admin/ip-blocks
```
`🔒 관리자 인증 필요`

**Request Body**
```json
{
  "ipAddress": "192.168.1.100",
  "reason": "스팸 활동",
  "endDate": "2025-12-31T23:59:59Z"
}
```

**Response** `201 Created`
```json
{
  "success": true,
  "data": {
    "ipAddress": "192.168.1.100",
    "reason": "스팸 활동",
    "endDate": "2025-12-31T23:59:59Z"
  },
  "error": null
}
```

---

### 9.7 IP 차단 해제
```
DELETE /admin/ip-blocks/{ipAddress}
```
`🔒 관리자 인증 필요`

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "message": "IP 차단이 해제되었습니다."
  },
  "error": null
}
```

---

### 9.8 게시글 관리
```
PUT /admin/posts/{postId}
```
`🔒 관리자 인증 필요`

**Request Body**
```json
{
  "action": "delete",
  "reason": "규칙 위반"
}
```

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "postId": 100,
    "action": "delete",
    "processedAt": "2025-01-21T10:00:00Z"
  },
  "error": null
}
```

---

### 9.9 댓글 관리
```
PUT /admin/comments/{commentId}
```
`🔒 관리자 인증 필요`

**Request Body**
```json
{
  "action": "delete",
  "reason": "규칙 위반"
}
```

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "commentId": 50,
    "action": "delete",
    "processedAt": "2025-01-21T10:00:00Z"
  },
  "error": null
}
```

---

### 9.10 사용자 관리
```
PUT /admin/users/{userId}
```
`🔒 관리자 인증 필요`

**Request Body**
```json
{
  "status": "SUSPENDED"
}
```

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "userId": 10,
    "status": "SUSPENDED",
    "modifiedAt": "2025-01-21T10:00:00Z"
  },
  "error": null
}
```

---

### 9.11 게시판 관리
```
PUT /admin/boards/{boardId}
```
`🔒 관리자 인증 필요`

**Request Body**
```json
{
  "isActive": false,
  "sortOrder": 5
}
```

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "boardId": 1,
    "isActive": false,
    "sortOrder": 5
  },
  "error": null
}
```

---

### 9.12 활동 로그 조회
```
GET /admin/logs
```
`🔒 관리자 인증 필요`

**Query Parameters**
| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|-------|------|
| userId | Long | N | - | 사용자 필터 |
| actionType | String | N | - | 액션 타입 필터 |
| from | DateTime | N | - | 시작 일시 |
| to | DateTime | N | - | 종료 일시 |
| page | int | N | 0 | 페이지 번호 |
| size | int | N | 20 | 페이지 크기 |

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "logId": 1000,
        "user": {
          "userId": 5,
          "displayName": "사용자"
        },
        "actionType": "POST_CREATE",
        "targetId": 100,
        "ipAddress": "192.168.1.1",
        "details": {
          "boardId": 1,
          "title": "게시글 제목"
        },
        "createdAt": "2025-01-21T10:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 10000,
    "totalPages": 500,
    "hasNext": true,
    "hasPrevious": false
  },
  "error": null
}
```

---

### 9.13 전역 설정 조회
```
GET /admin/configs
```
`🔒 슈퍼 관리자 인증 필요`

**Response** `200 OK`
```json
{
  "success": true,
  "data": [
    {
      "configKey": "POINT_POST_CREATE",
      "configValue": "10",
      "description": "게시글 작성 시 포인트"
    },
    {
      "configKey": "POINT_COMMENT_CREATE",
      "configValue": "5",
      "description": "댓글 작성 시 포인트"
    }
  ],
  "error": null
}
```

---

### 9.14 전역 설정 수정
```
PUT /admin/configs/{configKey}
```
`🔒 슈퍼 관리자 인증 필요`

**Request Body**
```json
{
  "configValue": "15"
}
```

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "configKey": "POINT_POST_CREATE",
    "configValue": "15"
  },
  "error": null
}
```

---

### 9.15 관리자 목록 조회
```
GET /admin/admins
```
`🔒 슈퍼 관리자 인증 필요`

**Response** `200 OK`
```json
{
  "success": true,
  "data": [
    {
      "adminId": 1,
      "user": {
        "userId": 1,
        "displayName": "슈퍼관리자"
      },
      "role": "SUPER",
      "board": null,
      "isActive": true,
      "createdAt": "2025-01-01T00:00:00Z"
    },
    {
      "adminId": 2,
      "user": {
        "userId": 10,
        "displayName": "게시판관리자"
      },
      "role": "MODERATOR",
      "board": {
        "boardId": 1,
        "boardName": "자유게시판"
      },
      "isActive": true,
      "createdAt": "2025-01-15T00:00:00Z"
    }
  ],
  "error": null
}
```

---

### 9.16 관리자 권한 부여
```
POST /admin/admins
```
`🔒 슈퍼 관리자 인증 필요`

**Request Body**
```json
{
  "userId": 15,
  "role": "MODERATOR",
  "boardId": 2
}
```

**Response** `201 Created`
```json
{
  "success": true,
  "data": {
    "adminId": 3,
    "userId": 15,
    "role": "MODERATOR",
    "boardId": 2
  },
  "error": null
}
```

---

### 9.17 관리자 권한 해제
```
DELETE /admin/admins/{adminId}
```
`🔒 슈퍼 관리자 인증 필요`

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "message": "관리자 권한이 해제되었습니다."
  },
  "error": null
}
```

---

## 10. 기타

### 10.1 신고하기
```
POST /reports
```
`🔒 인증 필요`

**Request Body**
```json
{
  "targetType": "POST",
  "targetId": 100,
  "reasonType": "SPAM",
  "contents": "스팸 게시글입니다"
}
```

**Response** `201 Created`
```json
{
  "success": true,
  "data": {
    "reportId": 50,
    "message": "신고가 접수되었습니다."
  },
  "error": null
}
```

**에러 코드**
| 코드 | 설명 |
|-----|------|
| ALREADY_REPORTED | 이미 신고함 |
| INVALID_TARGET | 유효하지 않은 대상 |

---

### 10.2 파일 업로드
```
POST /files
```
`🔒 인증 필요`

**Request** `multipart/form-data`
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| file | File | Y | 업로드 파일 |

**Response** `201 Created`
```json
{
  "success": true,
  "data": {
    "fileId": 1,
    "originalName": "image.png",
    "storedName": "uuid-image.png",
    "fileUrl": "https://cdn.example.com/uuid-image.png",
    "fileSize": 102400,
    "mimeType": "image/png"
  },
  "error": null
}
```

**에러 코드**
| 코드 | 설명 |
|-----|------|
| FILE_TOO_LARGE | 파일 크기 초과 |
| INVALID_FILE_TYPE | 허용되지 않은 파일 형식 |

---

### 10.3 공통코드 조회
```
GET /codes/{typeCode}
```

**Response** `200 OK`
```json
{
  "success": true,
  "data": [
    { "code": "SPAM", "name": "스팸", "sortOrder": 1 },
    { "code": "ABUSE", "name": "욕설/비방", "sortOrder": 2 },
    { "code": "ADULT", "name": "음란물", "sortOrder": 3 }
  ],
  "error": null
}
```

---

### 10.4 광고 조회
```
GET /ads
```

**Query Parameters**
| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|-------|------|
| placement | String | Y | - | 광고 위치 (HEADER/SIDEBAR/CONTENT) |

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "adId": 1,
    "title": "광고 제목",
    "imageUrl": "https://...",
    "targetUrl": "https://...",
    "placement": "HEADER"
  },
  "error": null
}
```

---

### 10.5 광고 클릭
```
POST /ads/{adId}/click
```

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "targetUrl": "https://..."
  },
  "error": null
}
```

---

### 10.6 열람 기록 조회
```
GET /users/me/view-history
```
`🔒 인증 필요`

**Query Parameters**
| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|-------|------|
| page | int | N | 0 | 페이지 번호 |
| size | int | N | 20 | 페이지 크기 |

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "viewHistoryId": 1,
        "post": {
          "postId": 100,
          "title": "열람한 게시글",
          "boardName": "자유게시판"
        },
        "lastViewedAt": "2025-01-21T10:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 100,
    "totalPages": 5,
    "hasNext": true,
    "hasPrevious": false
  },
  "error": null
}
```

---

### 10.7 열람 기록 삭제
```
DELETE /users/me/view-history/{viewHistoryId}
```
`🔒 인증 필요`

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "message": "삭제되었습니다."
  },
  "error": null
}
```

---

### 10.8 열람 기록 전체 삭제
```
DELETE /users/me/view-history
```
`🔒 인증 필요`

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "message": "전체 삭제되었습니다."
  },
  "error": null
}
```

---

## API 요약

| 영역 | 엔드포인트 수 |
|-----|-------------|
| 회원 (Auth/Users) | 20개 |
| 게시판 (Boards) | 11개 |
| 게시글 (Posts) | 17개 |
| 댓글 (Comments) | 9개 |
| 알림 (Notifications) | 4개 |
| 쪽지 (Messages) | 6개 |
| 검색 (Search) | 6개 |
| 포인트/상점 (Points/Shop) | 6개 |
| 관리자 (Admin) | 17개 |
| 기타 (Etc) | 8개 |
| **총계** | **104개** |
