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

const optionalBoardColumnCount = computed(() => (
  Number(props.showBoardName) + Number(props.showBoardUrl)
))
const columnWidths = computed(() => {
  if (optionalBoardColumnCount.value === 2) {
    return { action: '13%', actor: '13%', target: '13%', board: '13%', reason: '20%', createdAt: '15%' }
  }
  if (optionalBoardColumnCount.value === 1) {
    return { action: '15%', actor: '15%', target: '15%', board: '17%', reason: '22%', createdAt: '16%' }
  }
  return { action: '18%', actor: '18%', target: '18%', board: '0%', reason: '27%', createdAt: '19%' }
})

const columns = computed<TableColumn[]>(() => [
  { key: 'action', label: t('admin.dashboard.auditAction'), width: columnWidths.value.action },
  { key: 'actor', label: t('admin.dashboard.auditActor'), width: columnWidths.value.actor },
  { key: 'target', label: t('admin.dashboard.auditTarget'), width: columnWidths.value.target },
  ...(props.showBoardName
    ? [{ key: 'boardName', label: t('admin.dashboard.auditBoardName'), width: columnWidths.value.board }]
    : []),
  ...(props.showBoardUrl
    ? [{ key: 'boardUrl', label: t('admin.dashboard.auditBoardUrl'), width: columnWidths.value.board }]
    : []),
  { key: 'reason', label: t('admin.dashboard.auditReason'), width: columnWidths.value.reason },
  { key: 'createdAt', label: t('admin.dashboard.auditCreatedAt'), width: columnWidths.value.createdAt },
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
    appearance="embedded"
    min-width-class="min-w-[48rem]"
  >
    <template #cell-action="{ item }">
      <span class="block truncate font-medium nv-title" :title="actionLabel(item)">{{ actionLabel(item) }}</span>
    </template>
    <template #cell-actor="{ item }">
      <span class="block truncate" :title="actorLabel(item)">{{ actorLabel(item) }}</span>
    </template>
    <template #cell-target="{ item }">
      <span class="block truncate" :title="targetLabel(item)">{{ targetLabel(item) }}</span>
    </template>
    <template v-if="showBoardName" #cell-boardName="{ item }">
      <span class="block truncate" :title="item.boardName || undefined">{{ item.boardName || '-' }}</span>
    </template>
    <template v-if="showBoardUrl" #cell-boardUrl="{ item }">
      <span class="block truncate" :title="item.boardUrl || undefined">{{ item.boardUrl || '-' }}</span>
    </template>
    <template #cell-reason="{ item }">
      <span class="block whitespace-normal break-words" :title="item.reason || undefined">{{ item.reason || '-' }}</span>
    </template>
    <template #cell-createdAt="{ item }">{{ formattedDate(item.createdAt) }}</template>
  </BaseTable>
</template>
