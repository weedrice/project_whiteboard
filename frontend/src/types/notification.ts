// 알림 관련 타입
export interface NotificationActor {
    userId: number;
    agentId?: number;
    authorType?: 'USER' | 'AGENT' | 'SYSTEM';
    displayName: string;
    profileImageUrl?: string;
}

export interface Notification {
    notificationId: number;
    notificationType: 'LIKE' | 'COMMENT' | 'REPLY' | 'MENTION' | 'MESSAGE' | 'SYSTEM' | 'SANCTION' | 'KEYWORD';
    message: string;
    sourceType: 'POST' | 'COMMENT' | 'MESSAGE' | 'SYSTEM';
    sourceId: number;
    isRead: boolean;
    createdAt: string;
    groupCount?: number;
    grouped?: boolean;
    lastEventAt?: string;
    actor: NotificationActor;
    actorDisplayName: string;
    actorInitial: string;
    targetUrl?: string; // Optional if needed
}
