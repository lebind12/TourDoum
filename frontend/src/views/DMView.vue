<script setup lang="ts">
import { Avatar } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useChatStore } from "@/stores/chat";
import { ChevronLeft, Send } from "lucide-vue-next";
import { nextTick, onMounted, ref, watch } from "vue";
import { RouterLink, useRoute } from "vue-router";

const route = useRoute();
const chatStore = useChatStore();
const inputText = ref("");
const messagesEl = ref<HTMLElement | null>(null);

const partnerId = Number(route.params.userId);

function formatTime(iso: string): string {
	const d = new Date(iso);
	return `${d.getHours().toString().padStart(2, "0")}:${d.getMinutes().toString().padStart(2, "0")}`;
}

async function handleSend() {
	const text = inputText.value.trim();
	if (!text) return;
	inputText.value = "";
	await chatStore.sendDm(partnerId, text);
	await nextTick();
	scrollToBottom();
}

function scrollToBottom() {
	if (messagesEl.value) {
		messagesEl.value.scrollTop = messagesEl.value.scrollHeight;
	}
}

watch(
	() => chatStore.activeDmThread?.messages.length,
	async () => {
		await nextTick();
		scrollToBottom();
	},
);

onMounted(() => {
	chatStore.setActiveDm(partnerId);
	nextTick(scrollToBottom);
});
</script>

<template>
  <div class="h-[calc(100vh-4rem)] flex flex-col -mx-4 sm:-mx-6 lg:-mx-8">
    <!-- 헤더 -->
    <header class="bg-background border-b border-border px-4 py-3 flex items-center gap-3 shrink-0">
      <RouterLink to="/chat" aria-label="채팅 목록으로 돌아가기">
        <Button variant="ghost" size="icon" class="shrink-0">
          <ChevronLeft class="w-5 h-5" />
        </Button>
      </RouterLink>

      <template v-if="chatStore.activeDmThread">
        <Avatar :name="chatStore.activeDmThread.partnerName" variant="emerald" size="sm" />
        <div>
          <div class="flex items-center gap-2">
            <h1 class="font-semibold text-sm">{{ chatStore.activeDmThread.partnerName }}</h1>
            <Badge variant="emerald">호스트</Badge>
          </div>
          <p class="text-xs text-muted-foreground">{{ chatStore.activeDmThread.accommodationName }}</p>
        </div>
      </template>
      <template v-else>
        <h1 class="font-semibold text-sm">다이렉트 메시지</h1>
      </template>
    </header>

    <!-- 스레드 없음 -->
    <div v-if="!chatStore.activeDmThread" class="flex-1 flex flex-col items-center justify-center gap-3">
      <p class="text-muted-foreground">대화 상대를 찾을 수 없습니다.</p>
      <RouterLink to="/accommodations">
        <Button variant="link">숙박 목록에서 호스트에게 메시지 보내기</Button>
      </RouterLink>
    </div>

    <template v-else>
      <!-- 메시지 목록 -->
      <div ref="messagesEl" class="flex-1 overflow-y-auto px-4 py-4 flex flex-col gap-3 bg-muted/20">
        <div
          v-for="msg in chatStore.activeDmThread.messages"
          :key="msg.id"
          :class="['flex items-end gap-2', msg.senderId === 0 ? 'flex-row-reverse' : 'flex-row']"
        >
          <!-- 상대방 아바타 -->
          <Avatar
            v-if="msg.senderId !== 0"
            :name="chatStore.activeDmThread.partnerName"
            variant="emerald"
            size="sm"
          />

          <!-- 말풍선 -->
          <div
            :class="[
              'max-w-xs rounded-2xl px-4 py-2 text-sm leading-relaxed',
              msg.senderId === 0
                ? 'bg-primary text-primary-foreground rounded-br-sm'
                : 'bg-background text-foreground border border-border rounded-bl-sm shadow-sm',
            ]"
          >
            {{ msg.content }}
          </div>

          <!-- 시간 -->
          <span class="text-xs text-muted-foreground shrink-0 mb-0.5">{{ formatTime(msg.createdAt) }}</span>
        </div>
      </div>

      <!-- 입력창 -->
      <div class="bg-background border-t border-border px-4 py-3 shrink-0">
        <form class="flex gap-2" @submit.prevent="handleSend">
          <Input
            v-model="inputText"
            type="text"
            :placeholder="`${chatStore.activeDmThread.partnerName}님께 메시지 보내기`"
            class="flex-1"
            aria-label="메시지 입력"
          />
          <Button
            type="submit"
            size="icon"
            :disabled="!inputText.trim()"
            aria-label="메시지 전송"
          >
            <Send class="w-4 h-4" />
          </Button>
        </form>
      </div>
    </template>
  </div>
</template>
