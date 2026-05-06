<script setup lang="ts">
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { Sheet, SheetHeader } from "@/components/ui/sheet";
import { ThemeToggle } from "@/components/ui/theme-toggle";
import { useAuthStore } from "@/stores/auth";
import { Menu, X } from "lucide-vue-next";
import { ref } from "vue";
import { RouterLink, useRouter } from "vue-router";

const authStore = useAuthStore();
const router = useRouter();
const drawerOpen = ref(false);

async function handleLogout() {
	await authStore.logout();
	drawerOpen.value = false;
	router.push("/");
}

function closeDrawer() {
	drawerOpen.value = false;
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

        <!-- Desktop nav (≥768px) -->
        <nav class="hidden md:flex items-center gap-2">
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
          <ThemeToggle />
        </nav>

        <!-- Mobile: ThemeToggle + Hamburger (<768px) -->
        <div class="flex md:hidden items-center gap-1">
          <ThemeToggle />
          <Button
            variant="ghost"
            size="icon"
            aria-label="메뉴 열기"
            @click="drawerOpen = true"
          >
            <Menu class="h-5 w-5" />
          </Button>
        </div>
      </div>
    </header>

    <!-- Mobile slide drawer -->
    <Sheet v-model:open="drawerOpen" side="left">
      <SheetHeader>
        <RouterLink
          to="/"
          class="font-bold text-base text-primary"
          @click="closeDrawer"
        >
          TourDoum
        </RouterLink>
        <Button
          variant="ghost"
          size="icon"
          aria-label="메뉴 닫기"
          @click="drawerOpen = false"
        >
          <X class="h-4 w-4" />
        </Button>
      </SheetHeader>

      <nav class="flex flex-col gap-1 p-4">
        <template v-if="authStore.currentUser">
          <p class="px-3 py-2 text-sm font-medium text-muted-foreground">
            {{ authStore.currentUser.nickname }}님
          </p>
          <RouterLink to="/me" @click="closeDrawer">
            <Button variant="ghost" class="w-full justify-start" size="sm">내 정보</Button>
          </RouterLink>
          <Button
            variant="ghost"
            class="w-full justify-start"
            size="sm"
            :disabled="authStore.loading"
            @click="handleLogout"
          >
            {{ authStore.loading ? '처리 중...' : '로그아웃' }}
          </Button>
        </template>
        <template v-else>
          <RouterLink to="/login" @click="closeDrawer">
            <Button variant="ghost" class="w-full justify-start" size="sm">로그인</Button>
          </RouterLink>
          <RouterLink to="/signup" @click="closeDrawer">
            <Button class="w-full justify-start" size="sm">회원가입</Button>
          </RouterLink>
        </template>
      </nav>
    </Sheet>

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
