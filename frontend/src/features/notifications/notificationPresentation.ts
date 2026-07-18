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
    labelKey: string
    badgeClass: string
    iconClass: string
}

const PRESENTATION_BY_TYPE: Record<NotificationType, NotificationPresentation> = {
    LIKE: {
        icon: Heart,
        labelKey: 'notification.types.like',
        badgeClass: 'notification-badge-like',
        iconClass: 'notification-icon-like',
    },
    COMMENT: {
        icon: MessageCircle,
        labelKey: 'notification.types.comment',
        badgeClass: 'notification-badge-comment',
        iconClass: 'notification-icon-comment',
    },
    REPLY: {
        icon: Reply,
        labelKey: 'notification.types.reply',
        badgeClass: 'notification-badge-reply',
        iconClass: 'notification-icon-reply',
    },
    MENTION: {
        icon: AtSign,
        labelKey: 'notification.types.mention',
        badgeClass: 'notification-badge-mention',
        iconClass: 'notification-icon-mention',
    },
    MESSAGE: {
        icon: Mail,
        labelKey: 'notification.types.message',
        badgeClass: 'notification-badge-message',
        iconClass: 'notification-icon-message',
    },
    SYSTEM: {
        icon: Megaphone,
        labelKey: 'notification.types.system',
        badgeClass: 'notification-badge-system',
        iconClass: 'notification-icon-system',
    },
    SANCTION: {
        icon: ShieldAlert,
        labelKey: 'notification.types.sanction',
        badgeClass: 'notification-badge-sanction',
        iconClass: 'notification-icon-sanction',
    },
    KEYWORD: {
        icon: Bell,
        labelKey: 'notification.types.keyword',
        badgeClass: 'notification-badge-keyword',
        iconClass: 'notification-icon-keyword',
    },
    BADGE: {
        icon: Award,
        labelKey: 'notification.types.badge',
        badgeClass: 'notification-badge-badge',
        iconClass: 'notification-icon-badge',
    },
}

const FALLBACK_PRESENTATION: NotificationPresentation = {
    icon: Bell,
    labelKey: 'notification.types.default',
    badgeClass: 'notification-badge-default',
    iconClass: 'notification-icon-default',
}

export function getNotificationPresentation(notification: Pick<Notification, 'notificationType'>): NotificationPresentation {
    return PRESENTATION_BY_TYPE[notification.notificationType] ?? FALLBACK_PRESENTATION
}
