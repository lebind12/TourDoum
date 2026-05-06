<template>
  <section class="home">
    <h2>서버 상태</h2>

    <div v-if="healthStore.loading" class="status-badge loading">확인 중...</div>

    <div v-else-if="healthStore.status" class="status-badge up">
      {{ healthStore.status }}
    </div>

    <div v-else class="status-badge down">백엔드 미가동</div>

    <p class="hint">
      백엔드가 실행 중이면 <code>GET /api/health</code> 응답이 표시됩니다.<br />
      여행지 검색 / 숙박 추천 기능은 이후 개발 예정입니다.
    </p>
  </section>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useHealthStore } from '@/stores/health'

const healthStore = useHealthStore()

onMounted(() => {
  healthStore.fetchHealth()
})
</script>

<style scoped>
.home {
  padding: 1rem 0;
}

.status-badge {
  display: inline-block;
  padding: 0.4rem 1rem;
  border-radius: 4px;
  font-weight: bold;
  margin-bottom: 1rem;
}

.loading {
  background-color: #f0f0f0;
  color: #555;
}

.up {
  background-color: #d4edda;
  color: #155724;
}

.down {
  background-color: #f8d7da;
  color: #721c24;
}

.hint {
  color: #666;
  font-size: 0.9rem;
}
</style>
