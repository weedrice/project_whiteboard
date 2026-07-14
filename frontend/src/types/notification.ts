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
    notificationType: 'LIKE' | 'COMMENT' | 'REPLY' | 'MENTION' | 'MESSAGE' | 'SYSTEM' | 'SANCTION' | 'KEYWORD' | 'BADGE';
    message: string;
    messageKey?: string;
    messageParams?: string[];
    sourceType: 'POST' | 'COMMENT' | 'MESSAGE' | 'SYSTEM';
    sourceId: number;
    isRead: boolean;
    createdAt: string;
    groupCount?: number;
    grouped?: boolean;
    lastEventAt?: string;
    actor: NotificationActor;
    actorDisplayName: string;
    actorLabelKey?: 'notification.actors.system' | 'notification.actors.unknown';
    actorInitial: string;
    targetUrl?: string; // Optional if needed
}
