<script setup>
import AppSidebar from './AppSidebar.vue'
</script>

<template>
  <div class="main-layout">
    <AppSidebar />
    <main class="main-content">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.main-layout {
  min-height: 100vh;
}

.main-content {
  margin-left: 220px;
  min-height: 100vh;
}

@media (max-width: 768px) {
  .main-content {
    margin-left: 64px;
  }
}
</style>
