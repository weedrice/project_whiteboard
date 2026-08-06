<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { ModerationAuditLog } from '@/types/admin'
import type { SupportedLocale } from '@/locales/types'
import { formatDateTimeOrDash } from '@/utils/date'

withDefaults(defineProps<{
  audits: ModerationAuditLog[]
  caption: string
  emptyText: string
  showBoardName?: boolean
  showBoardUrl?: boolean
}>(), {
  showBoardName: false,
  showBoardUrl: false,
})

const { t, locale } = useI18n()

const actorLabel = (audit: ModerationAuditLog) => {
  if (audit.actorType === 'SYSTEM') return t('notification.actors.system')
  return audit.actorDisplayName || (audit.actorUserId ? `#${audit.actorUserId}` : '-')
}

const actionLabel = (audit: ModerationAuditLog) => {
  const key = `admin.dashboard.auditActions.${audit.action}`
  const translated = t(key)
  return translated === key ? audit.action : translated
}
const targetLabel = (audit: ModerationAuditLog) => {
  const key = `admin.dashboard.auditTargets.${audit.targetType}`
  const translated = t(key)
  return `${translated === key ? audit.targetType : translated} #${audit.targetId}`
}
const formattedDate = (dateString: string) => formatDateTimeOrDash(dateString, locale.value as SupportedLocale)
</script>

<template>
  <div v-if="audits.length > 0" class="overflow-x-auto" role="region" :aria-label="caption" tabindex="0">
    <table class="w-full min-w-[48rem] divide-y divide-[var(--nv-line)] text-sm">
      <caption class="sr-only">{{ caption }}</caption>
      <thead class="nv-surface-muted nv-text-subtle">
        <tr>
          <th scope="col" class="px-4 py-3 text-left font-medium">{{ t('admin.dashboard.auditAction') }}</th>
          <th scope="col" class="px-4 py-3 text-left font-medium">{{ t('admin.dashboard.auditActor') }}</th>
          <th scope="col" class="px-4 py-3 text-left font-medium">{{ t('admin.dashboard.auditTarget') }}</th>
          <th v-if="showBoardName" scope="col" class="px-4 py-3 text-left font-medium">
            {{ t('admin.dashboard.auditBoardName') }}
          </th>
          <th v-if="showBoardUrl" scope="col" class="px-4 py-3 text-left font-medium">
            {{ t('admin.dashboard.auditBoardUrl') }}
          </th>
          <th scope="col" class="px-4 py-3 text-left font-medium">{{ t('admin.dashboard.auditReason') }}</th>
          <th scope="col" class="px-4 py-3 text-left font-medium">{{ t('admin.dashboard.auditCreatedAt') }}</th>
        </tr>
      </thead>
      <tbody class="divide-y divide-[var(--nv-line)]">
        <tr v-for="audit in audits" :key="audit.auditId">
          <td class="px-4 py-3 font-medium nv-title">{{ actionLabel(audit) }}</td>
          <td class="px-4 py-3 nv-text-subtle">{{ actorLabel(audit) }}</td>
          <td class="px-4 py-3 nv-text-subtle">{{ targetLabel(audit) }}</td>
          <td v-if="showBoardName" class="px-4 py-3 nv-text-subtle">{{ audit.boardName || '-' }}</td>
          <td v-if="showBoardUrl" class="px-4 py-3 nv-text-subtle">{{ audit.boardUrl || '-' }}</td>
          <td class="px-4 py-3 nv-text-subtle">{{ audit.reason || '-' }}</td>
          <td class="px-4 py-3 nv-text-subtle">{{ formattedDate(audit.createdAt) }}</td>
        </tr>
      </tbody>
    </table>
  </div>
  <p v-else class="px-4 py-5 text-center text-sm nv-text-subtle" role="status" aria-live="polite">{{ emptyText }}</p>
</template>
