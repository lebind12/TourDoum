<script setup lang="ts">
import {
	Card,
	CardContent,
	CardDescription,
	CardHeader,
	CardTitle,
} from "@/components/ui/card";
import { useAuthStore } from "@/stores/auth";

const authStore = useAuthStore();
</script>

<template>
  <div class="mx-auto max-w-lg space-y-6">
    <div>
      <h2 class="text-2xl font-semibold tracking-tight">내 정보</h2>
      <p class="text-sm text-muted-foreground mt-1">계정 정보를 확인합니다.</p>
    </div>

    <!-- 로딩 -->
    <div v-if="authStore.loading" class="text-sm text-muted-foreground animate-pulse">
      불러오는 중...
    </div>

    <!-- 사용자 정보 카드 -->
    <Card v-else-if="authStore.currentUser">
      <CardHeader>
        <CardTitle class="text-base">계정 정보</CardTitle>
        <CardDescription>가입한 계정의 기본 정보입니다.</CardDescription>
      </CardHeader>
      <CardContent>
        <dl class="space-y-0 divide-y divide-border">
          <div class="flex items-center gap-4 py-3 first:pt-0 last:pb-0">
            <dt class="w-20 flex-shrink-0 text-sm font-medium text-muted-foreground">ID</dt>
            <dd class="text-sm">{{ authStore.currentUser.id }}</dd>
          </div>
          <div class="flex items-center gap-4 py-3">
            <dt class="w-20 flex-shrink-0 text-sm font-medium text-muted-foreground">이메일</dt>
            <dd class="text-sm">{{ authStore.currentUser.email }}</dd>
          </div>
          <div class="flex items-center gap-4 py-3">
            <dt class="w-20 flex-shrink-0 text-sm font-medium text-muted-foreground">닉네임</dt>
            <dd class="text-sm font-medium text-primary">{{ authStore.currentUser.nickname }}</dd>
          </div>
          <div class="flex items-center gap-4 py-3">
            <dt class="w-20 flex-shrink-0 text-sm font-medium text-muted-foreground">역할</dt>
            <dd class="text-sm">
              <span class="inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold">
                {{ authStore.currentUser.role }}
              </span>
            </dd>
          </div>
          <div v-if="authStore.currentUser.createdAt" class="flex items-center gap-4 py-3 last:pb-0">
            <dt class="w-20 flex-shrink-0 text-sm font-medium text-muted-foreground">가입일</dt>
            <dd class="text-sm text-muted-foreground">{{ authStore.currentUser.createdAt }}</dd>
          </div>
        </dl>
      </CardContent>
    </Card>

    <!-- 에러 -->
    <div
      v-else
      role="alert"
      aria-live="polite"
      class="rounded-md bg-destructive/10 px-4 py-3 text-sm text-destructive"
    >
      사용자 정보를 불러올 수 없습니다.
    </div>
  </div>
</template>
