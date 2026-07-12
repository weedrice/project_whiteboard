<script setup lang="ts">
import type { RouteLocationRaw } from 'vue-router'
import { ShieldCheck, User } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import UserMenu from '@/components/common/widgets/UserMenu.vue'
import type { BoardDetail } from '@/types/board'
import { getOptimizedBoardIconUrl, handleImageError } from '@/utils/image'
import { encodePathSegment } from '@/utils/urlPath'

defineProps<{
  board: BoardDetail
  canWrite: boolean
  isAuthenticated: boolean
  isSubscribePending: boolean
  buildBoardListRoute: () => RouteLocationRaw
}>()

const emit = defineEmits<{
  (e: 'subscribe'): void
}>()

const { t } = useI18n()
</script>

<template>
  <section class="nv-board-panel nv-board-header-panel p-4 sm:p-6">
    <div class="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
      <div class="flex min-w-0 flex-1 items-start gap-3 sm:gap-4">
        <router-link :to="buildBoardListRoute()" class="nv-board-icon-wrap flex-shrink-0">
          <img
            v-if="board.iconUrl"
            :src="getOptimizedBoardIconUrl(board.iconUrl, 96)"
            class="nv-board-icon"
            alt=""
            @error="handleImageError($event)"
          />
          <div v-else class="nv-board-icon-fallback">
            <span>{{ board.boardName?.[0] || '#' }}</span>
          </div>
        </router-link>

        <div class="min-w-0 flex-1 space-y-2.5">
          <div class="flex flex-wrap items-center gap-2">
            <router-link :to="buildBoardListRoute()" class="inline-flex min-w-0 max-w-full items-center gap-2">
              <h1 class="truncate text-2xl font-semibold tracking-[-0.04em] text-[var(--nv-ink)] sm:text-3xl">
                {{ board.boardName }}
              </h1>
            </router-link>

            <BaseButton
              v-if="isAuthenticated"
              size="sm"
              :variant="board.isSubscribed ? 'secondary' : 'primary'"
              :disabled="isSubscribePending"
              :aria-busy="isSubscribePending ? 'true' : 'false'"
              class="nv-board-subscribe-btn"
              @click="emit('subscribe')"
            >
              {{ board.isSubscribed ? $t('common.unsubscribe') : $t('common.subscribe') }}
            </BaseButton>
          </div>

          <div class="flex flex-wrap items-center gap-x-4 gap-y-2 text-sm text-[var(--nv-ink-soft)]">
            <span class="inline-flex items-center gap-1.5">
              <User class="h-4 w-4" />
              {{ $t('common.subscribers') }} {{ board.subscriberCount || 0 }}
            </span>
            <span class="inline-flex items-center gap-1.5">
              <span class="font-medium text-[var(--nv-ink)]">{{ t('board.detail.defaultAdminName') }}</span>
              <UserMenu
                v-if="board.adminUserId"
                :user-id="board.adminUserId"
                :display-name="board.adminDisplayName || t('board.detail.defaultAdminName')"
                size="inherit"
              />
              <span v-else>{{ board.adminDisplayName || t('board.detail.defaultAdminName') }}</span>
            </span>
          </div>

          <p class="max-w-3xl text-sm leading-6 text-[var(--nv-ink-soft)] sm:text-[15px]">
            {{ board.description || t('board.list.noDesc') }}
          </p>
        </div>
      </div>

      <div class="flex w-full flex-col gap-2 lg:w-auto lg:min-w-[7rem] lg:items-end lg:self-stretch">
        <div class="flex gap-2 lg:justify-end">
          <router-link
            v-if="board.isAdmin"
            :to="`/board/${encodePathSegment(board.boardUrl)}/edit`"
            class="nv-board-manage-btn"
          >
            <ShieldCheck class="h-4 w-4" />
            {{ $t('common.manage') }}
          </router-link>
        </div>

        <router-link
          v-if="canWrite"
          :to="`/board/${encodePathSegment(board.boardUrl)}/write`"
          class="nv-board-write-btn lg:mt-auto"
        >
          {{ $t('common.write') }}
        </router-link>
      </div>
    </div>
  </section>
</template>

<style scoped>
.nv-board-panel {
  background: color-mix(in srgb, var(--nv-surface) 94%, transparent);
  border: 1px solid var(--nv-line);
  border-radius: 1rem;
  box-shadow: var(--nv-shadow-card);
}

.nv-board-header-panel {
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--nv-surface-2) 45%, transparent), transparent),
    color-mix(in srgb, var(--nv-surface) 96%, transparent);
  border-bottom: 0;
  border-bottom-left-radius: 0;
  border-bottom-right-radius: 0;
}

.nv-board-icon-wrap {
  border-radius: 0.9rem;
  display: inline-flex;
}

.nv-board-icon,
.nv-board-icon-fallback {
  border-radius: 0.9rem;
  height: 5.5rem;
  width: 5.5rem;
}

.nv-board-icon {
  border: 1px solid var(--nv-line);
  object-fit: cover;
}

.nv-board-icon-fallback {
  align-items: center;
  background: var(--nv-accent-bg);
  color: var(--nv-accent);
  display: inline-flex;
  font-size: 2rem;
  font-weight: 700;
  justify-content: center;
}

.nv-board-manage-btn,
.nv-board-subscribe-btn,
.nv-board-write-btn {
  align-items: center;
  border-radius: 0.55rem;
  display: inline-flex;
  font-size: 0.82rem;
  font-weight: 600;
  justify-content: center;
  transition: background-color 0.2s ease, border-color 0.2s ease, color 0.2s ease, filter 0.2s ease, box-shadow 0.2s ease;
}

.nv-board-subscribe-btn {
  min-height: 1.95rem;
  padding: 0.35rem 0.65rem;
}

.nv-board-manage-btn {
  background: var(--nv-surface);
  border: 1px solid var(--nv-line);
  color: var(--nv-ink);
  gap: 0.375rem;
  min-height: 1.95rem;
  padding: 0.35rem 0.65rem;
}

.nv-board-manage-btn:hover {
  background: var(--nv-surface-2);
}

.nv-board-write-btn {
  background: var(--nv-accent);
  border: 1px solid var(--nv-accent);
  color: #fff;
  height: 2.2rem;
  justify-content: center;
  line-height: 1;
  min-height: 2.2rem;
  min-width: 4.75rem;
  padding: 0 0.8rem;
}

.nv-board-write-btn:hover {
  filter: brightness(0.94);
}

.nv-board-subscribe-btn.btn-secondary:not(:disabled):hover {
  background: var(--nv-surface-2);
}

.nv-board-subscribe-btn.btn-primary:not(:disabled):hover {
  filter: brightness(0.94);
}

.nv-board-subscribe-btn:not(:disabled),
.nv-board-write-btn {
  cursor: pointer;
}

.nv-board-subscribe-btn:not(:disabled):active,
.nv-board-write-btn:active {
  filter: brightness(0.9);
}

.nv-board-subscribe-btn:focus-visible,
.nv-board-write-btn:focus-visible {
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--nv-accent) 18%, transparent);
  outline: none;
}

.nv-board-subscribe-btn:disabled {
  cursor: not-allowed;
}

@media (max-width: 639px) {
  .nv-board-manage-btn,
  .nv-board-subscribe-btn,
  .nv-board-write-btn {
    min-height: 2.75rem;
  }

  .nv-board-panel {
    border-radius: 0.85rem;
  }

  .nv-board-icon,
  .nv-board-icon-fallback {
    border-radius: 0.8rem;
    height: 4.5rem;
    width: 4.5rem;
  }
}
</style>
