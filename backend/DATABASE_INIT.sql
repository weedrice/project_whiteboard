-- 현재 시각을 DATETIME 컬럼에 기록하기 위해 NOW() 사용
INSERT INTO common_codes (type_code, type_name, description, created_at, modified_at) VALUES
('NOTIFICATION_TYPE', '알림 유형', '사용자에게 발송되는 알림의 종류', NOW(), NOW()),
('ACTION_TYPE', '활동 기록 유형', '사용자의 서비스 활동 내역 종류', NOW(), NOW()),
('REPORT_REASON', '신고 사유 코드', '게시글, 댓글 등에 대한 신고 이유', NOW(), NOW()),
('REPORT_STATUS', '신고 처리 상태', '신고 건의 처리 진행 상태', NOW(), NOW()),
('SANCTION_TYPE', '제재 유형 코드', '사용자에게 가해지는 제재의 종류', NOW(), NOW()),
('ADMIN_ROLE', '관리자 역할 코드', '관리자 계정의 권한 역할', NOW(), NOW()),
('ITEM_TYPE', '상점 아이템 유형', '상점에서 판매되는 아이템의 종류', NOW(), NOW());

INSERT INTO common_codes (type_code, type_name, description, created_at, modified_at) VALUES
('RELATED_TYPE', '관련 객체 타입', '파일, 포인트 이력 등이 참조하는 객체 타입 (POST, COMMENT, USER 등)', NOW(), NOW()),
('RANKING_TYPE', '랭킹 유형', '인기 게시글 캐시의 랭킹 산정 기준', NOW(), NOW()),
('POINT_CHANGE_TYPE', '포인트 변동 유형', '포인트 획득/사용 구분', NOW(), NOW()),
('VERSION_TYPE', '게시글 버전 유형', '게시글 이력의 생성/수정/삭제 구분', NOW(), NOW()),
('TARGET_TYPE', '신고 대상 타입', '신고 대상이 되는 객체 타입 (reports.target_type)', NOW(), NOW());

-- common_code_details의 id 컬럼은 시퀀스를 사용하지 않고 명시적으로 BIGINT 값을 삽입합니다.
INSERT INTO common_code_details (id, type_code, code_value, code_name, sort_order, is_active, created_at, modified_at) VALUES
-- ----------------------------------------------------------------------------------------------------------------------------------
-- NOTIFICATION_TYPE (알림 유형)
(1,  'NOTIFICATION_TYPE', 'POST_COMMENT', '댓글 알림', 10, 'Y', NOW(), NOW()),
(2,  'NOTIFICATION_TYPE', 'REPLY_COMMENT', '대댓글 알림', 20, 'Y', NOW(), NOW()),
(3,  'NOTIFICATION_TYPE', 'USER_MENTION', '멘션 알림', 30, 'Y', NOW(), NOW()),
(4,  'NOTIFICATION_TYPE', 'ADMIN_NOTICE', '관리자 공지', 40, 'Y', NOW(), NOW()),

-- ACTION_TYPE (활동 기록 유형)
(5,  'ACTION_TYPE', 'LOGIN', '로그인', 10, 'Y', NOW(), NOW()),
(6,  'ACTION_TYPE', 'POST_CREATE', '게시글 작성', 20, 'Y', NOW(), NOW()),
(7,  'ACTION_TYPE', 'POST_DELETE', '게시글 삭제', 30, 'Y', NOW(), NOW()),
(8,  'ACTION_TYPE', 'COMMENT_WRITE', '댓글 작성', 40, 'Y', NOW(), NOW()),
(9,  'ACTION_TYPE', 'VIEW_POST', '게시글 조회', 50, 'Y', NOW(), NOW()),

-- REPORT_STATUS (신고 처리 상태)
(10, 'REPORT_STATUS', 'PENDING', '대기', 10, 'Y', NOW(), NOW()),
(11, 'REPORT_STATUS', 'PROCESSING', '처리 중', 20, 'Y', NOW(), NOW()),
(12, 'REPORT_STATUS', 'RESOLVED', '처리 완료', 30, 'Y', NOW(), NOW()),
(13, 'REPORT_STATUS', 'REJECTED', '반려', 40, 'Y', NOW(), NOW()),

-- SANCTION_TYPE (제재 유형 코드)
(14, 'SANCTION_TYPE', 'TEMP_BAN', '임시 정지', 10, 'Y', NOW(), NOW()),
(15, 'SANCTION_TYPE', 'PERM_BAN', '영구 정지', 20, 'Y', NOW(), NOW()),
(16, 'SANCTION_TYPE', 'POST_DELETE', '게시글 강제 삭제', 30, 'Y', NOW(), NOW()),
(17, 'SANCTION_TYPE', 'COMMENT_HIDE', '댓글 숨김 처리', 40, 'Y', NOW(), NOW()),

-- ADMIN_ROLE (관리자 역할 코드)
(18, 'ADMIN_ROLE', 'GLOBAL_ADMIN', '전역 관리자', 10, 'Y', NOW(), NOW()),
(19, 'ADMIN_ROLE', 'BOARD_MANAGER', '게시판 관리자', 20, 'Y', NOW(), NOW()),
(20, 'ADMIN_ROLE', 'SANCTION_STAFF', '제재 담당자', 30, 'Y', NOW(), NOW()),

-- REPORT_REASON (신고 사유 코드) - ID 21부터 시작
(21, 'REPORT_REASON', 'SPAM', '광고/도배', 10, 'Y', NOW(), NOW()),
(22, 'REPORT_REASON', 'HATE_SPEECH', '욕설/혐오 발언', 20, 'Y', NOW(), NOW()),
(23, 'REPORT_REASON', 'PORNOGRAPHY', '음란물/선정성', 30, 'Y', NOW(), NOW()),
(24, 'REPORT_REASON', 'ILLEGAL_ADS', '불법 광고/거래', 40, 'Y', NOW(), NOW()),
(25, 'REPORT_REASON', 'PERSONAL_INFO', '개인 정보 유출', 50, 'Y', NOW(), NOW()),

-- ITEM_TYPE (상점 아이템 유형) - ID 26부터 시작
(26, 'ITEM_TYPE', 'ICON', '프로필 아이콘', 10, 'Y', NOW(), NOW()),
(27, 'ITEM_TYPE', 'TITLE_BADGE', '칭호/뱃지', 20, 'Y', NOW(), NOW()),
(28, 'ITEM_TYPE', 'NAME_COLOR', '닉네임 색상 변경권', 30, 'Y', NOW(), NOW()),
(29, 'ITEM_TYPE', 'TICKET', '임시 이용권', 40, 'Y', NOW(), NOW()),

-- ACTION_TYPE (활동 기록 유형) - ID 30부터 시작
(30, 'ACTION_TYPE', 'LOGOUT', '로그아웃', 60, 'Y', NOW(), NOW()),
(31, 'ACTION_TYPE', 'POST_UPDATE', '게시글 수정', 70, 'Y', NOW(), NOW()),
(32, 'ACTION_TYPE', 'COMMENT_UPDATE', '댓글 수정', 80, 'Y', NOW(), NOW()),
(33, 'ACTION_TYPE', 'POST_LIKE', '게시글 좋아요', 90, 'Y', NOW(), NOW()),
(34, 'ACTION_TYPE', 'COMMENT_LIKE', '댓글 좋아요', 100, 'Y', NOW(), NOW()),
(35, 'ACTION_TYPE', 'SCRAP', '게시글 스크랩', 110, 'Y', NOW(), NOW()),
(36, 'ACTION_TYPE', 'FAVORITE_BOARD', '게시판 즐겨찾기', 120, 'Y', NOW(), NOW()),
(37, 'ACTION_TYPE', 'POINT_EARN', '포인트 획득', 130, 'Y', NOW(), NOW()),
(38, 'ACTION_TYPE', 'ITEM_PURCHASE', '아이템 구매', 140, 'Y', NOW(), NOW()),
(39, 'ACTION_TYPE', 'FILE_UPLOAD', '파일 업로드', 150, 'Y', NOW(), NOW()),
(40, 'ACTION_TYPE', 'USER_BLOCK', '사용자 차단', 160, 'Y', NOW(), NOW()),
(41, 'ACTION_TYPE', 'PROFILE_UPDATE', '프로필 수정', 170, 'Y', NOW(), NOW());

INSERT INTO common_code_details (id, type_code, code_value, code_name, sort_order, is_active, created_at, modified_at) VALUES
-- ----------------------------------------------------------------------------------------------------------------------------------
-- RELATED_TYPE (첨부파일, 포인트, 제재 등에서 사용되는 객체 타입) - ID 42부터 시작
(42, 'RELATED_TYPE', 'POST', '게시글', 10, 'Y', NOW(), NOW()),
(43, 'RELATED_TYPE', 'COMMENT', '댓글', 20, 'Y', NOW(), NOW()),
(44, 'RELATED_TYPE', 'USER', '사용자', 30, 'Y', NOW(), NOW()),
(45, 'RELATED_TYPE', 'DRAFT', '임시 저장', 40, 'Y', NOW(), NOW()),
(46, 'RELATED_TYPE', 'FILE', '첨부 파일', 50, 'Y', NOW(), NOW()),
(47, 'RELATED_TYPE', 'ITEM', '상점 아이템', 60, 'Y', NOW(), NOW()),

-- RANKING_TYPE (인기글 캐시의 랭킹 유형) - ID 48부터 시작
(48, 'RANKING_TYPE', 'DAILY', '일간 랭킹', 10, 'Y', NOW(), NOW()),
(49, 'RANKING_TYPE', 'WEEKLY', '주간 랭킹', 20, 'Y', NOW(), NOW()),
(50, 'RANKING_TYPE', 'MONTHLY', '월간 랭킹', 30, 'Y', NOW(), NOW()),
(51, 'RANKING_TYPE', 'REALTIME', '실시간 랭킹', 40, 'Y', NOW(), NOW()),

-- POINT_CHANGE_TYPE (포인트 변동 유형) - ID 52부터 시작
(52, 'POINT_CHANGE_TYPE', 'EARN', '포인트 획득', 10, 'Y', NOW(), NOW()),
(53, 'POINT_CHANGE_TYPE', 'SPEND', '포인트 사용', 20, 'Y', NOW(), NOW()),
(54, 'POINT_CHANGE_TYPE', 'ADMIN_ADJ', '관리자 조정', 30, 'Y', NOW(), NOW()),

-- VERSION_TYPE (게시글 버전 관리 유형) - ID 55부터 시작
(55, 'VERSION_TYPE', 'CREATE', '최초 작성', 10, 'Y', NOW(), NOW()),
(56, 'VERSION_TYPE', 'UPDATE', '수정', 20, 'Y', NOW(), NOW()),
(57, 'VERSION_TYPE', 'DELETE', '삭제', 30, 'Y', NOW(), NOW()),

-- TARGET_TYPE (신고 대상 타입) - ID 58부터 시작
(58, 'TARGET_TYPE', 'POST', '게시글', 10, 'Y', NOW(), NOW()),
(59, 'TARGET_TYPE', 'COMMENT', '댓글', 20, 'Y', NOW(), NOW()),
(60, 'TARGET_TYPE', 'USER', '사용자', 30, 'Y', NOW(), NOW());