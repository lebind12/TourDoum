<script setup lang="ts">
import NotificationDrawer from "@/components/layout/NotificationDrawer.vue";
import SearchWidget from "@/components/layout/SearchWidget.vue";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { Sheet, SheetHeader } from "@/components/ui/sheet";
import { ThemeToggle } from "@/components/ui/theme-toggle";
import { Toaster } from "@/components/ui/toast";
import { useAuthStore } from "@/stores/auth";
import { useNotificationsStore } from "@/stores/notifications";
import { Menu, X } from "lucide-vue-next";
import { ref, watch } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";

const authStore = useAuthStore();
const notificationsStore = useNotificationsStore();
const router = useRouter();
const route = useRoute();

/** 현재 경로가 주어진 prefix로 시작하면 활성 nav 스타일 적용 */
function navClass(prefix: string): string {
	return route.path.startsWith(prefix) ? "text-primary bg-primary/5" : "";
}
const drawerOpen = ref(false);

// 로그인 상태에 따라 폴링 시작/중단
watch(
	() => authStore.currentUser,
	(user) => {
		if (user) {
			notificationsStore.startPolling();
		} else {
			notificationsStore.stopPolling();
		}
	},
	{ immediate: true },
);

async function handleLogout() {
	notificationsStore.stopPolling();
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
    <!-- Skip-to-content: 키보드/스크린리더 사용자 접근성 -->
    <a
      href="#main-content"
      class="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-[9999] focus:rounded-md focus:bg-background focus:px-4 focus:py-2 focus:text-sm focus:font-medium focus:text-foreground focus:shadow-md focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2 focus:ring-offset-background"
    >
      본문으로 건너뛰기
    </a>

    <!-- Header -->
    <header class="sticky top-0 z-50 w-full border-b border-border bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60">
      <div class="container flex h-14 items-center">
        <!-- Logo -->
        <RouterLink to="/" class="flex items-center gap-2 font-bold text-lg text-primary">
          TourDoum
        </RouterLink>

        <!-- Domain nav (≥768px) — 항상 노출 -->
        <nav aria-label="주요 메뉴" class="hidden md:flex items-center gap-1 ml-6">
          <RouterLink to="/attractions" :aria-current="route.path.startsWith('/attractions') ? 'page' : undefined">
            <Button variant="ghost" size="sm" :class="navClass('/attractions')">여행지</Button>
          </RouterLink>
          <RouterLink to="/accommodations" :aria-current="route.path.startsWith('/accommodations') ? 'page' : undefined">
            <Button variant="ghost" size="sm" :class="navClass('/accommodations')">숙박</Button>
          </RouterLink>
          <RouterLink to="/chat" :aria-current="route.path.startsWith('/chat') ? 'page' : undefined">
            <Button variant="ghost" size="sm" :class="navClass('/chat')">채팅</Button>
          </RouterLink>
          <RouterLink v-if="authStore.currentUser" to="/favorites" :aria-current="route.path.startsWith('/favorites') ? 'page' : undefined">
            <Button variant="ghost" size="sm" :class="navClass('/favorites')">즐겨찾기</Button>
          </RouterLink>
        </nav>

        <!-- Spacer -->
        <div class="flex-1" />

        <!-- Auth nav (≥768px) -->
        <nav class="hidden md:flex items-center gap-2">
          <!-- 검색 위젯 -->
          <SearchWidget />
          <!-- 알림 (로그인 시) -->
          <NotificationDrawer v-if="authStore.currentUser" />
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
          <SearchWidget />
          <NotificationDrawer v-if="authStore.currentUser" />
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

      <nav aria-label="모바일 주요 메뉴" class="flex flex-col gap-1 p-4">
        <!-- Domain section -->
        <p class="px-3 py-2 text-xs font-semibold text-muted-foreground uppercase">
          탐색
        </p>
        <RouterLink to="/search" @click="closeDrawer">
          <Button variant="ghost" class="w-full justify-start" size="sm">🔍 검색</Button>
        </RouterLink>
        <RouterLink to="/attractions" :aria-current="route.path.startsWith('/attractions') ? 'page' : undefined" @click="closeDrawer">
          <Button variant="ghost" class="w-full justify-start" size="sm" :class="navClass('/attractions')">여행지</Button>
        </RouterLink>
        <RouterLink to="/accommodations" :aria-current="route.path.startsWith('/accommodations') ? 'page' : undefined" @click="closeDrawer">
          <Button variant="ghost" class="w-full justify-start" size="sm" :class="navClass('/accommodations')">숙박</Button>
        </RouterLink>
        <RouterLink to="/chat" :aria-current="route.path.startsWith('/chat') ? 'page' : undefined" @click="closeDrawer">
          <Button variant="ghost" class="w-full justify-start" size="sm" :class="navClass('/chat')">채팅</Button>
        </RouterLink>
        <RouterLink v-if="authStore.currentUser" to="/favorites" :aria-current="route.path.startsWith('/favorites') ? 'page' : undefined" @click="closeDrawer">
          <Button variant="ghost" class="w-full justify-start" size="sm" :class="navClass('/favorites')">즐겨찾기</Button>
        </RouterLink>

        <Separator class="my-2" />

        <!-- Auth section -->
        <template v-if="authStore.currentUser">
          <p class="px-3 py-2 text-sm font-medium text-muted-foreground">
            {{ authStore.currentUser.nickname }}님
          </p>
          <RouterLink to="/notifications" @click="closeDrawer">
            <Button variant="ghost" class="w-full justify-start" size="sm">🔔 알림</Button>
          </RouterLink>
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
    <main
      id="main-content"
      tabindex="-1"
      :class="route.path === '/'
        ? 'flex-1 flex flex-col overflow-hidden focus:outline-none'
        : 'flex-1 container py-6 focus:outline-none'"
    >
      <slot />
    </main>

    <!-- Footer (랜딩 페이지 / 에서는 숨김 — 랜딩 자체 CTA 섹션이 대체) -->
    <footer v-if="route.path !== '/'" class="border-t border-border">
      <div class="container flex h-12 items-center justify-center">
        <Separator class="hidden" />
        <p class="text-xs text-muted-foreground">© 2025 TourDoum. SSAFY 특화 프로젝트.</p>
      </div>
    </footer>

    <!-- 글로벌 Toast 알림 -->
    <Toaster />
  </div>
</template>
