<script setup lang="ts">
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { useNotificationsStore } from "@/stores/notifications";
import { Bell } from "lucide-vue-next";
import { ref } from "vue";
import { RouterLink } from "vue-router";

const store = useNotificationsStore();
const open = ref(false);

const typeIcon: Record<string, string> = {
	welcome: "🎉",
	dm: "💬",
	reservation: "🏨",
	review: "⭐",
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
  <div class="relative">
    <!-- 종 아이콘 버튼 -->
    <Button
      variant="ghost"
      size="icon"
      aria-label="알림"
      data-testid="notification-bell"
      @click="toggle"
    >
      <Bell class="h-4 w-4" />
      <!-- 미읽음 배지 -->
      <span
        v-if="store.unreadCount > 0"
        class="absolute -top-0.5 -right-0.5 flex h-4 w-4 items-center justify-center rounded-full bg-destructive text-[10px] font-bold text-destructive-foreground"
        aria-label="`미읽음 알림 ${store.unreadCount}개`"
      >
        {{ store.unreadCount > 9 ? '9+' : store.unreadCount }}
      </span>
    </Button>

    <!-- Popover 패널 -->
    <div
      v-if="open"
      class="absolute right-0 top-full mt-2 w-80 rounded-xl border bg-popover shadow-lg z-50"
      role="dialog"
      aria-label="최근 알림"
    >
      <!-- 헤더 -->
      <div class="flex items-center justify-between px-4 py-3 border-b">
        <p class="font-semibold text-sm">알림</p>
        <Badge v-if="store.unreadCount > 0" variant="destructive" class="text-xs">
          {{ store.unreadCount }} 미읽음
        </Badge>
      </div>

      <!-- 최근 5개 목록 -->
      <ul class="divide-y max-h-[340px] overflow-y-auto">
        <li v-if="store.recent.length === 0" class="py-8 text-center text-sm text-muted-foreground">
          알림이 없습니다.
        </li>
        <li v-for="n in store.recent" :key="n.id">
          <component
            :is="n.link ? RouterLink : 'div'"
            :to="n.link"
            class="flex gap-3 px-4 py-3 hover:bg-muted/50 transition-colors cursor-pointer"
            :class="{ 'bg-primary/5': !n.isRead }"
            @click="handleItemClick(n.id)"
          >
            <span class="text-xl shrink-0 mt-0.5" aria-hidden="true">{{ typeIcon[n.type] ?? '🔔' }}</span>
            <div class="min-w-0 flex-1">
              <p
                class="text-sm font-medium truncate"
                :class="{ 'text-foreground': !n.isRead, 'text-muted-foreground': n.isRead }"
              >
                {{ n.title }}
              </p>
              <p class="text-xs text-muted-foreground truncate">{{ n.body }}</p>
            </div>
            <span v-if="!n.isRead" class="h-2 w-2 rounded-full bg-primary shrink-0 mt-1.5" />
          </component>
        </li>
      </ul>

      <!-- 푸터: 전체 보기 -->
      <div class="border-t p-2">
        <RouterLink :to="{ name: 'notifications' }" class="block" @click="open = false">
          <Button variant="ghost" size="sm" class="w-full text-xs">전체 알림 보기</Button>
        </RouterLink>
      </div>
    </div>

    <!-- 외부 클릭 시 닫기 오버레이 -->
    <div
      v-if="open"
      class="fixed inset-0 z-40"
      aria-hidden="true"
      @click="open = false"
    />
  </div>
</template>
