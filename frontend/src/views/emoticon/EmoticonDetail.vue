<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useHead } from '@unhead/vue'
import { ArrowLeft, ShoppingCart, Tag, Calendar, User, TrendingUp, Pencil, EyeOff, Eye } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import { useConfirm } from '@/composables/useConfirm'
import { applyImageFallback } from '@/utils/imageFallback'
import { formatDateOnlyLongOrDash } from '@/utils/date'
import { useEmoticonDetailResource } from '@/features/emoticon/detail/useEmoticonDetailResource'
import { useEmoticonDetailActions } from '@/features/emoticon/detail/useEmoticonDetailActions'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { confirm } = useConfirm()

const emoticonId = computed(() => Number(route.params.emoticonId))

const {
  canPurchase,
  emoticonView,
  error,
  isLoading,
  isOwner,
  isPurchaseStatusPending,
  isPurchasing,
  isToggling,
  purchase,
  purchaseButtonText,
  purchasePrice,
  toggleVisibility,
} = useEmoticonDetailResource(emoticonId)

const {
  goToEdit,
  goToList,
  handlePurchase,
  handleToggleVisibility,
} = useEmoticonDetailActions({
  canPurchase,
  confirm,
  emoticonId,
  emoticonView,
  purchase,
  purchasePrice,
  router,
  t,
  toggleVisibility,
})

// 페이지 제목 설정
useHead({
  title: computed(() => emoticonView.value?.name ? `${emoticonView.value.name} - ${t('emoticon.title')}` : t('emoticon.title'))
})
</script>

<template>
  <div class="mx-auto max-w-5xl">
    <!-- 뒤로가기 버튼 -->
    <div class="mb-6">
      <button type="button" @click="goToList"
        class="nv-focus-ring inline-flex min-h-11 items-center rounded-md px-2 text-sm nv-text-muted hover:text-[var(--nv-accent)] transition-colors">
        <ArrowLeft class="w-4 h-4 mr-1" />
        {{ t('emoticon.detail.backToList') }}
      </button>
    </div>

    <!-- 로딩 -->
    <div v-if="isLoading" class="animate-pulse" role="status" aria-live="polite" aria-busy="true"
      :aria-label="t('common.loading')">
      <div class="nv-surface rounded-lg shadow-sm border nv-border p-6 mb-8">
        <div class="flex flex-col sm:flex-row gap-6">
          <div class="flex-shrink-0">
            <div class="w-40 h-40 nv-surface-muted rounded-lg"></div>
          </div>
          <div class="flex-1">
            <div class="flex items-start justify-between mb-4 gap-2 flex-wrap">
              <div class="space-y-3 min-w-0 flex-1">
                <div class="h-8 nv-surface-muted rounded w-1/2 max-w-sm"></div>
                <div class="h-5 nv-surface-muted rounded w-20"></div>
              </div>
              <div class="flex items-center gap-2">
                <div class="h-8 nv-surface-muted rounded-lg w-20"></div>
                <div class="h-8 nv-surface-muted rounded-lg w-16"></div>
              </div>
            </div>
            <div class="space-y-3">
              <div class="h-4 nv-surface-muted rounded w-40"></div>
              <div class="h-4 nv-surface-muted rounded w-52"></div>
              <div class="h-4 nv-surface-muted rounded w-36"></div>
            </div>
          </div>
        </div>
      </div>

      <div class="nv-surface rounded-lg shadow-sm border nv-border p-6 mb-8">
        <div class="h-6 nv-surface-muted rounded w-40 mb-4"></div>
        <div class="grid grid-cols-[repeat(auto-fill,minmax(4.5rem,1fr))] gap-3">
          <div
            v-for="i in 10"
            :key="i"
            class="aspect-square w-full nv-surface-muted rounded-lg"
          ></div>
        </div>
      </div>

      <div class="flex justify-end">
        <div class="h-11 nv-surface-muted rounded-lg w-32"></div>
      </div>
    </div>

    <!-- 에러 -->
    <div v-else-if="error" class="text-center py-20">
      <p class="nv-form-error">{{ t('emoticon.detail.loadFailed') }}</p>
      <BaseButton @click="goToList" variant="secondary" class="mt-4">
        {{ t('emoticon.detail.backToListFull') }}
      </BaseButton>
    </div>

    <!-- 컨텐츠 -->
    <div v-else-if="emoticonView">
      <!-- 상단 정보 -->
      <div class="nv-surface rounded-lg shadow-sm border nv-border p-6 mb-8">
        <div class="flex flex-col sm:flex-row gap-6">
          <!-- 썸네일 -->
          <div class="flex-shrink-0">
            <div class="w-40 h-40 nv-surface-muted rounded-lg overflow-hidden">
              <img :src="emoticonView.thumbnailSrc" :alt="emoticonView.name" decoding="async"
                class="w-full h-full object-contain" @error="applyImageFallback" />
            </div>
          </div>

          <!-- 정보 -->
          <div class="flex-1">
            <div class="flex items-start justify-between mb-4 gap-2 flex-wrap">
              <div class="flex items-center gap-2 flex-wrap">
                <h1 class="text-2xl font-bold nv-title">{{ emoticonView.name }}</h1>
                <span v-if="!emoticonView.isActive"
                  class="inline-flex items-center px-2.5 py-0.5 rounded text-xs font-medium nv-surface-muted nv-text-muted">
                  {{ $t('emoticon.visibility.hidden') }}
                </span>
              </div>
              <div v-if="isOwner" class="flex items-center gap-2">
                <button type="button" @click="handleToggleVisibility" :disabled="isToggling"
                  :class="emoticonView.isActive
                    ? 'nv-focus-ring inline-flex min-h-11 items-center px-3 py-1.5 text-sm nv-status-warning nv-hover-surface rounded-lg transition-colors'
                    : 'nv-focus-ring inline-flex min-h-11 items-center px-3 py-1.5 text-sm nv-status-success nv-hover-surface rounded-lg transition-colors'">
                  <EyeOff v-if="emoticonView.isActive" class="w-4 h-4 mr-1" />
                  <Eye v-else class="w-4 h-4 mr-1" />
                  {{ emoticonView.isActive ? $t('emoticon.visibility.hide') : $t('emoticon.visibility.show') }}
                </button>
                <button type="button" @click="goToEdit"
                  class="nv-focus-ring inline-flex min-h-11 items-center px-3 py-1.5 text-sm nv-status-info nv-hover-surface rounded-lg transition-colors">
                  <Pencil class="w-4 h-4 mr-1" />
                  {{ t('common.edit') }}
                </button>
              </div>
            </div>

            <div class="space-y-2 text-sm">
              <div class="flex items-center nv-text-muted">
                <User class="w-4 h-4 mr-2" />
                <span>{{ t('emoticon.detail.creator') }}: {{ emoticonView.creatorDisplayName }}</span>
              </div>
              <div class="flex items-center nv-text-muted">
                <Calendar class="w-4 h-4 mr-2" />
                <span>{{ t('emoticon.detail.createdAt') }}: {{ formatDateOnlyLongOrDash(emoticonView.createdAt) }}</span>
              </div>
              <div class="flex items-center nv-accent-text">
                <TrendingUp class="w-4 h-4 mr-2" />
                <span>{{ t('emoticon.detail.purchaseCount') }}: {{ t('emoticon.list.itemCount', { count: emoticonView.purchaseCountText }) }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 이모티콘 이미지 목록 -->
      <div class="nv-surface rounded-lg shadow-sm border nv-border p-6 mb-8">
        <h2 class="text-lg font-semibold nv-title mb-4">
          {{ t('emoticon.detail.imageList') }} <span class="text-sm font-normal nv-text-subtle">({{ t('emoticon.list.itemCount', { count: emoticonView.imageCount }) }})</span>
        </h2>

        <div v-if="emoticonView.imageItems.length > 0"
          class="grid grid-cols-[repeat(auto-fill,minmax(4.5rem,1fr))] gap-3">
          <div v-for="image in emoticonView.imageItems" :key="image.imageId"
            class="aspect-square w-full nv-surface-muted rounded-lg overflow-hidden">
            <img :src="image.src" :alt="image.alt" loading="lazy" decoding="async"
              class="w-full h-full object-contain" @error="applyImageFallback" />
          </div>
        </div>
        <div v-else class="text-center py-8 nv-text-subtle">
          {{ t('emoticon.detail.imageEmpty') }}
        </div>
      </div>

      <!-- 태그 -->
      <div v-if="emoticonView.tags.length > 0"
        class="nv-surface rounded-lg shadow-sm border nv-border p-6 mb-8">
        <h2 class="text-lg font-semibold nv-title mb-4 flex items-center">
          <Tag class="w-4 h-4 mr-2" />
          {{ t('emoticon.detail.tags') }}
        </h2>
        <div class="flex flex-wrap gap-2">
          <span v-for="tag in emoticonView.tags" :key="tag"
            class="inline-flex items-center px-3 py-1 rounded-full text-sm font-medium nv-status-info">
            #{{ tag }}
          </span>
        </div>
      </div>

      <!-- 구매 버튼 -->
      <div class="flex justify-end">
        <BaseButton @click="handlePurchase" :disabled="!canPurchase || isPurchasing || isPurchaseStatusPending"
          :variant="canPurchase ? 'primary' : 'secondary'" size="lg">
          <ShoppingCart class="w-4 h-4 mr-2" />
          {{ isPurchasing ? $t('emoticon.purchase.purchasing') : purchaseButtonText }}
        </BaseButton>
      </div>
    </div>
  </div>
</template>
