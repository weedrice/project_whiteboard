<script setup lang="ts">
import { computed, ref } from 'vue'
import { ChevronLeft, ChevronRight, Flame } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import ErrorState from '@/components/common/ui/ErrorState.vue'
import { useAttendance } from '@/features/user/attendance/useAttendance'

const { locale, t } = useI18n()
const enabled = ref(true)
const initialDate = new Date()
const visibleDate = ref(new Date(initialDate.getFullYear(), initialDate.getMonth(), 1))
const month = computed(() => {
  const year = visibleDate.value.getFullYear()
  const monthNumber = String(visibleDate.value.getMonth() + 1).padStart(2, '0')
  return `${year}-${monthNumber}`
})
const { useMyAttendance } = useAttendance()
const { data: attendance, isLoading, isError, refetch } = useMyAttendance(enabled, month)

const monthLabel = computed(() => new Intl.DateTimeFormat(locale.value, {
  year: 'numeric',
  month: 'long',
}).format(visibleDate.value))

const weekdays = computed(() => Array.from({ length: 7 }, (_, index) => (
  new Intl.DateTimeFormat(locale.value, { weekday: 'short' }).format(new Date(2026, 0, 4 + index))
)))

const attendanceByDate = computed(() => new Map(
  (attendance.value?.days ?? []).map((day) => [day.attendanceDate, day]),
))

const calendarCells = computed(() => {
  const year = visibleDate.value.getFullYear()
  const monthIndex = visibleDate.value.getMonth()
  const leading = new Date(year, monthIndex, 1).getDay()
  const daysInMonth = new Date(year, monthIndex + 1, 0).getDate()
  const cells: Array<{ date: string; day: number; streakCount: number; checked: boolean } | null> = Array(leading).fill(null)

  for (let day = 1; day <= daysInMonth; day += 1) {
    const date = `${month.value}-${String(day).padStart(2, '0')}`
    const checkedDay = attendanceByDate.value.get(date)
    cells.push({ date, day, streakCount: checkedDay?.streakCount ?? 0, checked: Boolean(checkedDay) })
  }
  while (cells.length % 7 !== 0) cells.push(null)
  return cells
})

const calendarWeeks = computed(() => {
  const weeks = []
  for (let index = 0; index < calendarCells.value.length; index += 7) {
    weeks.push(calendarCells.value.slice(index, index + 7))
  }
  return weeks
})

const getCellLabel = (cell: NonNullable<(typeof calendarCells.value)[number]>) => {
  const parts = [new Intl.DateTimeFormat(locale.value, { dateStyle: 'full' }).format(new Date(`${cell.date}T00:00:00`))]
  if (cell.date === attendance.value?.today) parts.push(t('user.attendance.today'))
  if (cell.checked) parts.push(t('user.attendance.checkedDay', { count: cell.streakCount }))
  return parts.join(', ')
}

const changeMonth = (offset: number) => {
  visibleDate.value = new Date(visibleDate.value.getFullYear(), visibleDate.value.getMonth() + offset, 1)
}
</script>

<template>
  <section class="nv-surface rounded-[var(--nv-radius-lg)] border nv-border p-4 shadow sm:p-6">
    <div class="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
      <div>
        <p class="nv-kicker">{{ $t('user.navigation.activity') }}</p>
        <h1 class="mt-1 text-2xl font-semibold nv-title">{{ $t('user.attendance.title') }}</h1>
        <p class="mt-2 text-sm nv-text-subtle">{{ $t('user.attendance.description') }}</p>
      </div>
      <div class="flex items-center gap-2 rounded-[var(--nv-radius-md)] nv-surface-muted p-2">
        <Flame class="h-5 w-5 text-[var(--nv-accent)]" aria-hidden="true" />
        <span class="text-sm font-semibold nv-title">
          {{ $t('user.dashboard.dayStreak', { count: attendance?.currentStreakCount ?? 0 }) }}
        </span>
      </div>
    </div>

    <div class="mt-6 flex items-center justify-between gap-3">
      <BaseButton size="sm" variant="secondary" :aria-label="t('user.attendance.previousMonth')" @click="changeMonth(-1)">
        <ChevronLeft class="h-4 w-4" aria-hidden="true" />
      </BaseButton>
      <h2 class="text-lg font-semibold nv-title">{{ monthLabel }}</h2>
      <BaseButton size="sm" variant="secondary" :aria-label="t('user.attendance.nextMonth')" @click="changeMonth(1)">
        <ChevronRight class="h-4 w-4" aria-hidden="true" />
      </BaseButton>
    </div>

    <div v-if="isLoading" class="flex justify-center py-16" role="status" aria-live="polite">
      <BaseSpinner />
      <span class="sr-only">{{ $t('common.loading') }}</span>
    </div>
    <ErrorState
      v-else-if="isError"
      title-tag="h2"
      :message="$t('common.messages.loadFailed')"
      show-retry
      @retry="refetch()"
    />
    <div
      v-else
      class="mt-4 overflow-hidden rounded-[var(--nv-radius-md)] border nv-border"
      role="grid"
      :aria-label="monthLabel"
    >
      <div class="grid grid-cols-7 nv-surface-muted" role="row">
        <div v-for="weekday in weekdays" :key="weekday" role="columnheader" class="px-1 py-2 text-center text-xs font-semibold nv-text-subtle">
          {{ weekday }}
        </div>
      </div>
      <div class="border-t nv-border" role="rowgroup">
        <div v-for="(week, weekIndex) in calendarWeeks" :key="weekIndex" class="grid grid-cols-7" role="row">
        <div
          v-for="(cell, index) in week"
          :key="cell?.date ?? `empty-${weekIndex}-${index}`"
          class="relative min-h-16 border-b border-r nv-border p-1.5 sm:min-h-20 sm:p-2"
          :class="cell?.checked && 'bg-[var(--nv-accent-bg)]'"
          role="gridcell"
          :aria-label="cell ? getCellLabel(cell) : undefined"
          :aria-current="cell?.date === attendance?.today ? 'date' : undefined"
          :aria-hidden="cell ? undefined : 'true'"
        >
          <template v-if="cell">
            <span class="text-xs font-medium nv-title">{{ cell.day }}</span>
            <span
              v-if="cell.date === attendance?.today"
              class="ml-1 text-xs font-semibold nv-accent-text"
            >{{ $t('user.attendance.today') }}</span>
            <div v-if="cell.checked" class="mt-2 flex items-center gap-1 text-xs font-semibold nv-accent-text">
              <Flame class="h-3 w-3" aria-hidden="true" />
              <span>{{ $t('user.attendance.checkedDay', { count: cell.streakCount }) }}</span>
            </div>
          </template>
        </div>
        </div>
      </div>
    </div>
  </section>
</template>
