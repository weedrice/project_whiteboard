<script setup lang="ts">
import { watch, computed, type Component } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useKeyboardStore, type DropdownItem } from '@/stores/keyboard'
import { User, LogOut, CreditCard, FileText, Clock, AlertTriangle, PlusSquare, ChevronDown, Bell, LayoutDashboard, Mail, Star, Slash, Smile, ShoppingBag } from 'lucide-vue-next'
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
const triggerId = 'user-dropdown-trigger'
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

type UserDropdownMenuItem = {
  key?: string
  route: string
  label: string
  icon: Component
}

type UserDropdownMenuSection = {
  id: string
  items: UserDropdownMenuItem[]
}

const mainMenuItems: UserDropdownMenuItem[] = [
  { key: '1', route: '/mypage', label: 'common.myPage', icon: User },
  { key: '2', route: '/mypage/notifications', label: 'common.notifications', icon: Bell },
  { key: '3', route: '/mypage/messages', label: 'common.mailbox', icon: Mail },
  { key: '4', route: '/mypage/points', label: 'common.points', icon: CreditCard },
]

const activityMenuItems: UserDropdownMenuItem[] = [
  { key: '5', route: '/mypage/scraps', label: 'common.scrap', icon: FileText },
  { key: '6', route: '/mypage/subscriptions', label: 'user.tabs.subscriptions', icon: Star },
  { route: '/mypage/purchases', label: 'shop.purchases.title', icon: ShoppingBag },
]

const historyMenuItems: UserDropdownMenuItem[] = [
  { key: '7', route: '/mypage/recent', label: 'layout.menu.recent', icon: Clock },
  { key: '8', route: '/mypage/reports', label: 'layout.menu.reports', icon: AlertTriangle },
  { key: '9', route: '/mypage/blocked', label: 'user.tabs.blocked', icon: Slash },
  { route: '/board/create', label: 'layout.menu.createBoard', icon: PlusSquare },
  { route: '/emoticons', label: 'emoticon.title', icon: Smile },
  { route: '/shop', label: 'shop.title', icon: ShoppingBag },
]

const menuSections = computed<UserDropdownMenuSection[]>(() => {
  const sections: UserDropdownMenuSection[] = []

  if (authStore.user?.role === 'SUPER_ADMIN') {
    sections.push({
      id: 'admin',
      items: [{ route: '/admin/dashboard', label: 'layout.menu.admin', icon: LayoutDashboard }],
    })
  }

  return sections.concat([
    { id: 'main', items: mainMenuItems },
    { id: 'activity', items: activityMenuItems },
    { id: 'history', items: historyMenuItems },
  ])
})

const numberedMenuItems = computed(() => (
  menuSections.value
    .flatMap((section) => section.items)
    .filter((item): item is UserDropdownMenuItem & { key: string } => Boolean(item.key))
))

const navigateTo = (route: string) => {
  emit('toggle')
  router.push(route)
}

const { handleMenuKeyDown } = useNumberedDropdownKeyboard({
  isOpen: () => props.isOpen,
  items: numberedMenuItems,
  onClose: () => {
    emit('toggle')
    keyboardStore.closeDropdown()
  },
  onSelect: (item) => navigateTo(item.route),
  getMenu: () => document.getElementById(menuId),
  getTrigger: () => document.getElementById(triggerId),
})

watch(() => props.isOpen, (isOpen) => {
  if (isOpen) {
    const dropdownItems: DropdownItem[] = numberedMenuItems.value.map((item) => ({
      label: item.label,
      action: () => navigateTo(item.route),
    }))
    keyboardStore.setOpenDropdown('user', dropdownItems)
  }
}, { immediate: true })

</script>

<template>
  <div class="relative">
    <button :id="triggerId" type="button" @click.stop="toggleDropdown"
      :aria-label="$t('layout.userMenu.ariaLabel')"
      aria-haspopup="menu"
      :aria-expanded="isOpen ? 'true' : 'false'"
      :aria-controls="isOpen ? menuId : undefined"
      class="nv-focus-ring flex items-center justify-center sm:justify-start space-x-2 text-xs sm:text-sm nv-text-muted user-dropdown-trigger focus:outline-none min-h-[40px] min-w-[40px] sm:min-h-0 sm:min-w-0 rounded-md touch-manipulation">
      <UserAvatar
        :image-url="authStore.user?.profileImageUrl"
        :name="authStore.user?.displayName || authStore.user?.loginId || 'U'"
        :alt="$t('common.profile')"
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
      @keydown="handleMenuKeyDown"
      class="origin-top-right absolute right-0 mt-2 w-[min(16rem,92vw)] sm:w-64 rounded-md shadow-lg py-1 nv-surface border nv-border focus:outline-none z-50">
      <!-- User Info -->
      <div class="px-3 py-2.5 sm:py-3 border-b nv-border">
        <p class="text-xs sm:text-sm font-medium nv-title truncate">
          {{ authStore.user?.displayName }}
        </p>
        <p class="text-xs nv-text-subtle truncate mb-1.5">
          {{ authStore.user?.email }}
        </p>
        <router-link to="/mypage/points"
          role="menuitem"
          class="flex items-center min-h-[40px] sm:min-h-0 py-1 text-xs sm:text-sm nv-accent-text font-medium nv-press-surface rounded touch-manipulation -mx-2 px-2"
          @click="emit('toggle')">
          <CreditCard class="h-3 w-3 sm:h-4 sm:w-4 mr-1 flex-shrink-0" />
          {{ formatInteger(points) }} P
        </router-link>
      </div>

      <div
        v-for="section in menuSections"
        :key="section.id"
        class="py-1 border-b nv-border"
      >
        <router-link
          v-for="item in section.items"
          :key="item.route"
          :to="item.route"
          role="menuitem"
          class="group flex items-center justify-between px-3 py-2.5 sm:py-2 min-h-[40px] sm:min-h-0 text-xs sm:text-sm nv-text-muted nv-hover-surface touch-manipulation"
          @click="emit('toggle')"
        >
          <div class="flex items-center">
            <component
              :is="item.icon"
              class="mr-2.5 sm:mr-3 h-3 w-3 sm:h-4 sm:w-4 nv-text-subtle flex-shrink-0"
            />
            {{ $t(item.label) }}
          </div>
          <kbd
            v-if="item.key"
            class="hidden sm:inline-block px-1.5 py-0.5 text-xs font-medium nv-text-subtle nv-surface-muted border nv-border rounded"
          >
            {{ item.key }}
          </kbd>
        </router-link>
      </div>

      <!-- Logout -->
      <div class="py-1">
        <BaseButton @click="handleLogout" variant="ghost" full-width
          role="menuitem"
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
  color: var(--nv-ink);
}
</style>
