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
      <Card>
        <CardHeader>
          <CardTitle class="text-2xl">로그인</CardTitle>
          <CardDescription>이메일과 비밀번호를 입력하세요.</CardDescription>
        </CardHeader>

        <CardContent>
          <form @submit.prevent="handleSubmit" novalidate aria-label="로그인 폼" class="space-y-4">
            <div class="space-y-2">
              <Label for="email">이메일</Label>
              <Input
                id="email"
                v-model="form.email"
                type="email"
                autocomplete="email"
                placeholder="example@email.com"
                required
              />
            </div>

            <div class="space-y-2">
              <Label for="password">비밀번호</Label>
              <Input
                id="password"
                v-model="form.password"
                type="password"
                autocomplete="current-password"
                placeholder="비밀번호"
                required
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

            <Button type="submit" class="w-full" :disabled="authStore.loading" data-testid="login-submit">
              {{ authStore.loading ? '로그인 중...' : '로그인' }}
            </Button>
          </form>
        </CardContent>

        <CardFooter>
          <p class="text-sm text-muted-foreground">
            계정이 없으신가요?
            <RouterLink to="/signup" class="font-medium text-primary underline-offset-4 hover:underline">
              회원가입 &rarr;
            </RouterLink>
          </p>
        </CardFooter>
      </Card>
    </div>
  </div>
</template>
