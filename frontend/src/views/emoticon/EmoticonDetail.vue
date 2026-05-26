<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { emoticonApi } from '@/api/emoticon'
import { useAuthStore } from '@/stores/auth'
import { useHead } from '@unhead/vue'
import { ArrowLeft, ShoppingCart, Tag, Calendar, User, TrendingUp, Pencil, EyeOff, Eye } from 'lucide-vue-next'
import { useToastStore } from '@/stores/toast'
import { useI18n } from 'vue-i18n'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import { useConfirm } from '@/composables/useConfirm'
import { extractErrorMessage } from '@/utils/errorHandler'
import { applyImageFallback } from '@/utils/imageFallback'
import { useToggleEmoticonVisibility } from '@/composables/useToggleEmoticonVisibility'
import { useEmoticonPermissions } from '@/composables/useEmoticonPermissions'
import { useEmoticonDetailViewModel } from '@/composables/useEmoticonDetailViewModel'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const queryClient = useQueryClient()
const toastStore = useToastStore()
const { confirm } = useConfirm()

const emoticonId = computed(() => Number(route.params.emoticonId))

// 이모티콘 상세 조회
const { data: emoticon, isLoading, error } = useQuery({
  queryKey: ['emoticon', emoticonId],
  queryFn: async () => {
    return emoticonApi.getEmoticonData(emoticonId.value)
  },
  enabled: () => !!emoticonId.value
})

// 구매 상태 확인
const {
  data: purchaseStatus,
  isLoading: isPurchaseStatusLoading,
  isFetching: isPurchaseStatusFetching,
} = useQuery({
  queryKey: ['emoticon', emoticonId, 'purchased'],
  queryFn: async () => {
    return emoticonApi.checkPurchaseStatusData(emoticonId.value)
  },
  enabled: () => !!emoticonId.value && authStore.isAuthenticated
})

const emoticonView = useEmoticonDetailViewModel(emoticon)

// 구매하기 mutation
const { mutate: purchase, isPending: isPurchasing } = useMutation({
  mutationFn: () => emoticonApi.purchaseEmoticon(emoticonId.value),
  onSuccess: () => {
    toastStore.addToast(t('emoticon.purchase.success'), 'success')
    queryClient.invalidateQueries({ queryKey: ['emoticon', emoticonId] })
    queryClient.invalidateQueries({ queryKey: ['emoticon', emoticonId, 'purchased'] })
    queryClient.invalidateQueries({ queryKey: ['user', 'points'] })
  },
  onError: (error: unknown) => {
    const message = extractErrorMessage(error) || t('emoticon.purchase.failed')
    toastStore.addToast(message, 'error')
  }
})

// 등록자 여부
const { isOwner } = useEmoticonPermissions({
  isAuthenticated: () => authStore.isAuthenticated,
  getCreatorId: () => emoticonView.value?.creatorId,
  getUserId: () => authStore.user?.userId,
})

const isPurchaseStatusPending = computed(() => (
  authStore.isAuthenticated
  && !!emoticonId.value
  && (isPurchaseStatusLoading.value || isPurchaseStatusFetching.value)
))

// 구매 가능 여부 (숨김 처리된 노비콘은 구매 불가)
const canPurchase = computed(() => {
  if (!authStore.isAuthenticated) return false
  if (!emoticonView.value) return false
  if (isPurchaseStatusPending.value) return false
  if (!emoticonView.value.isActive) return false
  if (purchaseStatus.value?.purchased) return false
  if (isOwner.value) return false
  return true
})

// 구매 버튼 텍스트
const purchaseButtonText = computed(() => {
  if (!authStore.isAuthenticated) return t('emoticon.purchase.button.loginRequired')
  if (purchaseStatus.value?.purchased) return t('emoticon.purchase.button.purchased')
  if (isOwner.value) return t('emoticon.purchase.button.myEmoticon')
  return t('emoticon.purchase.button.buyWithPrice', { price: purchaseStatus.value?.price || 100 })
})

// 목록으로 이동
const goToList = () => {
  router.push({ name: 'emoticon-list' })
}

// 수정 페이지로 이동
const goToEdit = () => {
  router.push({ name: 'emoticon-edit', params: { emoticonId: emoticonId.value } })
}

// 구매 처리
const handlePurchase = async () => {
  if (!canPurchase.value) return
  const isConfirmed = await confirm(t('emoticon.purchase.confirm'))
  if (!isConfirmed) return

  purchase()
}

// 숨김/표시 전환 mutation
const { mutate: toggleVisibility, isPending: isToggling } = useToggleEmoticonVisibility(emoticonId, {
  invalidatePurchaseStatus: true
})

const handleToggleVisibility = async () => {
  if (!emoticonView.value) return
  const verb = emoticonView.value.isActive ? t('emoticon.visibility.hideConfirm') : t('emoticon.visibility.showConfirm')
  const isConfirmed = await confirm(verb)
  if (!isConfirmed) return

  toggleVisibility()
}

// 날짜 포맷
const formatDate = (dateString: string) => {
  return new Date(dateString).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric'
  })
}

// 페이지 제목 설정
useHead({
  title: computed(() => emoticonView.value?.name ? `${emoticonView.value.name} - 노비콘` : '노비콘')
})
</script>

<template>
  <div class="max-w-5xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
    <!-- 뒤로가기 버튼 -->
    <div class="mb-6">
      <button @click="goToList"
        class="inline-flex items-center text-sm text-gray-600 dark:text-gray-400 hover:text-indigo-600 dark:hover:text-indigo-400 transition-colors">
        <ArrowLeft class="w-4 h-4 mr-1" />
        목록으로
      </button>
    </div>

    <!-- 로딩 -->
    <div v-if="isLoading" class="animate-pulse">
      <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6 mb-8">
        <div class="flex flex-col sm:flex-row gap-6">
          <div class="flex-shrink-0">
            <div class="w-40 h-40 bg-gray-200 dark:bg-gray-700 rounded-lg"></div>
          </div>
          <div class="flex-1">
            <div class="flex items-start justify-between mb-4 gap-2 flex-wrap">
              <div class="space-y-3 min-w-0 flex-1">
                <div class="h-8 bg-gray-200 dark:bg-gray-700 rounded w-1/2 max-w-sm"></div>
                <div class="h-5 bg-gray-200 dark:bg-gray-700 rounded w-20"></div>
              </div>
              <div class="flex items-center gap-2">
                <div class="h-8 bg-gray-200 dark:bg-gray-700 rounded-lg w-20"></div>
                <div class="h-8 bg-gray-200 dark:bg-gray-700 rounded-lg w-16"></div>
              </div>
            </div>
            <div class="space-y-3">
              <div class="h-4 bg-gray-200 dark:bg-gray-700 rounded w-40"></div>
              <div class="h-4 bg-gray-200 dark:bg-gray-700 rounded w-52"></div>
              <div class="h-4 bg-gray-200 dark:bg-gray-700 rounded w-36"></div>
            </div>
          </div>
        </div>
      </div>

      <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6 mb-8">
        <div class="h-6 bg-gray-200 dark:bg-gray-700 rounded w-40 mb-4"></div>
        <div class="grid grid-cols-4 sm:grid-cols-6 md:grid-cols-8 lg:grid-cols-10 gap-3">
          <div
            v-for="i in 10"
            :key="i"
            class="bg-gray-200 dark:bg-gray-700 rounded-lg"
            style="width: 100px; height: 100px;"
          ></div>
        </div>
      </div>

      <div class="flex justify-end">
        <div class="h-11 bg-gray-200 dark:bg-gray-700 rounded-lg w-32"></div>
      </div>
    </div>

    <!-- 에러 -->
    <div v-else-if="error" class="text-center py-20">
      <p class="text-red-500 dark:text-red-400">이모티콘을 불러오는데 실패했습니다.</p>
      <BaseButton @click="goToList" variant="secondary" class="mt-4">
        목록으로 돌아가기
      </BaseButton>
    </div>

    <!-- 컨텐츠 -->
    <div v-else-if="emoticonView">
      <!-- 상단 정보 -->
      <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6 mb-8">
        <div class="flex flex-col sm:flex-row gap-6">
          <!-- 썸네일 -->
          <div class="flex-shrink-0">
            <div class="w-40 h-40 bg-gray-100 dark:bg-gray-700 rounded-lg overflow-hidden">
              <img :src="emoticonView.thumbnailSrc" :alt="emoticonView.name"
                class="w-full h-full object-contain" @error="applyImageFallback" />
            </div>
          </div>

          <!-- 정보 -->
          <div class="flex-1">
            <div class="flex items-start justify-between mb-4 gap-2 flex-wrap">
              <div class="flex items-center gap-2 flex-wrap">
                <h1 class="text-2xl font-bold text-gray-900 dark:text-white">{{ emoticonView.name }}</h1>
                <span v-if="!emoticonView.isActive"
                  class="inline-flex items-center px-2.5 py-0.5 rounded text-xs font-medium bg-gray-200 text-gray-700 dark:bg-gray-600 dark:text-gray-300">
                  {{ $t('emoticon.visibility.hidden') }}
                </span>
              </div>
              <div v-if="isOwner" class="flex items-center gap-2">
                <button @click="handleToggleVisibility" :disabled="isToggling"
                  :class="emoticonView.isActive
                    ? 'inline-flex items-center px-3 py-1.5 text-sm bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-300 rounded-lg hover:bg-amber-200 dark:hover:bg-amber-900/50 transition-colors'
                    : 'inline-flex items-center px-3 py-1.5 text-sm bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300 rounded-lg hover:bg-green-200 dark:hover:bg-green-900/50 transition-colors'">
                  <EyeOff v-if="emoticonView.isActive" class="w-4 h-4 mr-1" />
                  <Eye v-else class="w-4 h-4 mr-1" />
                  {{ emoticonView.isActive ? $t('emoticon.visibility.hide') : $t('emoticon.visibility.show') }}
                </button>
                <button @click="goToEdit"
                  class="inline-flex items-center px-3 py-1.5 text-sm bg-indigo-100 text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-300 rounded-lg hover:bg-indigo-200 dark:hover:bg-indigo-900/50 transition-colors">
                  <Pencil class="w-4 h-4 mr-1" />
                  수정
                </button>
              </div>
            </div>

            <div class="space-y-2 text-sm">
              <div class="flex items-center text-gray-600 dark:text-gray-400">
                <User class="w-4 h-4 mr-2" />
                <span>등록자: {{ emoticonView.creatorDisplayName }}</span>
              </div>
              <div class="flex items-center text-gray-600 dark:text-gray-400">
                <Calendar class="w-4 h-4 mr-2" />
                <span>등록일: {{ formatDate(emoticonView.createdAt) }}</span>
              </div>
              <div class="flex items-center text-indigo-600 dark:text-indigo-400">
                <TrendingUp class="w-4 h-4 mr-2" />
                <span>판매 수량: {{ emoticonView.purchaseCountText }}개</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 이모티콘 이미지 목록 -->
      <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6 mb-8">
        <h2 class="text-lg font-semibold text-gray-900 dark:text-white mb-4">
          이모티콘 목록 <span class="text-sm font-normal text-gray-500">({{ emoticonView.imageCount }}개)</span>
        </h2>

        <div v-if="emoticonView.imageItems.length > 0"
          class="grid grid-cols-4 sm:grid-cols-6 md:grid-cols-8 lg:grid-cols-10 gap-3">
          <div v-for="image in emoticonView.imageItems" :key="image.imageId"
            class="aspect-square bg-gray-100 dark:bg-gray-700 rounded-lg overflow-hidden"
            style="width: 100px; height: 100px;">
            <img :src="image.src" :alt="image.alt"
              class="w-full h-full object-contain" @error="applyImageFallback" />
          </div>
        </div>
        <div v-else class="text-center py-8 text-gray-500 dark:text-gray-400">
          등록된 이미지가 없습니다.
        </div>
      </div>

      <!-- 태그 -->
      <div v-if="emoticonView.tags.length > 0"
        class="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6 mb-8">
        <h2 class="text-lg font-semibold text-gray-900 dark:text-white mb-4 flex items-center">
          <Tag class="w-4 h-4 mr-2" />
          태그
        </h2>
        <div class="flex flex-wrap gap-2">
          <span v-for="tag in emoticonView.tags" :key="tag"
            class="inline-flex items-center px-3 py-1 rounded-full text-sm font-medium bg-indigo-100 text-indigo-800 dark:bg-indigo-900/30 dark:text-indigo-300">
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
