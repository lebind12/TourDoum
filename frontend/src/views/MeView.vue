<template>
  <section class="me-page">
    <h2>내 정보</h2>

    <div v-if="authStore.loading" class="loading">불러오는 중...</div>

    <div v-else-if="authStore.currentUser" class="user-card">
      <dl class="info-list">
        <div class="info-row">
          <dt>ID</dt>
          <dd>{{ authStore.currentUser.id }}</dd>
        </div>
        <div class="info-row">
          <dt>이메일</dt>
          <dd>{{ authStore.currentUser.email }}</dd>
        </div>
        <div class="info-row">
          <dt>닉네임</dt>
          <dd>{{ authStore.currentUser.nickname }}</dd>
        </div>
        <div class="info-row">
          <dt>역할</dt>
          <dd>{{ authStore.currentUser.role }}</dd>
        </div>
        <div v-if="authStore.currentUser.createdAt" class="info-row">
          <dt>가입일</dt>
          <dd>{{ authStore.currentUser.createdAt }}</dd>
        </div>
      </dl>
    </div>

    <div v-else class="error-msg" role="alert" aria-live="polite">
      사용자 정보를 불러올 수 없습니다.
    </div>
  </section>
</template>

<script setup lang="ts">
import { useAuthStore } from "@/stores/auth";

const authStore = useAuthStore();
</script>

<style scoped>
.me-page {
  max-width: 480px;
  margin: 2rem auto;
  padding: 0 1rem;
}

.me-page h2 {
  margin-bottom: 1.5rem;
}

.loading {
  color: #555;
}

.user-card {
  border: 1px solid #e0e0e0;
  border-radius: 6px;
  padding: 1.5rem;
}

.info-list {
  margin: 0;
}

.info-row {
  display: flex;
  gap: 1rem;
  padding: 0.5rem 0;
  border-bottom: 1px solid #f0f0f0;
}

.info-row:last-child {
  border-bottom: none;
}

.info-row dt {
  font-weight: 600;
  min-width: 80px;
  color: #333;
}

.info-row dd {
  margin: 0;
  color: #555;
}

.error-msg {
  color: #c0392b;
  background: #fde8e8;
  border-radius: 4px;
  padding: 0.75rem;
}
</style>
