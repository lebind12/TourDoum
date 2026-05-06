<template>
  <section class="home">
    <!-- 인증 사용자 환영 영역 -->
    <div v-if="authStore.currentUser" class="welcome-bar">
      <p class="welcome-msg">
        안녕하세요, <strong>{{ authStore.currentUser.nickname }}</strong>님
      </p>
      <div class="welcome-actions">
        <RouterLink to="/me" class="link-btn">내 정보</RouterLink>
        <button
          type="button"
          class="btn-logout"
          :disabled="authStore.loading"
          @click="handleLogout"
        >
          {{ authStore.loading ? '처리 중...' : '로그아웃' }}
        </button>
      </div>
    </div>

    <!-- 비인증 사용자 -->
    <div v-else class="guest-bar">
      <RouterLink to="/login" class="link-btn">로그인</RouterLink>
      <RouterLink to="/signup" class="link-btn">회원가입</RouterLink>
    </div>

    <!-- 서버 상태 영역 -->
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
import { useAuthStore } from "@/stores/auth";
import { useHealthStore } from "@/stores/health";
import { onMounted } from "vue";
import { useRouter } from "vue-router";

const authStore = useAuthStore();
const healthStore = useHealthStore();
const router = useRouter();

onMounted(() => {
	healthStore.fetchHealth();
});

async function handleLogout() {
	await authStore.logout();
	router.push("/");
}
</script>

<style scoped>
.home {
  padding: 1rem 0;
}

.welcome-bar,
.guest-bar {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1.5rem;
  padding: 0.75rem 1rem;
  background: #f0f7ff;
  border-radius: 6px;
}

.welcome-msg {
  margin: 0;
  flex: 1;
}

.welcome-actions {
  display: flex;
  gap: 0.5rem;
}

.link-btn {
  display: inline-block;
  padding: 0.4rem 0.9rem;
  background: #4a90e2;
  color: #fff;
  border-radius: 4px;
  text-decoration: none;
  font-size: 0.875rem;
}

.link-btn:hover {
  background: #357abd;
}

.btn-logout {
  padding: 0.4rem 0.9rem;
  background: #e0e0e0;
  color: #333;
  border: none;
  border-radius: 4px;
  font-size: 0.875rem;
  cursor: pointer;
}

.btn-logout:hover {
  background: #c0c0c0;
}

.btn-logout:disabled {
  opacity: 0.6;
  cursor: not-allowed;
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
