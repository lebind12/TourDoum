<template>
  <section class="auth-page">
    <h2>회원가입</h2>

    <form @submit.prevent="handleSubmit" novalidate aria-label="회원가입 폼">
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
        <span v-if="validationErrors.email" class="field-error" role="alert">
          {{ validationErrors.email }}
        </span>
      </div>

      <div class="field">
        <label for="password">비밀번호</label>
        <input
          id="password"
          v-model="form.password"
          type="password"
          autocomplete="new-password"
          placeholder="8자 이상"
          required
        />
        <span v-if="validationErrors.password" class="field-error" role="alert">
          {{ validationErrors.password }}
        </span>
      </div>

      <div class="field">
        <label for="nickname">닉네임</label>
        <input
          id="nickname"
          v-model="form.nickname"
          type="text"
          autocomplete="nickname"
          placeholder="2~50자"
          required
        />
        <span v-if="validationErrors.nickname" class="field-error" role="alert">
          {{ validationErrors.nickname }}
        </span>
      </div>

      <div v-if="authStore.error" role="alert" aria-live="polite" class="error-msg">
        {{ authStore.error }}
      </div>

      <button type="submit" :disabled="authStore.loading" class="btn-primary">
        {{ authStore.loading ? '처리 중...' : '회원가입' }}
      </button>
    </form>

    <p class="link-row">
      이미 계정이 있으신가요?
      <RouterLink to="/login">&larr; 로그인</RouterLink>
    </p>
  </section>
</template>

<script setup lang="ts">
import { useAuthStore } from "@/stores/auth";
import { reactive } from "vue";
import { useRouter } from "vue-router";

const authStore = useAuthStore();
const router = useRouter();

const form = reactive({ email: "", password: "", nickname: "" });
const validationErrors = reactive({ email: "", password: "", nickname: "" });

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function validate(): boolean {
	let ok = true;

	validationErrors.email = "";
	validationErrors.password = "";
	validationErrors.nickname = "";

	if (!EMAIL_RE.test(form.email)) {
		validationErrors.email = "올바른 이메일 형식을 입력하세요.";
		ok = false;
	}

	if (form.password.length < 8) {
		validationErrors.password = "비밀번호는 8자 이상이어야 합니다.";
		ok = false;
	}

	if (form.nickname.length < 2 || form.nickname.length > 50) {
		validationErrors.nickname = "닉네임은 2~50자여야 합니다.";
		ok = false;
	}

	return ok;
}

async function handleSubmit() {
	if (!validate()) return;

	const ok = await authStore.signup({
		email: form.email,
		password: form.password,
		nickname: form.nickname,
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

.field-error {
  color: #c0392b;
  font-size: 0.8rem;
  margin-top: 0.2rem;
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
