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

<template>
  <div class="flex min-h-[calc(100vh-8rem)] items-center justify-center py-8">
    <div class="w-full max-w-sm">
      <div class="rounded-lg border bg-card text-card-foreground shadow-sm">
        <div class="flex flex-col space-y-1.5 p-6">
          <h2 class="text-2xl font-semibold leading-none tracking-tight">로그인</h2>
          <p class="text-sm text-muted-foreground">이메일과 비밀번호를 입력하세요.</p>
        </div>

        <div class="p-6 pt-0">
          <form @submit.prevent="handleSubmit" novalidate aria-label="로그인 폼" class="space-y-4">
            <div class="space-y-2">
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
            </div>

            <div class="space-y-2">
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
                autocomplete="current-password"
                placeholder="비밀번호"
                required
                class="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
              />
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
              {{ authStore.loading ? '로그인 중...' : '로그인' }}
            </button>
          </form>
        </div>

        <div class="flex items-center p-6 pt-0">
          <p class="text-sm text-muted-foreground">
            계정이 없으신가요?
            <RouterLink to="/signup" class="font-medium text-primary underline-offset-4 hover:underline">
              회원가입 &rarr;
            </RouterLink>
          </p>
        </div>
      </div>
    </div>
  </div>
</template>
