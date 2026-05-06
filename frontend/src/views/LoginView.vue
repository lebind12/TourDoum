<template>
  <section class="auth-page">
    <h2>로그인</h2>

    <form @submit.prevent="handleSubmit" novalidate aria-label="로그인 폼">
      <div class="field">
        <label for="email">이메일</label>
        <input
          id="email"
          v-model="form.email"
          type="email"
          autocomplete="email"
          placeholder="example@email.com"
          required
        />
      </div>

      <div class="field">
        <label for="password">비밀번호</label>
        <input
          id="password"
          v-model="form.password"
          type="password"
          autocomplete="current-password"
          placeholder="비밀번호"
          required
        />
      </div>

      <div v-if="authStore.error" role="alert" aria-live="polite" class="error-msg">
        {{ authStore.error }}
      </div>

      <button type="submit" :disabled="authStore.loading" class="btn-primary">
        {{ authStore.loading ? '로그인 중...' : '로그인' }}
      </button>
    </form>

    <p class="link-row">
      계정이 없으신가요?
      <RouterLink to="/signup">회원가입 &rarr;</RouterLink>
    </p>
  </section>
</template>

<script setup lang="ts">
import { useAuthStore } from "@/stores/auth";
import { reactive } from "vue";
import { useRouter } from "vue-router";

const authStore = useAuthStore();
const router = useRouter();

const form = reactive({ email: "", password: "" });

async function handleSubmit() {
	const ok = await authStore.login({
		email: form.email,
		password: form.password,
	});
	if (ok) {
		router.push("/");
	}
}
</script>

<style scoped>
.auth-page {
  max-width: 400px;
  margin: 2rem auto;
  padding: 0 1rem;
}

.auth-page h2 {
  margin-bottom: 1.5rem;
}

.field {
  display: flex;
  flex-direction: column;
  margin-bottom: 1rem;
}

.field label {
  font-size: 0.9rem;
  margin-bottom: 0.3rem;
  font-weight: 600;
}

.field input {
  padding: 0.5rem 0.75rem;
  border: 1px solid #ccc;
  border-radius: 4px;
  font-size: 1rem;
}

.field input:focus {
  outline: 2px solid #4a90e2;
  outline-offset: 1px;
}

.error-msg {
  color: #c0392b;
  font-size: 0.875rem;
  margin-bottom: 0.75rem;
  background: #fde8e8;
  border-radius: 4px;
  padding: 0.5rem 0.75rem;
}

.btn-primary {
  width: 100%;
  padding: 0.6rem;
  background: #4a90e2;
  color: #fff;
  border: none;
  border-radius: 4px;
  font-size: 1rem;
  cursor: pointer;
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.link-row {
  margin-top: 1rem;
  font-size: 0.9rem;
  color: #555;
}

.link-row a {
  color: #4a90e2;
  text-decoration: none;
}

.link-row a:hover {
  text-decoration: underline;
}
</style>
