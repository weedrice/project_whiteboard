<script setup lang="ts">
import { computed, type ComponentPublicInstance } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Home, Layers3, PenSquare, Rss, UserRound } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { useAuthGuard } from '@/composables/useAuthGuard'
import { useWriteBoardSheet } from '@/features/board/write/useWriteBoardSheet'

defineProps<{
  hidden?: boolean
}>()

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const { requireAuth } = useAuthGuard()
const {
  fabButtonRef,
  sheetRef,
  sheetOverlayRef,
  isTopDialog,
  showWriteSheet,
  preferredBoards,
  isSubscribedBoardsLoading,
  isBoardsError,
  isSubscribedBoardsError,
  isVerifyingWriteAccess,
  openWriteSheet,
  closeWriteSheet,
  goToBoardWrite,
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

const isPersonalFeed = computed(() => route.name === 'home' && route.query.view === 'feed')
const isHome = computed(() => route.name === 'home' && !isPersonalFeed.value)
const isBoards = computed(() => route.name === 'all-boards' || route.name === 'board-detail' || route.name === 'post-detail')
const isProfile = computed(() => String(route.name ?? '').startsWith('My')
  || route.name === 'mypage'
  || route.name === 'SubscribedBoards'
  || route.name === 'BlockList')

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
        :to="{ name: 'home', query: { view: 'feed' } }"
        class="nv-mobile-nav-item"
        :class="{ 'is-active': isPersonalFeed }"
        :aria-current="isPersonalFeed ? 'page' : undefined"
        @click="handleProtectedNavigation($event, '/?view=feed')"
      >
        <Rss class="h-5 w-5" aria-hidden="true" />
        <span class="nv-mobile-nav-label">{{ $t('layout.mobileNav.feed') }}</span>
      </RouterLink>
      <RouterLink to="/mypage" class="nv-mobile-nav-item" :class="{ 'is-active': isProfile }" :aria-current="isProfile ? 'page' : undefined"
        @click="handleProtectedNavigation($event, '/mypage')">
        <UserRound class="h-5 w-5" aria-hidden="true" />
        <span class="nv-mobile-nav-label">{{ $t('layout.mobileNav.my') }}</span>
      </RouterLink>
    </nav>

    <Teleport to="body">
      <div v-if="showWriteSheet" ref="sheetOverlayRef" class="nv-dialog-overlay nv-dialog-overlay--sheet" @click.self="closeWriteSheet">
        <div
          id="mobile-write-sheet"
          :ref="setSheetRef"
          class="nv-mobile-sheet nv-dialog-surface nv-dialog-surface--sheet"
          role="dialog"
          :aria-modal="isTopDialog ? 'true' : undefined"
          :aria-hidden="isTopDialog ? undefined : 'true'"
          :inert="isTopDialog ? undefined : true"
          aria-labelledby="mobile-write-sheet-title"
          tabindex="-1"
          :aria-busy="isVerifyingWriteAccess"
          @click.stop
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
              :disabled="isVerifyingWriteAccess"
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
