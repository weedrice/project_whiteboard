<template>
  <nav v-if="totalPages >= 1" class="flex items-center justify-center gap-0.5 sm:space-x-1 text-xs sm:text-sm flex-wrap" :aria-label="$t('common.pagination')">
    <template v-if="hasLinkBuilder">
      <router-link v-if="currentPage > 0" :to="buildLink(currentPage - 1)" :class="navButtonClass">
        {{ $t('common.previous') }}
      </router-link>
      <span v-else :class="[navButtonClass, 'opacity-50 cursor-not-allowed']">
        {{ $t('common.previous') }}
      </span>

      <template v-for="(page, index) in displayedPages" :key="getPageKey(page, index)">
        <span v-if="page === '...'" class="px-2 sm:px-3 py-2 sm:py-2 text-xs sm:text-sm nv-text-subtle min-h-[44px] sm:min-h-0 flex items-center">...</span>
        <span v-else-if="currentPage === (page as number) - 1" :class="activePageClass" aria-current="page">
          {{ page }}
        </span>
        <router-link v-else :to="buildLink((page as number) - 1)" :class="pageLinkClass">
          {{ page }}
        </router-link>
      </template>

      <router-link v-if="currentPage < totalPages - 1" :to="buildLink(currentPage + 1)" :class="navButtonClass">
        {{ $t('common.next') }}
      </router-link>
      <span v-else :class="[navButtonClass, 'opacity-50 cursor-not-allowed']">
        {{ $t('common.next') }}
      </span>
    </template>

    <template v-else>
      <BaseButton :disabled="currentPage === 0" @click="$emit('page-change', currentPage - 1)" variant="secondary" size="sm"
        class="min-h-[44px] min-w-[44px] sm:min-h-0 sm:min-w-0 touch-manipulation">
        {{ $t('common.previous') }}
      </BaseButton>

      <template v-for="(page, index) in displayedPages" :key="getPageKey(page, index)">
        <span v-if="page === '...'" class="px-2 sm:px-3 py-2 sm:py-2 text-xs sm:text-sm nv-text-subtle min-h-[44px] sm:min-h-0 flex items-center">...</span>
        <BaseButton v-else @click="$emit('page-change', (page as number) - 1)"
          :variant="currentPage === (page as number) - 1 ? 'primary' : 'secondary'"
          :aria-current="currentPage === (page as number) - 1 ? 'page' : undefined"
          size="sm"
          :class="[currentPage === (page as number) - 1 ? 'z-10' : '', 'min-h-[44px] min-w-[44px] sm:min-h-0 sm:min-w-0 touch-manipulation']">
          {{ page }}
        </BaseButton>
      </template>

      <BaseButton :disabled="currentPage === totalPages - 1" @click="$emit('page-change', currentPage + 1)"
        variant="secondary" size="sm"
        class="min-h-[44px] min-w-[44px] sm:min-h-0 sm:min-w-0 touch-manipulation">
        {{ $t('common.next') }}
      </BaseButton>
    </template>
  </nav>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { RouteLocationRaw } from 'vue-router'
import BaseButton from '@/components/common/ui/BaseButton.vue'

const props = withDefaults(defineProps<{
  currentPage: number
  totalPages: number
  linkBuilder?: ((page: number) => RouteLocationRaw) | null
}>(), {
  linkBuilder: null
})

defineEmits<{
  (e: 'page-change', page: number): void
}>()

const hasLinkBuilder = computed(() => typeof props.linkBuilder === 'function')

const navButtonClass = 'btn-secondary btn-sm min-h-[44px] min-w-[44px] sm:min-h-0 sm:min-w-0 touch-manipulation no-underline inline-flex justify-center items-center'
const pageLinkClass = 'btn-secondary btn-sm min-h-[44px] min-w-[44px] sm:min-h-0 sm:min-w-0 touch-manipulation no-underline inline-flex justify-center items-center'
const activePageClass = 'btn-primary btn-sm z-10 min-h-[44px] min-w-[44px] sm:min-h-0 sm:min-w-0 touch-manipulation inline-flex justify-center items-center'

function buildLink(page: number): RouteLocationRaw {
  if (!props.linkBuilder) {
    return {}
  }
  return props.linkBuilder(page)
}

function getPageKey(page: number | string, index: number): string {
  return `${page}-${index}`
}

const displayedPages = computed(() => {
  const delta = 2
  const totalPages = Math.max(props.totalPages, 0)
  const currentPageNumber = Math.min(Math.max(props.currentPage + 1, 1), totalPages)
  const candidatePages = new Set<number>()
  const rangeWithDots: (number | string)[] = []
  let l: number | undefined

  if (totalPages === 0) {
    return rangeWithDots
  }

  candidatePages.add(1)
  candidatePages.add(totalPages)

  for (
    let i = Math.max(1, currentPageNumber - delta);
    i <= Math.min(totalPages, currentPageNumber + delta);
    i += 1
  ) {
    candidatePages.add(i)
  }

  const range = [...candidatePages].sort((a, b) => a - b)

  for (const i of range) {
    if (l) {
      if (i - l === 2) {
        rangeWithDots.push(l + 1)
      } else if (i - l !== 1) {
        rangeWithDots.push('...')
      }
    }
    rangeWithDots.push(i)
    l = i
  }

  return rangeWithDots
})
</script>

