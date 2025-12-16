<template>
  <div class="relative">
    <BaseInput v-model="keyword" @keydown.enter="onSearch" placeholder="Search..." inputClass="pl-10" hideLabel>
      <template #prefix>
        <svg class="h-5 w-5 text-gray-400" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor"
          aria-hidden="true">
          <path fill-rule="evenodd"
            d="M8 4a4 4 0 100 8 4 4 0 000-8zM2 8a6 6 0 1110.89 3.476l4.817 4.817a1 1 0 01-1.414 1.414l-4.816-4.816A6 6 0 012 8z"
            clip-rule="evenodd" />
        </svg>
      </template>
    </BaseInput>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import BaseInput from '@/components/common/ui/BaseInput.vue'

const router = useRouter()
const keyword = ref('')

const onSearch = () => {
  if (keyword.value.trim()) {
    router.push({ name: 'search', query: { keyword: keyword.value.trim() } })
    keyword.value = ''
  }
}
</script>

