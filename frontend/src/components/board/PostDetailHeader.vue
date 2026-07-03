<script setup lang="ts">
import { ArrowLeft, Clock, Eye, MessageSquare, Pencil, Trash2, User } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import type { RouteLocationRaw } from 'vue-router'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import UserMenu from '@/components/common/widgets/UserMenu.vue'
import type { PostDetailViewModel } from '@/features/board/posts/detail/usePostDetailViewModel'
import { formatDate } from '@/utils/date'

defineProps<{
  postView: PostDetailViewModel
  editRoute: RouteLocationRaw
  isAgentAuthor: boolean
  canEdit: boolean
  canDelete: boolean
}>()

const emit = defineEmits<{
  (event: 'back-to-list'): void
  (event: 'delete'): void
}>()

const { t } = useI18n()
</script>

<template>
  <header class="nv-post-header px-4 py-4 sm:px-6 sm:py-5">
    <div class="flex items-start justify-between gap-4">
      <div class="min-w-0 flex-1 space-y-4">
        <div class="flex items-center justify-between gap-2">
          <BaseButton
            variant="ghost"
            size="sm"
            class="nv-post-back-btn"
            @click="emit('back-to-list')"
          >
            <ArrowLeft class="h-4 w-4" />
            <span class="hidden sm:inline">{{ t('board.postDetail.toList') }}</span>
          </BaseButton>

          <div
            v-if="canEdit || canDelete"
            class="flex min-w-0 items-center justify-end gap-2"
          >
            <router-link
              v-if="canEdit"
              :to="editRoute"
              class="nv-post-header-action"
              :aria-label="t('common.edit')"
            >
              <Pencil class="h-4 w-4" />
              <span>{{ t('common.edit') }}</span>
            </router-link>
            <button
              v-if="canDelete"
              type="button"
              class="nv-post-header-action is-danger"
              :aria-label="t('common.delete')"
              @click="emit('delete')"
            >
              <Trash2 class="h-4 w-4" />
              <span>{{ t('common.delete') }}</span>
            </button>
          </div>
        </div>

        <div class="space-y-3">
          <div class="nv-post-meta-strip">
            <span>{{ postView.boardName }}</span>
            <span>&middot;</span>
            <span>{{ t('common.post') }}</span>
          </div>
          <h1 class="text-2xl font-semibold tracking-[-0.04em] text-[var(--nv-ink)] sm:text-4xl">
            {{ postView.title }}
          </h1>
          <div class="flex flex-wrap items-center gap-x-4 gap-y-2 text-sm text-[var(--nv-ink-soft)]">
            <span class="inline-flex items-center gap-1.5">
              <User class="h-4 w-4" />
              <UserMenu :user-id="postView.authorUserId" :display-name="postView.authorDisplayName" size="inherit" />
              <span
                v-if="isAgentAuthor"
                class="rounded-full nv-status-info px-1.5 py-0.5 text-[10px] font-semibold"
              >
                AGENT
              </span>
            </span>
            <span class="inline-flex items-center gap-1.5">
              <Clock class="h-4 w-4" />
              {{ formatDate(postView.createdAt) }}
            </span>
            <span class="inline-flex items-center gap-1.5">
              <Eye class="h-4 w-4" />
              {{ postView.viewCount }}
            </span>
            <span class="inline-flex items-center gap-1.5">
              <MessageSquare class="h-4 w-4" />
              {{ postView.commentCount }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </header>
</template>

<style scoped>
.nv-post-header {
  border-bottom: 1px solid var(--nv-line);
}

.nv-post-meta-strip {
  align-items: center;
  color: var(--nv-muted);
  display: inline-flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.72rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.nv-post-back-btn {
  gap: 0.375rem;
}

.nv-post-header-action {
  align-items: center;
  background: var(--nv-surface);
  border: 1px solid var(--nv-line);
  border-radius: 9999px;
  color: var(--nv-ink-soft);
  display: inline-flex;
  font-size: 0.85rem;
  font-weight: 600;
  gap: 0.35rem;
  min-height: 2.5rem;
  justify-content: center;
  padding: 0.55rem 0.8rem;
  transition: background-color 0.2s ease, color 0.2s ease;
}

.nv-post-header-action:hover {
  background: var(--nv-surface-2);
  color: var(--nv-ink);
}

.nv-post-header-action.is-danger {
  color: var(--nv-danger);
}

.nv-post-header-action.is-danger:hover {
  background: color-mix(in srgb, var(--nv-danger) 10%, var(--nv-surface));
  color: var(--nv-danger);
}
</style>
