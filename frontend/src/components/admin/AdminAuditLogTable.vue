<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import BaseTable from '@/components/common/ui/BaseTable.vue'
import type { TableColumn } from '@/components/common/ui/baseTableModel'
import type { ModerationAuditLog } from '@/types/admin'
import type { SupportedLocale } from '@/locales/types'
import { formatDateTimeOrDash } from '@/utils/date'

const props = withDefaults(defineProps<{
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

const columns = computed<TableColumn[]>(() => [
  { key: 'action', label: t('admin.dashboard.auditAction') },
  { key: 'actor', label: t('admin.dashboard.auditActor') },
  { key: 'target', label: t('admin.dashboard.auditTarget') },
  ...(props.showBoardName
    ? [{ key: 'boardName', label: t('admin.dashboard.auditBoardName') }]
    : []),
  ...(props.showBoardUrl
    ? [{ key: 'boardUrl', label: t('admin.dashboard.auditBoardUrl') }]
    : []),
  { key: 'reason', label: t('admin.dashboard.auditReason') },
  { key: 'createdAt', label: t('admin.dashboard.auditCreatedAt') },
])

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
  <BaseTable
    :columns="columns"
    :items="audits"
    :caption="caption"
    :scroll-label="caption"
    :empty-text="emptyText"
    row-key="auditId"
    density="compact"
    :shadow="false"
    min-width-class="min-w-[48rem]"
  >
    <template #cell-action="{ item }">
      <span class="font-medium nv-title">{{ actionLabel(item) }}</span>
    </template>
    <template #cell-actor="{ item }">{{ actorLabel(item) }}</template>
    <template #cell-target="{ item }">{{ targetLabel(item) }}</template>
    <template v-if="showBoardName" #cell-boardName="{ item }">{{ item.boardName || '-' }}</template>
    <template v-if="showBoardUrl" #cell-boardUrl="{ item }">{{ item.boardUrl || '-' }}</template>
    <template #cell-reason="{ item }">{{ item.reason || '-' }}</template>
    <template #cell-createdAt="{ item }">{{ formattedDate(item.createdAt) }}</template>
  </BaseTable>
</template>
