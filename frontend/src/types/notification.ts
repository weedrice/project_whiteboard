// 알림 관련 타입
export interface NotificationActor {
    userId: number;
    displayName: string;
    profileImageUrl?: string;
}

export interface Notification {
    notificationId: number;
    message: string;
    sourceType: 'POST' | 'COMMENT' | 'SYSTEM';
    sourceId: number;
    isRead: boolean;
    createdAt: string;
    actor: NotificationActor;
    targetUrl?: string; // Optional if needed
}
