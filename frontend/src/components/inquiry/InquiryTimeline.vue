<script setup lang="ts">
import type { InquiryMessage } from '@/types/inquiry'
import { formatDateTimeOrDash } from '@/utils/date'
import AuthenticatedInquiryImage from '@/components/inquiry/AuthenticatedInquiryImage.vue'
import { useI18n } from 'vue-i18n'

defineProps<{ messages: InquiryMessage[]; admin?: boolean }>()
const { t } = useI18n()

function typeLabel(type: InquiryMessage['messageType']) {
  return t(`inquiry.timeline.${type}`)
}
</script>

<template>
  <ol class="space-y-4" :aria-label="t('inquiry.timeline.label')">
    <li
      v-for="message in messages"
      :key="message.messageId"
      class="rounded-xl border nv-border p-4"
      :class="message.messageType === 'INTERNAL_NOTE' ? 'nv-status-warning' : 'nv-surface'"
    >
      <div class="flex flex-wrap items-center justify-between gap-2 text-sm">
        <strong>{{ typeLabel(message.messageType) }} · {{ message.authorName }}</strong>
        <time class="nv-text-muted">{{ formatDateTimeOrDash(message.createdAt) }}</time>
      </div>
      <p class="mt-3 whitespace-pre-wrap break-words text-sm leading-6">{{ message.content }}</p>
      <div v-if="message.attachments.length" class="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-3">
        <AuthenticatedInquiryImage v-for="attachment in message.attachments" :key="attachment.fileId" :attachment="attachment" />
      </div>
    </li>
  </ol>
</template>
