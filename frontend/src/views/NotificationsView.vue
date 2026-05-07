<script setup lang="ts">
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import type { Notification } from "@/stores/notifications";
import { useNotificationsStore } from "@/stores/notifications";
import { Bell, BellOff } from "lucide-vue-next";
import { computed } from "vue";
import { RouterLink } from "vue-router";

const store = useNotificationsStore();
const groups = computed(() => store.groupByDate());

const typeIcon: Record<string, string> = {
	welcome: "🎉",
	dm: "💬",
	reservation: "🏨",
	review: "⭐",
	system: "🔔",
};

function relativeTime(iso: string): string {
	const diff = Date.now() - new Date(iso).getTime();
	const mins = Math.floor(diff / 60000);
	if (mins < 1) return "방금 전";
	if (mins < 60) return `${mins}분 전`;
	const hours = Math.floor(mins / 60);
	if (hours < 24) return `${hours}시간 전`;
	const days = Math.floor(hours / 24);
	return `${days}일 전`;
}

function handleClick(n: Notification) {
	store.markAsRead(n.id);
}
</script>

<template>
  <div class="max-w-2xl mx-auto space-y-6">
    <!-- 헤더 -->
    <div class="flex items-center justify-between">
      <h1 class="text-2xl font-semibold tracking-tight flex items-center gap-2">
        <Bell class="h-6 w-6" />
        알림
        <Badge v-if="store.unreadCount > 0" class="ml-1">{{ store.unreadCount }}</Badge>
      </h1>
      <Button
        v-if="store.unreadCount > 0"
        variant="ghost"
        size="sm"
        class="text-sm"
        @click="store.markAllAsRead()"
      >
        <BellOff class="mr-1.5 h-4 w-4" />
        전체 읽음 처리
      </Button>
    </div>

    <!-- 빈 상태 -->
    <div
      v-if="store.notifications.length === 0"
      class="flex flex-col items-center py-20 gap-3 text-center"
      role="status"
    >
      <span class="text-5xl" aria-hidden="true">🔔</span>
      <p class="font-medium">새로운 알림이 없습니다.</p>
    </div>

    <template v-else>
      <!-- 오늘 -->
      <section v-if="groups.today.length > 0" aria-labelledby="notif-today">
        <h2 id="notif-today" class="text-xs font-semibold uppercase text-muted-foreground mb-2 px-1">오늘</h2>
        <ul class="space-y-2">
          <li
            v-for="n in groups.today"
            :key="n.id"
          >
            <component
              :is="n.link ? RouterLink : 'div'"
              :to="n.link"
              class="block"
              @click="handleClick(n)"
            >
              <Card
                class="transition-shadow hover:shadow-md"
                :class="{ 'bg-primary/5 border-primary/20': !n.isRead }"
              >
                <CardContent class="p-4 flex gap-3">
                  <span class="text-2xl shrink-0 mt-0.5" aria-hidden="true">{{ typeIcon[n.type] ?? '🔔' }}</span>
                  <div class="min-w-0 flex-1">
                    <div class="flex items-start justify-between gap-2">
                      <p class="font-medium text-sm" :class="{ 'text-foreground': !n.isRead, 'text-muted-foreground': n.isRead }">
                        {{ n.title }}
                      </p>
                      <div class="flex items-center gap-2 shrink-0">
                        <span
                          v-if="!n.isRead"
                          class="h-2 w-2 rounded-full bg-primary"
                          aria-label="미읽음"
                        />
                        <time class="text-xs text-muted-foreground">{{ relativeTime(n.createdAt) }}</time>
                      </div>
                    </div>
                    <p class="text-sm text-muted-foreground mt-0.5 line-clamp-2">{{ n.body }}</p>
                  </div>
                </CardContent>
              </Card>
            </component>
          </li>
        </ul>
      </section>

      <!-- 어제 -->
      <section v-if="groups.yesterday.length > 0" aria-labelledby="notif-yesterday">
        <h2 id="notif-yesterday" class="text-xs font-semibold uppercase text-muted-foreground mb-2 px-1">어제</h2>
        <ul class="space-y-2">
          <li
            v-for="n in groups.yesterday"
            :key="n.id"
          >
            <component
              :is="n.link ? RouterLink : 'div'"
              :to="n.link"
              class="block"
              @click="handleClick(n)"
            >
              <Card
                class="transition-shadow hover:shadow-md"
                :class="{ 'bg-primary/5 border-primary/20': !n.isRead }"
              >
                <CardContent class="p-4 flex gap-3">
                  <span class="text-2xl shrink-0 mt-0.5" aria-hidden="true">{{ typeIcon[n.type] ?? '🔔' }}</span>
                  <div class="min-w-0 flex-1">
                    <div class="flex items-start justify-between gap-2">
                      <p class="font-medium text-sm" :class="{ 'text-foreground': !n.isRead, 'text-muted-foreground': n.isRead }">
                        {{ n.title }}
                      </p>
                      <div class="flex items-center gap-2 shrink-0">
                        <span v-if="!n.isRead" class="h-2 w-2 rounded-full bg-primary" aria-label="미읽음" />
                        <time class="text-xs text-muted-foreground">{{ relativeTime(n.createdAt) }}</time>
                      </div>
                    </div>
                    <p class="text-sm text-muted-foreground mt-0.5 line-clamp-2">{{ n.body }}</p>
                  </div>
                </CardContent>
              </Card>
            </component>
          </li>
        </ul>
      </section>

      <!-- 이전 -->
      <section v-if="groups.earlier.length > 0" aria-labelledby="notif-earlier">
        <h2 id="notif-earlier" class="text-xs font-semibold uppercase text-muted-foreground mb-2 px-1">이전</h2>
        <ul class="space-y-2">
          <li
            v-for="n in groups.earlier"
            :key="n.id"
          >
            <component
              :is="n.link ? RouterLink : 'div'"
              :to="n.link"
              class="block"
              @click="handleClick(n)"
            >
              <Card
                class="transition-shadow hover:shadow-md"
                :class="{ 'bg-primary/5 border-primary/20': !n.isRead }"
              >
                <CardContent class="p-4 flex gap-3">
                  <span class="text-2xl shrink-0 mt-0.5" aria-hidden="true">{{ typeIcon[n.type] ?? '🔔' }}</span>
                  <div class="min-w-0 flex-1">
                    <div class="flex items-start justify-between gap-2">
                      <p class="font-medium text-sm" :class="{ 'text-foreground': !n.isRead, 'text-muted-foreground': n.isRead }">
                        {{ n.title }}
                      </p>
                      <div class="flex items-center gap-2 shrink-0">
                        <span v-if="!n.isRead" class="h-2 w-2 rounded-full bg-primary" aria-label="미읽음" />
                        <time class="text-xs text-muted-foreground">{{ relativeTime(n.createdAt) }}</time>
                      </div>
                    </div>
                    <p class="text-sm text-muted-foreground mt-0.5 line-clamp-2">{{ n.body }}</p>
                  </div>
                </CardContent>
              </Card>
            </component>
          </li>
        </ul>
      </section>
    </template>
  </div>
</template>
