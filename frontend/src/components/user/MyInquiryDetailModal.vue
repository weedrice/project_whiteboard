<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
import PostContentView from '@/components/board/PostContentView.vue'
import CommentList from '@/components/comment/CommentList.vue'
import { formatDate } from '@/utils/date'

interface InquiryDetailPost {
  postId: number
  title: string
  contents?: string | null
  createdAt: string
}

defineProps<{
  isOpen: boolean
  post: InquiryDetailPost | null
  isLoading: boolean
  error?: string | null
  isDeleting: boolean
}>()

defineEmits<{
  (event: 'close'): void
  (event: 'delete-post'): void
}>()

const { t } = useI18n()
</script>

<template>
  <BaseModal :isOpen="isOpen" :title="t('user.inquiryDetail.title')" size="2xl" mobile-full @close="$emit('close')">
    <div class="space-y-4 p-2 sm:p-4">
      <div v-if="isLoading" class="flex items-center justify-center py-10">
        <BaseSkeleton width="100%" height="180px" />
      </div>

      <div v-else-if="error" class="rounded border px-4 py-3 text-sm nv-danger-surface">
        {{ error }}
      </div>

      <template v-else-if="post">
        <div class="space-y-2 border-b nv-border pb-3">
          <h2 class="text-lg font-semibold nv-title">{{ post.title }}</h2>
          <div class="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs nv-text-subtle">
            <span>{{ t('common.createdAt') }} {{ formatDate(post.createdAt) }}</span>
          </div>
        </div>

        <div class="max-h-[60vh] overflow-y-auto rounded-md nv-surface-muted p-4 text-sm">
          <PostContentView
            v-if="post.contents"
            class="break-words leading-6"
            :content="post.contents"
            :sandbox-title="post.title"
          />
          <div v-else>-</div>
        </div>

        <div class="border-t nv-border pt-4">
          <CommentList :postId="post.postId" boardUrl="inquiry" />
        </div>
      </template>
    </div>

    <template #footer>
      <div class="flex justify-end gap-2">
        <BaseButton type="button" variant="danger" :loading="isDeleting" @click="$emit('delete-post')">
          {{ t('common.delete') }}
        </BaseButton>
        <BaseButton type="button" variant="secondary" @click="$emit('close')">
          {{ t('common.close') }}
        </BaseButton>
      </div>
    </template>
  </BaseModal>
</template>
