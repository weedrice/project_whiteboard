<script setup lang="ts">
import { computed, type ComponentPublicInstance } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Bell, Home, Layers3, PenSquare, UserRound } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { useAuthGuard } from '@/composables/useAuthGuard'
import { useNotification } from '@/features/notifications/queries/useNotification'
import { useWriteBoardSheet } from '@/features/board/write/useWriteBoardSheet'

defineProps<{
  hidden?: boolean
}>()

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const authStore = useAuthStore()
const { requireAuth } = useAuthGuard()
const { useUnreadCount } = useNotification()
const { data: unreadCount } = useUnreadCount()
const notificationsLabel = computed(() => unreadCount.value && unreadCount.value > 0
  ? `${t('layout.mobileNav.alerts')}. ${t('notification.unreadNotifications', { count: unreadCount.value })}`
  : t('layout.mobileNav.alerts'))
const {
  fabButtonRef,
  sheetRef,
  showWriteSheet,
  preferredBoards,
  isSubscribedBoardsLoading,
  isBoardsError,
  isSubscribedBoardsError,
  openWriteSheet,
  closeWriteSheet,
  goToBoardWrite,
  handleSheetKeydown,
  retryBoardOptions,
} = useWriteBoardSheet()

const setFabButtonRef = (element: Element | ComponentPublicInstance | null) => {
  const target = element instanceof HTMLButtonElement ? element : null
  fabButtonRef.value = target
}

const setSheetRef = (element: Element | ComponentPublicInstance | null) => {
  const target = element instanceof HTMLElement ? element : null
  sheetRef.value = target
}

const isHome = computed(() => route.name === 'home')
const isBoards = computed(() => route.name === 'all-boards' || route.name === 'board-detail' || route.name === 'post-detail')
const isNotifications = computed(() => route.name === 'MyNotifications')
const isProfile = computed(() => {
  if (route.name === 'MyNotifications') return false

  return String(route.name ?? '').startsWith('My')
    || route.name === 'mypage'
    || route.name === 'SubscribedBoards'
    || route.name === 'BlockList'
})

const navigateOrLogin = async (path: string) => {
  if ((path.startsWith('/mypage') || path.includes('/notifications')) && !requireAuth(path)) {
    return
  }

  await router.push(path)
}

const handleProtectedNavigation = (event: MouseEvent, path: string) => {
  if (!requireAuth(path)) {
    event.preventDefault()
  }
}
</script>

<template>
  <div v-if="!hidden" class="sm:hidden">
    <nav class="nv-mobile-nav" :aria-label="t('layout.mobileNav.ariaLabel')">
      <RouterLink to="/" class="nv-mobile-nav-item" :class="{ 'is-active': isHome }" :aria-current="isHome ? 'page' : undefined">
        <Home class="h-5 w-5" aria-hidden="true" />
        <span class="nv-mobile-nav-label">{{ $t('layout.mobileNav.home') }}</span>
      </RouterLink>
      <RouterLink to="/boards" class="nv-mobile-nav-item" :class="{ 'is-active': isBoards }" :aria-current="isBoards ? 'page' : undefined">
        <Layers3 class="h-5 w-5" aria-hidden="true" />
        <span class="nv-mobile-nav-label">{{ $t('layout.mobileNav.boards') }}</span>
      </RouterLink>
      <button
        :ref="setFabButtonRef"
        type="button"
        class="nv-mobile-nav-fab"
        aria-haspopup="dialog"
        aria-controls="mobile-write-sheet"
        :aria-expanded="showWriteSheet ? 'true' : 'false'"
        :aria-label="t('layout.mobileNav.createPost')"
        @click="openWriteSheet"
      >
        <PenSquare class="h-5 w-5" aria-hidden="true" />
      </button>
      <RouterLink
        to="/mypage/notifications"
        class="nv-mobile-nav-item relative"
        :class="{ 'is-active': isNotifications }"
        :aria-current="isNotifications ? 'page' : undefined"
        :aria-label="notificationsLabel"
        @click="handleProtectedNavigation($event, '/mypage/notifications')"
      >
        <Bell class="h-5 w-5" aria-hidden="true" />
        <span class="nv-mobile-nav-label">{{ $t('layout.mobileNav.alerts') }}</span>
        <span v-if="authStore.isAuthenticated && unreadCount && unreadCount > 0" class="nv-mobile-nav-dot" aria-hidden="true" />
      </RouterLink>
      <RouterLink to="/mypage" class="nv-mobile-nav-item" :class="{ 'is-active': isProfile }" :aria-current="isProfile ? 'page' : undefined"
        @click="handleProtectedNavigation($event, '/mypage')">
        <UserRound class="h-5 w-5" aria-hidden="true" />
        <span class="nv-mobile-nav-label">{{ $t('layout.mobileNav.my') }}</span>
      </RouterLink>
    </nav>

    <Teleport to="body">
      <div v-if="showWriteSheet" class="fixed inset-0 z-[var(--nv-z-overlay)] bg-black/40 backdrop-blur-[1px]" @click="closeWriteSheet">
        <div
          id="mobile-write-sheet"
          :ref="setSheetRef"
          class="nv-mobile-sheet nv-elevated-surface"
          role="dialog"
          aria-modal="true"
          aria-labelledby="mobile-write-sheet-title"
          tabindex="-1"
          @click.stop
          @keydown="handleSheetKeydown"
        >
          <div class="mx-auto mb-4 h-1.5 w-14 rounded-full bg-[var(--nv-line)]" />
          <div class="mb-4 flex items-center justify-between">
            <div>
              <p class="text-xs font-medium tracking-[0.18em] text-[var(--nv-muted)]">{{ $t('layout.mobileNav.write') }}</p>
              <h2 id="mobile-write-sheet-title" class="text-lg font-semibold text-[var(--nv-ink)]">{{ $t('layout.mobileNav.chooseBoard') }}</h2>
            </div>
            <button type="button" class="nv-touch-target rounded-full border border-[var(--nv-line)] px-3 py-1.5 text-sm text-[var(--nv-ink-soft)]" @click="closeWriteSheet">
              {{ $t('layout.mobileNav.closeSheet') }}
            </button>
          </div>
          <div class="space-y-2">
            <div
              v-if="isBoardsError || isSubscribedBoardsError"
              class="rounded-2xl border border-dashed border-[var(--nv-danger)]/30 px-4 py-5 text-sm text-[var(--nv-danger-text)]"
              role="alert"
            >
              <p>{{ $t('layout.mobileNav.boardOptionsError') }}</p>
              <button type="button" class="nv-focus-ring mt-3 min-h-11 rounded-full border nv-border px-4" @click="retryBoardOptions">
                {{ $t('common.error.retry') }}
              </button>
            </div>
            <div
              v-else-if="isSubscribedBoardsLoading"
              class="rounded-2xl border border-dashed border-[var(--nv-line)] px-4 py-5 text-sm text-[var(--nv-muted)]"
            >
              {{ $t('layout.mobileNav.loadingBoards') }}
            </div>
            <button
              v-for="board in isSubscribedBoardsLoading || isBoardsError || isSubscribedBoardsError ? [] : preferredBoards"
              :key="board.boardUrl"
              type="button"
              class="nv-mobile-sheet-item"
              @click="goToBoardWrite(board.boardUrl)"
            >
              <span class="min-w-0 truncate">{{ board.boardName }}</span>
              <span class="text-xs text-[var(--nv-muted)]">{{ board.subscriberCount }}</span>
            </button>
            <button
              v-if="!isBoardsError && !isSubscribedBoardsError"
              type="button"
              class="nv-mobile-sheet-item"
              @click="navigateOrLogin('/boards')"
            >
              <span>{{ $t('layout.mobileNav.browseAllBoards') }}</span>
              <span class="text-xs text-[var(--nv-muted)]">/boards</span>
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
