import type { Component } from 'vue'
import { AtSign, Award, Bell, Heart, Mail, MessageCircle, Megaphone, Reply, ShieldAlert } from 'lucide-vue-next'
import type { Notification } from '@/types'

type NotificationType = Notification['notificationType']
type Translate = (key: string, ...args: unknown[]) => string

const LOCALIZED_MESSAGE_KEYS = new Set([
    'notification.comment.created',
    'notification.reply.created',
    'notification.comment.liked',
    'notification.post.liked',
    'notification.badge.awarded',
    'notification.scheduled.published',
    'notification.scheduled.failed',
    'notification.message.received',
    'notification.mention.created',
    'notification.keyword.matched',
    'notification.inquiry.created',
    'notification.inquiry.reopened',
    'notification.inquiry.replied',
    'notification.inquiry.closed',
    'notification.inquiry.autoClosed',
])

const ACTOR_NAME_MESSAGE_KEYS = new Set([
    'notification.comment.created',
    'notification.reply.created',
    'notification.comment.liked',
    'notification.post.liked',
    'notification.message.received',
    'notification.mention.created',
])

export function getNotificationMessage(
    notification: Pick<Notification, 'message' | 'messageKey' | 'messageParams' | 'actorLabelKey'>,
    t: Translate,
): string {
    if (!notification.messageKey || !LOCALIZED_MESSAGE_KEYS.has(notification.messageKey)) {
        return notification.message
    }

    const messageParams = [...(notification.messageParams ?? [])]
    if (notification.actorLabelKey
        && ACTOR_NAME_MESSAGE_KEYS.has(notification.messageKey)
        && messageParams.length > 0) {
        messageParams[0] = t(notification.actorLabelKey)
    }
    const translated = t(notification.messageKey, messageParams)
    return translated && translated !== notification.messageKey ? translated : notification.message
}

export function getNotificationActorDisplayName(
    notification: Pick<Notification, 'actorDisplayName' | 'actorLabelKey'>,
    t: Translate,
): string {
    return notification.actorDisplayName || (notification.actorLabelKey ? t(notification.actorLabelKey) : '')
}

export interface NotificationPresentation {
    icon: Component
    label: (t: Translate) => string
    tone: NotificationTone
}

export type NotificationTone = 'neutral' | 'danger' | 'info' | 'accent' | 'success' | 'warning'

const PRESENTATION_BY_TYPE: Record<NotificationType, NotificationPresentation> = {
    LIKE: {
        icon: Heart,
        label: (t) => t('notification.types.like'),
        tone: 'danger',
    },
    COMMENT: {
        icon: MessageCircle,
        label: (t) => t('notification.types.comment'),
        tone: 'info',
    },
    REPLY: {
        icon: Reply,
        label: (t) => t('notification.types.reply'),
        tone: 'info',
    },
    MENTION: {
        icon: AtSign,
        label: (t) => t('notification.types.mention'),
        tone: 'accent',
    },
    MESSAGE: {
        icon: Mail,
        label: (t) => t('notification.types.message'),
        tone: 'success',
    },
    SYSTEM: {
        icon: Megaphone,
        label: (t) => t('notification.types.system'),
        tone: 'success',
    },
    SANCTION: {
        icon: ShieldAlert,
        label: (t) => t('notification.types.sanction'),
        tone: 'warning',
    },
    KEYWORD: {
        icon: Bell,
        label: (t) => t('notification.types.keyword'),
        tone: 'accent',
    },
    BADGE: {
        icon: Award,
        label: (t) => t('notification.types.badge'),
        tone: 'accent',
    },
    INQUIRY: {
        icon: MessageCircle,
        label: (t) => t('notification.types.inquiry'),
        tone: 'info',
    },
}

const FALLBACK_PRESENTATION: NotificationPresentation = {
    icon: Bell,
    label: (t) => t('notification.types.default'),
    tone: 'neutral',
}

export function getNotificationPresentation(notification: Pick<Notification, 'notificationType'>): NotificationPresentation {
    return PRESENTATION_BY_TYPE[notification.notificationType] ?? FALLBACK_PRESENTATION
}

export function getNotificationTypeLabel(
    notification: Pick<Notification, 'notificationType'>,
    t: Translate,
): string {
    return getNotificationPresentation(notification).label(t)
}
