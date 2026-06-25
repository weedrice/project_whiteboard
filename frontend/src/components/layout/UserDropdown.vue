<script setup lang="ts">
import { watch, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useKeyboardStore, type DropdownItem } from '@/stores/keyboard'
import { User, LogOut, CreditCard, FileText, Clock, AlertTriangle, PlusSquare, ChevronDown, Bell, LayoutDashboard, Mail, Star, Slash, Smile } from 'lucide-vue-next'
import { useUser } from '@/composables/useUser'
import { useNumberedDropdownKeyboard } from '@/composables/useNumberedDropdownKeyboard'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import UserAvatar from '@/components/common/ui/UserAvatar.vue'
import { formatInteger } from '@/utils/numberFormat'

const router = useRouter()
const authStore = useAuthStore()
const keyboardStore = useKeyboardStore()
const { useMyPoint } = useUser()

const props = withDefaults(defineProps<{
  isOpen?: boolean
}>(), {
  isOpen: false
})

const emit = defineEmits<{
  (e: 'toggle'): void
}>()

const menuId = 'user-dropdown-menu'
const shouldFetchPoints = computed(() => props.isOpen && authStore.isAuthenticated)
const pointQueryIdentity = computed(() => authStore.user?.userId ?? authStore.user?.loginId ?? null)
const { data: pointData } = useMyPoint(shouldFetchPoints, pointQueryIdentity)
const points = computed(() => pointData.value?.currentPoint ?? 0)

const toggleDropdown = () => {
  emit('toggle')
}

const handleLogout = async () => {
  await authStore.logout()
  router.push('/')
}

// 숫자 키로 선택 가능한 메뉴 목록
const menuItems = computed(() => [
  { key: '1', route: '/mypage', label: 'common.myPage' },
  // { key: '2', route: '/mypage/settings', label: 'common.settings' }, // 숨김
  { key: '2', route: '/mypage/notifications', label: 'common.notifications' },
  { key: '3', route: '/mypage/messages', label: 'common.mailbox' },
  { key: '4', route: '/mypage/points', label: 'common.points' },
  { key: '5', route: '/mypage/scraps', label: 'common.scrap' },
  { key: '6', route: '/mypage/subscriptions', label: 'user.tabs.subscriptions' },
  { key: '7', route: '/mypage/recent', label: 'layout.menu.recent' },
  { key: '8', route: '/mypage/reports', label: 'layout.menu.reports' },
  { key: '9', route: '/mypage/blocked', label: 'user.tabs.blocked' },
  // 노드 생성은 단축키 미연결
])

const navigateTo = (route: string) => {
  emit('toggle')
  router.push(route)
}

useNumberedDropdownKeyboard({
  isOpen: () => props.isOpen,
  items: menuItems,
  onClose: () => {
    emit('toggle')
    keyboardStore.closeDropdown()
  },
  onSelect: (item) => navigateTo(item.route),
})

// 드롭다운 열림 시 keyboard store에 항목 등록
watch(() => props.isOpen, (isOpen) => {
  if (isOpen) {
    const dropdownItems: DropdownItem[] = menuItems.value.map((item) => ({
      label: item.label,
      action: () => navigateTo(item.route),
    }))
    keyboardStore.setOpenDropdown('user', dropdownItems)
  }
}, { immediate: true })

</script>

<template>
  <div class="relative">
    <button type="button" @click.stop="toggleDropdown"
      aria-label="사용자 메뉴"
      aria-haspopup="menu"
      :aria-expanded="isOpen ? 'true' : 'false'"
      :aria-controls="isOpen ? menuId : undefined"
      class="flex items-center justify-center sm:justify-start space-x-2 text-xs sm:text-sm nv-text-muted user-dropdown-trigger focus:outline-none min-h-[40px] min-w-[40px] sm:min-h-0 sm:min-w-0 rounded-md touch-manipulation">
      <UserAvatar
        :image-url="authStore.user?.profileImageUrl"
        :name="authStore.user?.displayName || authStore.user?.loginId || 'U'"
        alt="Profile"
        size-class="h-7 w-7 sm:h-8 sm:w-8"
        image-class="object-contain nv-surface-muted"
        fallback-class="font-bold"
        class="border"
      />
      <span class="hidden md:block font-medium">{{ authStore.user?.displayName || authStore.user?.loginId }}</span>
      <ChevronDown class="hidden sm:inline-block h-3 w-3 sm:h-4 sm:w-4 nv-text-subtle" />
    </button>

    <div v-if="isOpen"
      :id="menuId"
      role="menu"
      class="origin-top-right absolute right-0 mt-2 w-[min(16rem,92vw)] sm:w-64 rounded-md shadow-lg py-1 nv-surface border nv-border focus:outline-none z-50">
      <!-- User Info -->
      <div class="px-3 py-2.5 sm:py-3 border-b nv-border">
        <p class="text-xs sm:text-sm font-medium nv-title truncate">
          {{ authStore.user?.displayName }}
        </p>
        <p class="text-[11px] sm:text-xs nv-text-subtle truncate mb-1.5">
          {{ authStore.user?.email }}
        </p>
        <router-link to="/mypage/points"
          class="flex items-center min-h-[40px] sm:min-h-0 py-1 text-xs sm:text-sm nv-accent-text font-medium nv-press-surface rounded touch-manipulation -mx-2 px-2"
          @click="emit('toggle')">
          <CreditCard class="h-3 w-3 sm:h-4 sm:w-4 mr-1 flex-shrink-0" />
          {{ formatInteger(points) }} P
        </router-link>
      </div>

      <!-- Group 1: Admin (conditional) -->
      <div v-if="authStore.user?.role === 'SUPER_ADMIN'" class="py-1 border-b nv-border">
        <router-link to="/admin/dashboard"
          class="group flex items-center px-3 py-2.5 sm:py-2 min-h-[40px] sm:min-h-0 text-xs sm:text-sm nv-text-muted nv-hover-surface touch-manipulation"
          @click="emit('toggle')">
          <LayoutDashboard
            class="mr-2.5 sm:mr-3 h-3 w-3 sm:h-4 sm:w-4 nv-text-subtle" />
          {{ $t('layout.menu.admin') }}
        </router-link>
      </div>

      <!-- Group 2: MyPage, Notifications, Messages, Points -->
      <div class="py-1 border-b nv-border">
        <router-link to="/mypage"
          class="group flex items-center justify-between px-3 py-2.5 sm:py-2 min-h-[40px] sm:min-h-0 text-xs sm:text-sm nv-text-muted nv-hover-surface touch-manipulation"
          @click="emit('toggle')">
          <div class="flex items-center">
            <User
              class="mr-2.5 sm:mr-3 h-3 w-3 sm:h-4 sm:w-4 nv-text-subtle flex-shrink-0" />
            {{ $t('common.myPage') }}
          </div>
          <kbd
            class="hidden sm:inline-block px-1.5 py-0.5 text-xs font-medium nv-text-subtle nv-surface-muted border nv-border rounded">1</kbd>
        </router-link>
        <router-link to="/mypage/notifications"
          class="group flex items-center justify-between px-3 py-2.5 sm:py-2 min-h-[40px] sm:min-h-0 text-xs sm:text-sm nv-text-muted nv-hover-surface touch-manipulation"
          @click="emit('toggle')">
          <div class="flex items-center">
            <Bell
              class="mr-2.5 sm:mr-3 h-3 w-3 sm:h-4 sm:w-4 nv-text-subtle flex-shrink-0" />
            {{ $t('common.notifications') }}
          </div>
          <kbd
            class="hidden sm:inline-block px-1.5 py-0.5 text-xs font-medium nv-text-subtle nv-surface-muted border nv-border rounded">2</kbd>
        </router-link>
        <router-link to="/mypage/messages"
          class="group flex items-center justify-between px-3 py-2.5 sm:py-2 min-h-[40px] sm:min-h-0 text-xs sm:text-sm nv-text-muted nv-hover-surface touch-manipulation"
          @click="emit('toggle')">
          <div class="flex items-center">
            <Mail
              class="mr-2.5 sm:mr-3 h-3 w-3 sm:h-4 sm:w-4 nv-text-subtle flex-shrink-0" />
            {{ $t('common.mailbox') }}
          </div>
          <kbd
            class="hidden sm:inline-block px-1.5 py-0.5 text-xs font-medium nv-text-subtle nv-surface-muted border nv-border rounded">3</kbd>
        </router-link>
        <router-link to="/mypage/points"
          class="group flex items-center justify-between px-3 py-2.5 sm:py-2 min-h-[40px] sm:min-h-0 text-xs sm:text-sm nv-text-muted nv-hover-surface touch-manipulation"
          @click="emit('toggle')">
          <div class="flex items-center">
            <CreditCard
              class="mr-2.5 sm:mr-3 h-3 w-3 sm:h-4 sm:w-4 nv-text-subtle flex-shrink-0" />
            {{ $t('common.points') }}
          </div>
          <kbd
            class="hidden sm:inline-block px-1.5 py-0.5 text-xs font-medium nv-text-subtle nv-surface-muted border nv-border rounded">4</kbd>
        </router-link>
      </div>

      <!-- Group 3: Scraps, Subscriptions -->
      <div class="py-1 border-b nv-border">
        <router-link to="/mypage/scraps"
          class="group flex items-center justify-between px-3 py-2.5 sm:py-2 min-h-[40px] sm:min-h-0 text-xs sm:text-sm nv-text-muted nv-hover-surface touch-manipulation"
          @click="emit('toggle')">
          <div class="flex items-center">
            <FileText
              class="mr-2.5 sm:mr-3 h-3 w-3 sm:h-4 sm:w-4 nv-text-subtle flex-shrink-0" />
            {{ $t('common.scrap') }}
          </div>
          <kbd
            class="hidden sm:inline-block px-1.5 py-0.5 text-xs font-medium nv-text-subtle nv-surface-muted border nv-border rounded">5</kbd>
        </router-link>
        <router-link to="/mypage/subscriptions"
          class="group flex items-center justify-between px-3 py-2.5 sm:py-2 min-h-[40px] sm:min-h-0 text-xs sm:text-sm nv-text-muted nv-hover-surface touch-manipulation"
          @click="emit('toggle')">
          <div class="flex items-center">
            <Star
              class="mr-2.5 sm:mr-3 h-3 w-3 sm:h-4 sm:w-4 nv-text-subtle flex-shrink-0" />
            {{ $t('user.tabs.subscriptions') }}
          </div>
          <kbd
            class="hidden sm:inline-block px-1.5 py-0.5 text-xs font-medium nv-text-subtle nv-surface-muted border nv-border rounded">6</kbd>
        </router-link>
      </div>

      <!-- Group 4: Recent, Reports, Blocked, Create Board -->
      <div class="py-1 border-b nv-border">
        <router-link to="/mypage/recent"
          class="group flex items-center justify-between px-3 py-2.5 sm:py-2 min-h-[40px] sm:min-h-0 text-xs sm:text-sm nv-text-muted nv-hover-surface touch-manipulation"
          @click="emit('toggle')">
          <div class="flex items-center">
            <Clock
              class="mr-2.5 sm:mr-3 h-3 w-3 sm:h-4 sm:w-4 nv-text-subtle flex-shrink-0" />
            {{ $t('layout.menu.recent') }}
          </div>
          <kbd
            class="hidden sm:inline-block px-1.5 py-0.5 text-xs font-medium nv-text-subtle nv-surface-muted border nv-border rounded">7</kbd>
        </router-link>
        <router-link to="/mypage/reports"
          class="group flex items-center justify-between px-3 py-2.5 sm:py-2 min-h-[40px] sm:min-h-0 text-xs sm:text-sm nv-text-muted nv-hover-surface touch-manipulation"
          @click="emit('toggle')">
          <div class="flex items-center">
            <AlertTriangle
              class="mr-2.5 sm:mr-3 h-3 w-3 sm:h-4 sm:w-4 nv-text-subtle flex-shrink-0" />
            {{ $t('layout.menu.reports') }}
          </div>
          <kbd
            class="hidden sm:inline-block px-1.5 py-0.5 text-xs font-medium nv-text-subtle nv-surface-muted border nv-border rounded">8</kbd>
        </router-link>
        <router-link to="/mypage/blocked"
          class="group flex items-center justify-between px-3 py-2.5 sm:py-2 min-h-[40px] sm:min-h-0 text-xs sm:text-sm nv-text-muted nv-hover-surface touch-manipulation"
          @click="emit('toggle')">
          <div class="flex items-center">
            <Slash
              class="mr-2.5 sm:mr-3 h-3 w-3 sm:h-4 sm:w-4 nv-text-subtle flex-shrink-0" />
            {{ $t('user.tabs.blocked') }}
          </div>
          <kbd
            class="hidden sm:inline-block px-1.5 py-0.5 text-xs font-medium nv-text-subtle nv-surface-muted border nv-border rounded">9</kbd>
        </router-link>
        <router-link to="/board/create"
          class="group flex items-center px-3 py-2.5 sm:py-2 min-h-[40px] sm:min-h-0 text-xs sm:text-sm nv-text-muted nv-hover-surface touch-manipulation"
          @click="emit('toggle')">
          <PlusSquare
            class="mr-2.5 sm:mr-3 h-3 w-3 sm:h-4 sm:w-4 nv-text-subtle flex-shrink-0" />
          {{ $t('layout.menu.createBoard') }}
        </router-link>
        <router-link to="/emoticons"
          class="group flex items-center px-3 py-2.5 sm:py-2 min-h-[40px] sm:min-h-0 text-xs sm:text-sm nv-text-muted nv-hover-surface touch-manipulation"
          @click="emit('toggle')">
          <Smile
            class="mr-2.5 sm:mr-3 h-3 w-3 sm:h-4 sm:w-4 nv-text-subtle flex-shrink-0" />
          노비콘
        </router-link>
      </div>

      <!-- Logout -->
      <div class="py-1">
        <BaseButton @click="handleLogout" variant="ghost" full-width
          class="w-full text-left group flex items-center px-3 py-2.5 sm:py-2 min-h-[40px] sm:min-h-0 text-xs sm:text-sm text-[var(--nv-danger-text)] hover:bg-[var(--nv-danger-bg)] active:bg-[var(--nv-danger-bg)] justify-start touch-manipulation">
          <LogOut
            class="mr-2.5 sm:mr-3 h-3 w-3 sm:h-4 sm:w-4 text-[var(--nv-danger-text)] flex-shrink-0" />
          {{ $t('common.logout') }}
        </BaseButton>
      </div>
    </div>
  </div>
</template>

<style scoped>
.user-dropdown-trigger:hover {
  color: var(--nv-text);
}
</style>
