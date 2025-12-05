<script setup>
import { ref, onMounted } from 'vue'
import axios from '@/api'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const props = defineProps({
  placement: {
    type: String,
    default: 'SIDEBAR'
  }
})

const ad = ref(null)
const loading = ref(true)

const fetchAd = async () => {
  try {
    const { data } = await axios.get('/ads', {
      params: { placement: props.placement }
    })
    if (data.success) {
      ad.value = data.data
    }
  } catch (error) {
    // console.error('Failed to load ad:', error)
    // 광고 로드 실패 시 기본값 설정
        ad.value = {
            adId: null,
            title: t('common.advertisement'),
            imageUrl: null,
            targetUrl: null
        }
    } finally {
        loading.value = false
        // 데이터가 없어도 기본값 설정 (API 성공했으나 데이터가 없는 경우)
        if (!ad.value) {
            ad.value = {
                adId: null,
                title: t('common.advertisement'),
                imageUrl: null,
                targetUrl: null
            }
        }
  }
}

const handleAdClick = async () => {
  if (!ad.value) return
  
  try {
    // 클릭 로깅 및 타겟 URL 조회
    const { data } = await axios.post(`/ads/${ad.value.adId}/click`)
    if (data.success && data.data) {
      window.open(data.data, '_blank')
    } else if (ad.value.targetUrl) {
      window.open(ad.value.targetUrl, '_blank')
    }
  } catch (error) {
    console.error('Ad click error:', error)
    // 에러 시에도 targetUrl이 있으면 이동 시도
    if (ad.value.targetUrl) {
        window.open(ad.value.targetUrl, '_blank')
    }
  }
}

onMounted(() => {
  fetchAd()
})
</script>

<template>
  <div v-if="ad" class="my-4 text-center">
    <div 
      class="cursor-pointer overflow-hidden rounded-lg shadow-md hover:shadow-lg transition-shadow duration-300"
      :class="placement === 'SIDEBAR' ? 'inline-block' : 'block w-full'"
      @click="handleAdClick"
    >
      <img 
        v-if="ad.imageUrl"
        :src="ad.imageUrl" 
        :alt="ad.title" 
        class="max-w-full h-auto object-cover"
        :class="placement === 'SIDEBAR' ? 'w-40 h-[600px]' : 'w-full h-auto'"
      />
      <div v-else class="bg-gray-100 p-4 flex items-center justify-center text-gray-400 text-sm"
        :class="placement === 'SIDEBAR' ? 'w-40 h-[600px]' : 'w-full h-[120px]'">
        <span>{{ ad.title || $t('common.advertisement') }}</span>
      </div>
    </div>
  </div>
</template>
