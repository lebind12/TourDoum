<script setup lang="ts">
import { Button } from "@/components/ui/button";
import {
	Card,
	CardContent,
	CardDescription,
	CardFooter,
	CardHeader,
	CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useAuthStore } from "@/stores/auth";
import { reactive } from "vue";
import { RouterLink, useRouter } from "vue-router";

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
      <Card>
        <CardHeader>
          <CardTitle class="text-2xl">회원가입</CardTitle>
          <CardDescription>계정을 만들어 TourDoum을 시작하세요.</CardDescription>
        </CardHeader>

        <CardContent>
          <form @submit.prevent="handleSubmit" novalidate aria-label="회원가입 폼" class="space-y-4">
            <!-- 이메일 -->
            <div class="space-y-1">
              <Label for="email">이메일</Label>
              <Input
                id="email"
                v-model="form.email"
                type="email"
                autocomplete="email"
                placeholder="example@email.com"
                required
              />
              <span v-if="validationErrors.email" class="text-xs text-destructive" role="alert">
                {{ validationErrors.email }}
              </span>
            </div>

            <!-- 비밀번호 -->
            <div class="space-y-1">
              <Label for="password">비밀번호</Label>
              <Input
                id="password"
                v-model="form.password"
                type="password"
                autocomplete="new-password"
                placeholder="8자 이상"
                required
              />
              <span v-if="validationErrors.password" class="text-xs text-destructive" role="alert">
                {{ validationErrors.password }}
              </span>
            </div>

            <!-- 닉네임 -->
            <div class="space-y-1">
              <Label for="nickname">닉네임</Label>
              <Input
                id="nickname"
                v-model="form.nickname"
                type="text"
                autocomplete="nickname"
                placeholder="2~50자"
                required
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

            <Button type="submit" class="w-full" :disabled="authStore.loading" data-testid="signup-submit">
              {{ authStore.loading ? '처리 중...' : '회원가입' }}
            </Button>
          </form>
        </CardContent>

        <CardFooter>
          <p class="text-sm text-muted-foreground">
            이미 계정이 있으신가요?
            <RouterLink to="/login" class="font-medium text-primary underline-offset-4 hover:underline">
              &larr; 로그인
            </RouterLink>
          </p>
        </CardFooter>
      </Card>
    </div>
  </div>
</template>
