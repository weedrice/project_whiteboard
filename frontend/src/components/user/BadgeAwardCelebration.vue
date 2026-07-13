<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import {
  Award,
  CalendarCheck,
  CalendarDays,
  Edit3,
  Files,
  Flame,
  Heart,
  Library,
  MessageCircle,
  MessageSquareText,
  MessagesSquare,
} from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import { badgeApi } from '@/api/badge'
import { unwrapAxiosApiData } from '@/api/response'
import { subscribeBadgeAwardEvents } from '@/composables/badgeAwardEvents'
import { useAuthStore } from '@/stores/auth'
import type { Badge } from '@/types'
import logger from '@/utils/logger'

const router = useRouter()
const authStore = useAuthStore()
const awardedBadge = ref<Badge | null>(null)
const shownBadgeCodes = new Set<string>()
const badgeIcons = {
  'edit-3': Edit3,
  files: Files,
  library: Library,
  'message-circle': MessageCircle,
  'messages-square': MessagesSquare,
  'message-square-text': MessageSquareText,
  'calendar-check': CalendarCheck,
  'calendar-days': CalendarDays,
  heart: Heart,
  flame: Flame,
}
const badgeIcon = computed(() => badgeIcons[awardedBadge.value?.icon as keyof typeof badgeIcons] ?? Award)

const close = () => {
  awardedBadge.value = null
}

const showLatestBadge = async () => {
  try {
    const badges = unwrapAxiosApiData(await badgeApi.getMyBadges())
    const latest = badges
      .filter((badge) => badge.acquired && badge.acquiredAt && !shownBadgeCodes.has(badge.badgeCode))
      .sort((left, right) => String(right.acquiredAt).localeCompare(String(left.acquiredAt)))[0]
    if (!latest) return
    shownBadgeCodes.add(latest.badgeCode)
    awardedBadge.value = latest
  } catch (error) {
    logger.warn('Failed to load newly awarded badge', error)
  }
}

const goToBadges = async () => {
  const userId = authStore.user?.userId
  close()
  if (userId) await router.push(`/user/${userId}`)
}

let unsubscribe: (() => void) | undefined
onMounted(() => {
  unsubscribe = subscribeBadgeAwardEvents(() => {
    void showLatestBadge()
  })
})
onUnmounted(() => unsubscribe?.())
</script>

<template>
  <BaseModal
    :is-open="Boolean(awardedBadge)"
    :title="$t('user.badgeAward.title')"
    size="sm"
    footer-align="between"
    @close="close"
  >
    <div v-if="awardedBadge" class="py-3 text-center">
      <div class="badge-award-art mx-auto flex h-20 w-20 items-center justify-center rounded-full bg-[var(--nv-accent-bg)] text-[var(--nv-accent)]">
        <component :is="badgeIcon" class="h-10 w-10" aria-hidden="true" />
      </div>
      <p class="mt-5 nv-kicker">{{ awardedBadge.tier }}</p>
      <h3 class="mt-1 text-xl font-semibold nv-title">{{ awardedBadge.name || awardedBadge.badgeCode }}</h3>
      <p class="mt-2 text-sm nv-text-subtle">{{ awardedBadge.description }}</p>
    </div>
    <template #footer>
      <BaseButton variant="secondary" @click="close">{{ $t('common.close') }}</BaseButton>
      <BaseButton @click="goToBadges">{{ $t('user.badgeAward.setRepresentative') }}</BaseButton>
    </template>
  </BaseModal>
</template>

<style scoped>
.badge-award-art {
  animation: badge-award-arrive 420ms cubic-bezier(0.2, 0.8, 0.2, 1) both;
}

@keyframes badge-award-arrive {
  from { opacity: 0; transform: scale(0.86); }
  to { opacity: 1; transform: scale(1); }
}
</style>
