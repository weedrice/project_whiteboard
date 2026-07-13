<script setup lang="ts">
import { computed, ref } from 'vue'
import { CalendarCheck, Flame } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { useAttendance } from '@/composables/useAttendance'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'

const { t } = useI18n()
const authStore = useAuthStore()
const toastStore = useToastStore()
const { useMyAttendance, useCheckIn } = useAttendance()

const enabled = computed(() => authStore.isAuthenticated)
const { data: attendance, isLoading, isError, refetch } = useMyAttendance(enabled)
const { mutateAsync: checkIn, isPending } = useCheckIn()

const checkedInToday = computed(() => attendance.value?.checkedInToday === true)
const streakCount = computed(() => attendance.value?.currentStreakCount ?? 0)
const milestoneStreak = ref<number | null>(null)

async function handleCheckIn() {
  const result = await checkIn()
  if (!result.alreadyCheckedIn && (result.streakCount === 7 || result.streakCount === 30)) {
    milestoneStreak.value = result.streakCount
  }
  if (result.earnedPoints > 0) {
    toastStore.addToast(t('home.attendance.earned', { points: result.earnedPoints }), 'success')
  } else {
    toastStore.addToast(t('home.attendance.alreadyCheckedIn'), 'info')
  }
}
</script>

<template>
  <section v-if="authStore.isAuthenticated" class="nv-attendance-panel">
    <div v-if="isError" class="w-full" role="alert">
      <p class="text-sm text-[var(--nv-danger-text)]">{{ $t('common.messages.loadFailed') }}</p>
      <button type="button" class="nv-attendance-retry mt-3" @click="refetch()">
        {{ $t('common.error.retry') }}
      </button>
    </div>
    <template v-else>
    <div class="flex min-w-0 items-center gap-3">
      <div class="nv-attendance-icon">
        <CalendarCheck class="h-5 w-5" aria-hidden="true" />
      </div>
      <div class="min-w-0">
        <p class="nv-home-section-kicker">{{ $t('home.attendance.title') }}</p>
        <p class="mt-1 flex items-center gap-1.5 text-sm font-semibold text-[var(--nv-ink)]">
          <Flame class="h-4 w-4 text-[var(--nv-accent)]" aria-hidden="true" />
          {{ $t('home.attendance.streak', { count: streakCount }) }}
        </p>
      </div>
    </div>
    <button
      type="button"
      class="nv-attendance-button"
      :disabled="checkedInToday || isLoading || isPending"
      @click="handleCheckIn"
    >
      {{ checkedInToday ? $t('home.attendance.done') : $t('home.attendance.checkIn') }}
    </button>
    <p v-if="milestoneStreak" class="nv-attendance-milestone" role="status" aria-live="polite">
      <Flame class="h-4 w-4" aria-hidden="true" />
      {{ $t('home.attendance.milestone', { count: milestoneStreak }) }}
    </p>
    </template>
  </section>
</template>

<style scoped>
.nv-attendance-panel {
  align-items: center;
  background: var(--nv-surface);
  border: 1px solid var(--nv-line);
  border-radius: var(--nv-radius-lg);
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  justify-content: space-between;
  padding: 1rem;
  position: relative;
}

.nv-attendance-milestone {
  align-items: center;
  animation: attendance-milestone-arrive 420ms cubic-bezier(0.2, 0.8, 0.2, 1) both;
  background: var(--nv-accent-bg);
  border: 1px solid color-mix(in srgb, var(--nv-accent) 28%, var(--nv-line));
  border-radius: var(--nv-radius-pill);
  color: var(--nv-accent);
  display: inline-flex;
  font-size: 0.75rem;
  font-weight: 700;
  gap: 0.35rem;
  flex-basis: 100%;
  padding: 0.4rem 0.65rem;
  position: static;
}

@keyframes attendance-milestone-arrive {
  from { opacity: 0; transform: translateY(-0.25rem) scale(0.96); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}

.nv-attendance-icon {
  align-items: center;
  background: color-mix(in srgb, var(--nv-accent) 12%, var(--nv-surface));
  border: 1px solid color-mix(in srgb, var(--nv-accent) 28%, var(--nv-line));
  border-radius: var(--nv-radius-md);
  color: var(--nv-accent);
  display: inline-flex;
  flex-shrink: 0;
  height: 2.5rem;
  justify-content: center;
  width: 2.5rem;
}

.nv-attendance-button {
  background: var(--nv-accent);
  border-radius: var(--nv-radius-pill);
  color: white;
  flex-shrink: 0;
  font-size: 0.875rem;
  font-weight: 700;
  min-height: 2.75rem;
  padding: 0.5rem 0.9rem;
  transition: filter 0.15s ease, opacity 0.15s ease;
}

.nv-attendance-button:disabled {
  background: var(--nv-surface-2);
  color: var(--nv-muted);
  cursor: default;
}

.nv-attendance-button:not(:disabled):hover {
  filter: brightness(0.96);
}

.nv-attendance-retry {
  border: 1px solid var(--nv-line);
  border-radius: var(--nv-radius-pill);
  min-height: 2.75rem;
  padding: 0.5rem 0.9rem;
}
</style>
