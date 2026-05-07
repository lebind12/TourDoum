<script setup lang="ts">
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Popover } from "@/components/ui/popover";
import { Skeleton } from "@/components/ui/skeleton";
import { useNotificationsStore } from "@/stores/notifications";
import { Bell, BellOff, CheckCheck } from "lucide-vue-next";
import { ref } from "vue";
import { RouterLink } from "vue-router";

const store = useNotificationsStore();
const open = ref(false);

const typeIcon: Record<string, string> = {
	review_reply: "⭐",
	reservation_confirmed: "🏨",
	reservation_canceled: "❌",
	system: "🔔",
};

function toggle() {
	open.value = !open.value;
}

function handleItemClick(id: string) {
	store.markAsRead(id);
	open.value = false;
}
</script>

<template>
  <Popover v-model:open="open" align="end" width="w-80">
    <!-- 트리거: 종 아이콘 + 미읽음 배지 -->
    <template #trigger>
      <Button
        variant="ghost"
        size="icon"
        :aria-label="`알림${store.unreadCount > 0 ? ` (미읽음 ${store.unreadCount}개)` : ''}`"
        :aria-expanded="open"
        data-testid="notification-bell"
        class="relative"
        @click="toggle"
      >
        <!-- 종 아이콘 — 미읽음 시 흔들림 애니메이션 -->
        <Bell
          class="h-4 w-4 transition-transform"
          :class="store.unreadCount > 0 ? 'animate-[wiggle_1s_ease-in-out_2]' : ''"
        />
        <!-- 미읽음 뱃지 -->
        <span
          v-if="store.unreadCount > 0"
          class="absolute -top-0.5 -right-0.5 flex h-4 min-w-4 items-center justify-center rounded-full
                 bg-destructive px-0.5 text-[10px] font-bold text-destructive-foreground
                 ring-2 ring-background transition-all"
          aria-hidden="true"
        >
          {{ store.unreadCount > 9 ? "9+" : store.unreadCount }}
        </span>
      </Button>
    </template>

    <!-- 패널 콘텐츠 -->
    <template #content>
      <!-- 헤더 -->
      <div class="flex items-center justify-between px-4 py-3 border-b">
        <div class="flex items-center gap-2">
          <Bell class="h-4 w-4 text-muted-foreground" aria-hidden="true" />
          <p class="font-semibold text-sm">알림</p>
          <Badge v-if="store.unreadCount > 0" variant="destructive" class="text-xs px-1.5 py-0">
            {{ store.unreadCount }}
          </Badge>
        </div>
        <!-- 모두 읽음 -->
        <button
          v-if="store.unreadCount > 0"
          type="button"
          class="inline-flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground transition-colors
                 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring rounded"
          @click="store.markAllAsRead()"
        >
          <CheckCheck :size="13" aria-hidden="true" />
          전체 읽음
        </button>
      </div>

      <!-- 알림 목록 -->
      <ul class="divide-y max-h-[340px] overflow-y-auto" role="list" aria-label="최근 알림 목록">
        <!-- 빈 상태 -->
        <li v-if="store.recent.length === 0" class="flex flex-col items-center py-8 gap-2 text-center">
          <BellOff class="h-8 w-8 text-muted-foreground/40" aria-hidden="true" />
          <p class="text-sm text-muted-foreground">알림이 없습니다.</p>
        </li>

        <li v-for="n in store.recent" :key="n.id" role="listitem">
          <component
            :is="n.link ? RouterLink : 'div'"
            :to="n.link"
            class="flex gap-3 px-4 py-3 hover:bg-muted/50 transition-colors cursor-pointer
                   focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring"
            :class="!n.isRead ? 'bg-primary/5 border-l-2 border-l-primary' : ''"
            @click="handleItemClick(n.id)"
          >
            <!-- 타입 아이콘 -->
            <span
              class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-base"
              :class="!n.isRead ? 'bg-primary/10' : 'bg-muted'"
              aria-hidden="true"
            >
              {{ typeIcon[n.type] ?? "🔔" }}
            </span>

            <div class="min-w-0 flex-1">
              <p
                class="text-sm font-medium truncate leading-tight"
                :class="!n.isRead ? 'text-foreground' : 'text-muted-foreground'"
              >
                {{ n.title }}
              </p>
              <p class="text-xs text-muted-foreground truncate mt-0.5 leading-tight">{{ n.body }}</p>
            </div>

            <!-- 미읽음 점 -->
            <span
              v-if="!n.isRead"
              class="h-2 w-2 rounded-full bg-primary shrink-0 mt-1 ring-2 ring-background"
              aria-label="미읽음"
            />
          </component>
        </li>
      </ul>

      <!-- 푸터 -->
      <div class="border-t p-2">
        <RouterLink :to="{ name: 'notifications' }" class="block" @click="open = false">
          <Button variant="ghost" size="sm" class="w-full text-xs gap-1">
            전체 알림 보기
          </Button>
        </RouterLink>
      </div>
    </template>
  </Popover>
</template>
