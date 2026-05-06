<script setup lang="ts">
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { useAuthStore } from "@/stores/auth";
import { RouterLink, useRouter } from "vue-router";

const authStore = useAuthStore();
const router = useRouter();

async function handleLogout() {
	await authStore.logout();
	router.push("/");
}
</script>

<template>
  <div class="min-h-screen bg-background flex flex-col">
    <!-- Header -->
    <header class="sticky top-0 z-50 w-full border-b border-border bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60">
      <div class="container flex h-14 items-center">
        <!-- Logo -->
        <RouterLink to="/" class="flex items-center gap-2 font-bold text-lg text-primary">
          TourDoum
        </RouterLink>

        <!-- Spacer -->
        <div class="flex-1" />

        <!-- Nav actions -->
        <nav class="flex items-center gap-2">
          <template v-if="authStore.currentUser">
            <span class="text-sm text-muted-foreground hidden sm:inline">
              {{ authStore.currentUser.nickname }}님
            </span>
            <RouterLink to="/me">
              <Button variant="ghost" size="sm">내 정보</Button>
            </RouterLink>
            <Button
              variant="outline"
              size="sm"
              :disabled="authStore.loading"
              @click="handleLogout"
            >
              {{ authStore.loading ? '처리 중...' : '로그아웃' }}
            </Button>
          </template>
          <template v-else>
            <RouterLink to="/login">
              <Button variant="ghost" size="sm">로그인</Button>
            </RouterLink>
            <RouterLink to="/signup">
              <Button size="sm">회원가입</Button>
            </RouterLink>
          </template>
        </nav>
      </div>
    </header>

    <!-- Main content -->
    <main class="flex-1 container py-6">
      <slot />
    </main>

    <!-- Footer -->
    <footer class="border-t border-border">
      <div class="container flex h-12 items-center justify-center">
        <Separator class="hidden" />
        <p class="text-xs text-muted-foreground">© 2025 TourDoum. SSAFY 특화 프로젝트.</p>
      </div>
    </footer>
  </div>
</template>
