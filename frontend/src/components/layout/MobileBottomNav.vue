<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Bell, Home, Layers3, PenSquare, UserRound } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'
import { useNotification } from '@/composables/useNotification'
import { useWriteBoardSheet } from '@/composables/useWriteBoardSheet'

defineProps<{
  hidden?: boolean
}>()

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { useUnreadCount } = useNotification()
const { data: unreadCount } = useUnreadCount()
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
} = useWriteBoardSheet()

const isHome = computed(() => route.name === 'home')
const isBoards = computed(() => route.name === 'all-boards' || route.name === 'board-detail' || route.name === 'post-detail')
const isNotifications = computed(() => route.name === 'MyNotifications')
const isProfile = computed(() => {
  return String(route.name ?? '').startsWith('My')
    || route.name === 'mypage'
    || route.name === 'SubscribedBoards'
    || route.name === 'BlockList'
})

const navigateOrLogin = async (path: string) => {
  if (!authStore.isAuthenticated && (path.startsWith('/mypage') || path.includes('/notifications'))) {
    await router.push({ name: 'login', query: { redirect: path } })
    return
  }

  await router.push(path)
}
</script>

<template>
  <div v-if="!hidden" class="sm:hidden">
    <nav class="nv-mobile-nav" aria-label="Mobile primary navigation">
      <button type="button" class="nv-mobile-nav-item" :class="{ 'is-active': isHome }" @click="navigateOrLogin('/')">
        <Home class="h-5 w-5" />
        <span>HOME</span>
      </button>
      <button type="button" class="nv-mobile-nav-item" :class="{ 'is-active': isBoards }" @click="navigateOrLogin('/boards')">
        <Layers3 class="h-5 w-5" />
        <span>BOARDS</span>
      </button>
      <button ref="fabButtonRef" type="button" class="nv-mobile-nav-fab" @click="openWriteSheet" aria-label="Create post">
        <PenSquare class="h-5 w-5" />
      </button>
      <button
        type="button"
        class="nv-mobile-nav-item relative"
        :class="{ 'is-active': isNotifications }"
        @click="navigateOrLogin('/mypage/notifications')"
      >
        <Bell class="h-5 w-5" />
        <span>ALERTS</span>
        <span v-if="authStore.isAuthenticated && unreadCount && unreadCount > 0" class="nv-mobile-nav-dot" />
      </button>
      <button type="button" class="nv-mobile-nav-item" :class="{ 'is-active': isProfile }" @click="navigateOrLogin('/mypage')">
        <UserRound class="h-5 w-5" />
        <span>MY</span>
      </button>
    </nav>

    <Teleport to="body">
      <div v-if="showWriteSheet" class="fixed inset-0 z-[90] bg-black/40 backdrop-blur-[1px]" @click="closeWriteSheet">
        <div
          ref="sheetRef"
          class="nv-mobile-sheet"
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
              <p class="text-xs font-medium tracking-[0.18em] text-[var(--nv-muted)]">WRITE</p>
              <h2 id="mobile-write-sheet-title" class="text-lg font-semibold text-[var(--nv-ink)]">Choose a board</h2>
            </div>
            <button type="button" class="rounded-full border border-[var(--nv-line)] px-3 py-1.5 text-sm text-[var(--nv-ink-soft)]" @click="closeWriteSheet">
              Close
            </button>
          </div>
          <div class="space-y-2">
            <div
              v-if="isBoardsError || isSubscribedBoardsError"
              class="rounded-2xl border border-dashed border-[var(--nv-danger)]/30 px-4 py-5 text-sm text-[var(--nv-danger)]"
            >
              Unable to load board options right now.
            </div>
            <div
              v-else-if="isSubscribedBoardsLoading"
              class="rounded-2xl border border-dashed border-[var(--nv-line)] px-4 py-5 text-sm text-[var(--nv-muted)]"
            >
              Loading your boards...
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
              <span>Browse all boards</span>
              <span class="text-xs text-[var(--nv-muted)]">/boards</span>
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
