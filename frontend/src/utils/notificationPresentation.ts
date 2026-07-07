import type { Component } from 'vue'
import { AtSign, Bell, Heart, Mail, MessageCircle, Megaphone, Reply, ShieldAlert } from 'lucide-vue-next'
import type { Notification } from '@/types'

type NotificationType = Notification['notificationType']

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
