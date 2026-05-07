<script setup lang="ts">
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import type { Notification } from "@/stores/notifications";
import { useNotificationsStore } from "@/stores/notifications";
import { Bell, BellOff, CheckCheck } from "lucide-vue-next";
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
        <Bell class="h-6 w-6" aria-hidden="true" />
        알림
        <Badge v-if="store.unreadCount > 0" variant="destructive" class="ml-1 tabular-nums">
          {{ store.unreadCount }}
        </Badge>
      </h1>
      <Button
        v-if="store.unreadCount > 0"
        variant="outline"
        size="sm"
        class="text-sm gap-1.5"
        @click="store.markAllAsRead()"
      >
        <CheckCheck class="h-4 w-4" aria-hidden="true" />
        전체 읽음
      </Button>
    </div>

    <!-- 빈 상태 -->
    <div
      v-if="store.notifications.length === 0"
      class="flex flex-col items-center py-24 gap-4 text-center"
      role="status"
      aria-label="알림 없음"
    >
      <div class="flex h-16 w-16 items-center justify-center rounded-full bg-muted" aria-hidden="true">
        <BellOff class="h-8 w-8 text-muted-foreground/50" />
      </div>
      <div class="space-y-1">
        <p class="font-medium">새로운 알림이 없습니다.</p>
        <p class="text-sm text-muted-foreground">활동이 생기면 여기에 알림이 표시됩니다.</p>
      </div>
    </div>

    <template v-else>
      <!-- 오늘 -->
      <section v-if="groups.today.length > 0" aria-labelledby="notif-today">
        <h2
          id="notif-today"
          class="sticky top-[57px] z-10 -mx-px mb-2 border-b bg-background/95 px-1 py-2 backdrop-blur
                 text-xs font-semibold uppercase tracking-wider text-muted-foreground"
        >
          오늘
        </h2>
        <ul class="space-y-2">
          <li v-for="n in groups.today" :key="n.id">
            <component
              :is="n.link ? RouterLink : 'div'"
              :to="n.link"
              class="block focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring rounded-xl"
              @click="handleClick(n)"
            >
              <Card
                class="transition-shadow hover:shadow-md"
                :class="!n.isRead ? 'border-l-4 border-l-primary bg-primary/5' : 'border-l-4 border-l-transparent'"
              >
                <CardContent class="p-4 flex gap-3">
                  <!-- 타입 아이콘 -->
                  <span
                    class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-lg"
                    :class="!n.isRead ? 'bg-primary/10' : 'bg-muted'"
                    aria-hidden="true"
                  >{{ typeIcon[n.type] ?? "🔔" }}</span>
                  <div class="min-w-0 flex-1">
                    <div class="flex items-start justify-between gap-2">
                      <p
                        class="font-medium text-sm leading-snug"
                        :class="!n.isRead ? 'text-foreground' : 'text-muted-foreground'"
                      >{{ n.title }}</p>
                      <div class="flex items-center gap-1.5 shrink-0">
                        <span
                          v-if="!n.isRead"
                          class="h-2 w-2 rounded-full bg-primary ring-2 ring-background"
                          aria-label="미읽음"
                        />
                        <time class="text-xs text-muted-foreground tabular-nums">{{ relativeTime(n.createdAt) }}</time>
                      </div>
                    </div>
                    <p class="text-sm text-muted-foreground mt-0.5 line-clamp-2 leading-relaxed">{{ n.body }}</p>
                  </div>
                </CardContent>
              </Card>
            </component>
          </li>
        </ul>
      </section>

      <!-- 어제 -->
      <section v-if="groups.yesterday.length > 0" aria-labelledby="notif-yesterday">
        <h2
          id="notif-yesterday"
          class="sticky top-[57px] z-10 -mx-px mb-2 border-b bg-background/95 px-1 py-2 backdrop-blur
                 text-xs font-semibold uppercase tracking-wider text-muted-foreground"
        >
          어제
        </h2>
        <ul class="space-y-2">
          <li v-for="n in groups.yesterday" :key="n.id">
            <component
              :is="n.link ? RouterLink : 'div'"
              :to="n.link"
              class="block focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring rounded-xl"
              @click="handleClick(n)"
            >
              <Card
                class="transition-shadow hover:shadow-md"
                :class="!n.isRead ? 'border-l-4 border-l-primary bg-primary/5' : 'border-l-4 border-l-transparent'"
              >
                <CardContent class="p-4 flex gap-3">
                  <!-- 타입 아이콘 -->
                  <span
                    class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-lg"
                    :class="!n.isRead ? 'bg-primary/10' : 'bg-muted'"
                    aria-hidden="true"
                  >{{ typeIcon[n.type] ?? "🔔" }}</span>
                  <div class="min-w-0 flex-1">
                    <div class="flex items-start justify-between gap-2">
                      <p
                        class="font-medium text-sm leading-snug"
                        :class="!n.isRead ? 'text-foreground' : 'text-muted-foreground'"
                      >{{ n.title }}</p>
                      <div class="flex items-center gap-1.5 shrink-0">
                        <span
                          v-if="!n.isRead"
                          class="h-2 w-2 rounded-full bg-primary ring-2 ring-background"
                          aria-label="미읽음"
                        />
                        <time class="text-xs text-muted-foreground tabular-nums">{{ relativeTime(n.createdAt) }}</time>
                      </div>
                    </div>
                    <p class="text-sm text-muted-foreground mt-0.5 line-clamp-2 leading-relaxed">{{ n.body }}</p>
                  </div>
                </CardContent>
              </Card>
            </component>
          </li>
        </ul>
      </section>

      <!-- 이전 -->
      <section v-if="groups.earlier.length > 0" aria-labelledby="notif-earlier">
        <h2
          id="notif-earlier"
          class="sticky top-[57px] z-10 -mx-px mb-2 border-b bg-background/95 px-1 py-2 backdrop-blur
                 text-xs font-semibold uppercase tracking-wider text-muted-foreground"
        >
          이전
        </h2>
        <ul class="space-y-2">
          <li v-for="n in groups.earlier" :key="n.id">
            <component
              :is="n.link ? RouterLink : 'div'"
              :to="n.link"
              class="block focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring rounded-xl"
              @click="handleClick(n)"
            >
              <Card
                class="transition-shadow hover:shadow-md"
                :class="!n.isRead ? 'border-l-4 border-l-primary bg-primary/5' : 'border-l-4 border-l-transparent'"
              >
                <CardContent class="p-4 flex gap-3">
                  <!-- 타입 아이콘 -->
                  <span
                    class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-lg"
                    :class="!n.isRead ? 'bg-primary/10' : 'bg-muted'"
                    aria-hidden="true"
                  >{{ typeIcon[n.type] ?? "🔔" }}</span>
                  <div class="min-w-0 flex-1">
                    <div class="flex items-start justify-between gap-2">
                      <p
                        class="font-medium text-sm leading-snug"
                        :class="!n.isRead ? 'text-foreground' : 'text-muted-foreground'"
                      >{{ n.title }}</p>
                      <div class="flex items-center gap-1.5 shrink-0">
                        <span
                          v-if="!n.isRead"
                          class="h-2 w-2 rounded-full bg-primary ring-2 ring-background"
                          aria-label="미읽음"
                        />
                        <time class="text-xs text-muted-foreground tabular-nums">{{ relativeTime(n.createdAt) }}</time>
                      </div>
                    </div>
                    <p class="text-sm text-muted-foreground mt-0.5 line-clamp-2 leading-relaxed">{{ n.body }}</p>
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
