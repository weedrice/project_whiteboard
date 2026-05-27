<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import AdminModalContentState from '@/components/admin/AdminModalContentState.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import CommentList from '@/components/comment/CommentList.vue'
import type { AdminInquiryDetail } from '@/composables/useAdminInquiryPosts'

defineProps<{
  isOpen: boolean
  inquiry: AdminInquiryDetail | null | undefined
  loading: boolean
  fetching: boolean
  error: unknown
}>()

const emit = defineEmits<{
  close: []
}>()

const { t } = useI18n()
</script>

<template>
  <BaseModal :isOpen="isOpen" :title="t('admin.inquiries.detail.title')" size="2xl" mobile-full @close="emit('close')">
    <div class="space-y-4 p-2 sm:p-4">
      <AdminModalContentState
        :loading="loading || fetching"
        :error="error"
        :empty="!inquiry"
        empty-text=""
        :error-text="t('common.messages.loadFailed')"
      >
        <template #loading>
          <div class="flex items-center justify-center py-10">
            <BaseSpinner size="lg" />
          </div>
        </template>

        <template #error>
          <div
            class="rounded border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600 dark:border-red-900 dark:bg-red-950/30 dark:text-red-300"
          >
            {{ t('common.messages.loadFailed') }}
          </div>
        </template>

        <template v-if="inquiry">
          <div class="space-y-2 border-b border-gray-200 pb-3 dark:border-gray-700">
            <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">{{ inquiry.title }}</h2>
            <div class="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-gray-500 dark:text-gray-400">
              <span>{{ t('common.author') }} {{ inquiry.authorName }}</span>
              <span>{{ t('common.createdAt') }} {{ inquiry.createdAtText }}</span>
            </div>
          </div>

          <div class="max-h-[60vh] overflow-y-auto rounded-md bg-gray-50 p-4 text-sm text-gray-800 dark:bg-gray-900/30 dark:text-gray-200">
            <div v-if="inquiry.contentsHtml" class="break-words leading-6" v-html="inquiry.contentsHtml" />
            <div v-else>-</div>
          </div>

          <div class="border-t border-gray-200 pt-4 dark:border-gray-700">
            <CommentList :postId="inquiry.id" boardUrl="inquiry" />
          </div>
        </template>
      </AdminModalContentState>
    </div>

    <template #footer>
      <div class="flex justify-end">
        <BaseButton type="button" variant="secondary" @click="emit('close')">{{ t('common.close') }}</BaseButton>
      </div>
    </template>
  </BaseModal>
</template>
