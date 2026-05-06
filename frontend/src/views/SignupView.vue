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

<template>
  <div class="flex min-h-[calc(100vh-8rem)] items-center justify-center py-8">
    <div class="w-full max-w-sm">
      <div class="rounded-lg border bg-card text-card-foreground shadow-sm">
        <div class="flex flex-col space-y-1.5 p-6">
          <h2 class="text-2xl font-semibold leading-none tracking-tight">회원가입</h2>
          <p class="text-sm text-muted-foreground">계정을 만들어 TourDoum을 시작하세요.</p>
        </div>

        <div class="p-6 pt-0">
          <form @submit.prevent="handleSubmit" novalidate aria-label="회원가입 폼" class="space-y-4">
            <!-- 이메일 -->
            <div class="space-y-1">
              <label
                for="email"
                class="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70"
              >
                이메일
              </label>
              <input
                id="email"
                v-model="form.email"
                type="email"
                autocomplete="email"
                placeholder="example@email.com"
                required
                class="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
              />
              <span v-if="validationErrors.email" class="text-xs text-destructive" role="alert">
                {{ validationErrors.email }}
              </span>
            </div>

            <!-- 비밀번호 -->
            <div class="space-y-1">
              <label
                for="password"
                class="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70"
              >
                비밀번호
              </label>
              <input
                id="password"
                v-model="form.password"
                type="password"
                autocomplete="new-password"
                placeholder="8자 이상"
                required
                class="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
              />
              <span v-if="validationErrors.password" class="text-xs text-destructive" role="alert">
                {{ validationErrors.password }}
              </span>
            </div>

            <!-- 닉네임 -->
            <div class="space-y-1">
              <label
                for="nickname"
                class="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70"
              >
                닉네임
              </label>
              <input
                id="nickname"
                v-model="form.nickname"
                type="text"
                autocomplete="nickname"
                placeholder="2~50자"
                required
                class="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
              />
              <span v-if="validationErrors.nickname" class="text-xs text-destructive" role="alert">
                {{ validationErrors.nickname }}
              </span>
            </div>

            <div
              v-if="authStore.error"
              role="alert"
              aria-live="polite"
              class="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive"
            >
              {{ authStore.error }}
            </div>

            <button
              type="submit"
              :disabled="authStore.loading"
              class="inline-flex h-10 w-full items-center justify-center whitespace-nowrap rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground ring-offset-background transition-colors hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50"
            >
              {{ authStore.loading ? '처리 중...' : '회원가입' }}
            </button>
          </form>
        </div>

        <div class="flex items-center p-6 pt-0">
          <p class="text-sm text-muted-foreground">
            이미 계정이 있으신가요?
            <RouterLink to="/login" class="font-medium text-primary underline-offset-4 hover:underline">
              &larr; 로그인
            </RouterLink>
          </p>
        </div>
      </div>
    </div>
  </div>
</template>
