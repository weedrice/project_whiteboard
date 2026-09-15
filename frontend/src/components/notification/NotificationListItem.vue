<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import BaseBadge, { type BaseBadgeVariant } from '@/components/common/ui/BaseBadge.vue'
import {
  getNotificationActorDisplayName,
  getNotificationMessage,
  getNotificationPresentation,
  getNotificationTypeLabel,
  type NotificationTone,
} from '@/features/notifications/notificationPresentation'
import type { Notification } from '@/types'

const props = defineProps<{
  notification: Notification
  mode: 'dropdown' | 'page'
  timeText: string
}>()

const emit = defineEmits<{
  activate: [notification: Notification]
}>()

const { t } = useI18n()
const presentation = computed(() => getNotificationPresentation(props.notification))
const message = computed(() => getNotificationMessage(props.notification, t))
const actorName = computed(() => getNotificationActorDisplayName(props.notification, t))
const typeLabel = computed(() => getNotificationTypeLabel(props.notification, t))

const badgeVariantByTone: Record<NotificationTone, BaseBadgeVariant> = {
  neutral: 'gray',
  danger: 'danger',
  info: 'info',
  accent: 'accent',
  success: 'success',
  warning: 'warning',
}

const iconToneClass: Record<NotificationTone, string> = {
  neutral: 'nv-surface-muted nv-text-muted',
  danger: 'nv-status-danger',
  info: 'nv-status-info',
  accent: 'nv-accent-bg nv-accent-text',
  success: 'nv-status-success',
  warning: 'nv-status-warning',
}
</script>

<template>
  <button
    type="button"
    class="block w-full text-left transition duration-150 ease-in-out nv-hover-surface nv-press-surface"
    :class="[
      mode === 'dropdown' ? 'border-b nv-border px-4 py-3 last:border-0' : 'min-h-[48px] px-3 py-3 sm:px-6 sm:py-4',
      !notification.isRead && 'nv-unread-surface',
    ]"
    @click="emit('activate', notification)"
  >
    <div v-if="mode === 'dropdown'" class="flex items-start">
      <span class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full" :class="iconToneClass[presentation.tone]" aria-hidden="true">
        <component :is="presentation.icon" class="h-4 w-4" />
      </span>
      <span class="ml-3 min-w-0 flex-1">
        <span class="block text-sm font-medium nv-title">{{ actorName }}</span>
        <span class="block truncate text-sm nv-text-subtle">{{ message }}</span>
        <span class="mt-1 block text-xs nv-text-subtle">
          <BaseBadge class="mr-1" :variant="badgeVariantByTone[presentation.tone]" size="sm">
            {{ typeLabel }}
          </BaseBadge>
          {{ timeText }}
        </span>
      </span>
      <span v-if="!notification.isRead" class="ml-2 mt-1 h-2 w-2 shrink-0 rounded-full bg-[var(--nv-accent)]" aria-hidden="true" />
    </div>

    <div v-else class="flex items-center justify-between gap-3">
      <span class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full" :class="iconToneClass[presentation.tone]" aria-hidden="true">
        <component :is="presentation.icon" class="h-4 w-4" />
      </span>
      <span class="min-w-0 flex-1">
        <span class="mb-0.5 flex items-center justify-between gap-2">
          <span class="shrink-0 text-xs nv-text-subtle">{{ timeText }}</span>
          <BaseBadge class="shrink-0" :variant="badgeVariantByTone[presentation.tone]" size="sm">
            {{ typeLabel }}
          </BaseBadge>
        </span>
        <span class="line-clamp-2 block text-xs nv-text-subtle sm:text-sm">{{ message }}</span>
        <span v-if="notification.grouped" class="mt-1 block text-xs font-medium nv-accent-text">
          {{ t('notification.groupedCount', { count: notification.groupCount }) }}
        </span>
      </span>
    </div>
  </button>
</template>
