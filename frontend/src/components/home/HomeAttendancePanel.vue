<script setup lang="ts">
import { computed } from 'vue'
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
const { data: attendance, isLoading } = useMyAttendance(enabled)
const { mutateAsync: checkIn, isPending } = useCheckIn()

const checkedInToday = computed(() => attendance.value?.checkedInToday === true)
const streakCount = computed(() => attendance.value?.currentStreakCount ?? 0)

async function handleCheckIn() {
  const result = await checkIn()
  if (result.earnedPoints > 0) {
    toastStore.addToast(t('home.attendance.earned', { points: result.earnedPoints }), 'success')
  } else {
    toastStore.addToast(t('home.attendance.alreadyCheckedIn'), 'info')
  }
}
</script>

<template>
  <section v-if="authStore.isAuthenticated" class="nv-attendance-panel">
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
  </section>
</template>

<style scoped>
.nv-attendance-panel {
  align-items: center;
  background: var(--nv-surface);
  border: 1px solid var(--nv-line);
  border-radius: 16px;
  display: flex;
  gap: 1rem;
  justify-content: space-between;
  padding: 1rem;
}

.nv-attendance-icon {
  align-items: center;
  background: color-mix(in srgb, var(--nv-accent) 12%, var(--nv-surface));
  border: 1px solid color-mix(in srgb, var(--nv-accent) 28%, var(--nv-line));
  border-radius: 12px;
  color: var(--nv-accent);
  display: inline-flex;
  flex-shrink: 0;
  height: 2.5rem;
  justify-content: center;
  width: 2.5rem;
}

.nv-attendance-button {
  background: var(--nv-accent);
  border-radius: 999px;
  color: white;
  flex-shrink: 0;
  font-size: 0.875rem;
  font-weight: 700;
  min-height: 2.25rem;
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
</style>
