<script setup>
import UserNavigation from '@/components/user/UserNavigation.vue'
</script>

<template>
  <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
    <UserNavigation />
    
    <router-view />
  </div>
</template>
