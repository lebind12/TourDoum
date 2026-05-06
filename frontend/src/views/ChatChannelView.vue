<script setup lang="ts">
import { Avatar } from "@/components/ui/avatar";
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

const channelId = Number(route.params.channelId);

function formatTime(iso: string): string {
	const d = new Date(iso);
	return `${d.getHours().toString().padStart(2, "0")}:${d.getMinutes().toString().padStart(2, "0")}`;
}

async function handleSend() {
	const text = inputText.value.trim();
	if (!text) return;
	inputText.value = "";
	await chatStore.sendMessage(channelId, text);
	await nextTick();
	scrollToBottom();
}

function scrollToBottom() {
	if (messagesEl.value) {
		messagesEl.value.scrollTop = messagesEl.value.scrollHeight;
	}
}

watch(
	() => chatStore.activeMessages.length,
	async () => {
		await nextTick();
		scrollToBottom();
	},
);

onMounted(() => {
	chatStore.setActiveChannel(channelId);
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
      <Avatar name="#" variant="sky" size="sm" />
      <div>
        <h1 class="font-semibold text-sm">{{ chatStore.activeChannel?.name ?? '' }}</h1>
        <p class="text-xs text-muted-foreground">{{ chatStore.activeChannel?.memberCount.toLocaleString() }}명 참여중</p>
      </div>
    </header>

    <!-- 채널 없음 -->
    <div v-if="!chatStore.activeChannel" class="flex-1 flex items-center justify-center">
      <p class="text-muted-foreground">채널을 찾을 수 없습니다.</p>
    </div>

    <template v-else>
      <!-- 메시지 목록 -->
      <div ref="messagesEl" class="flex-1 overflow-y-auto px-4 py-4 flex flex-col gap-4 bg-muted/20">
        <div
          v-for="msg in chatStore.activeMessages"
          :key="msg.id"
          class="flex items-start gap-3"
        >
          <Avatar :name="msg.authorName" variant="slate" size="sm" class="mt-0.5" />
          <div>
            <div class="flex items-baseline gap-2">
              <span class="text-sm font-medium">{{ msg.authorName }}</span>
              <span class="text-xs text-muted-foreground">{{ formatTime(msg.createdAt) }}</span>
            </div>
            <p class="text-sm text-foreground mt-0.5 leading-relaxed">{{ msg.content }}</p>
          </div>
        </div>
      </div>

      <!-- 메시지 입력 -->
      <div class="bg-background border-t border-border px-4 py-3 shrink-0">
        <form class="flex gap-2" @submit.prevent="handleSend">
          <Input
            v-model="inputText"
            type="text"
            :placeholder="`#${chatStore.activeChannel.name} 채널에 메시지 보내기`"
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
