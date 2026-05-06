<template>
  <main class="h-screen flex flex-col bg-slate-50">
    <!-- 헤더 -->
    <div class="bg-white border-b border-slate-200 px-4 py-3 flex items-center gap-3">
      <RouterLink to="/chat" class="text-slate-400 hover:text-slate-600 transition-colors">
        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
        </svg>
      </RouterLink>
      <div class="w-8 h-8 rounded-full bg-sky-100 flex items-center justify-center">
        <span class="text-sky-600 font-bold text-sm">#</span>
      </div>
      <div>
        <h1 class="font-semibold text-slate-900">{{ chatStore.activeChannel?.name ?? '' }}</h1>
        <p class="text-xs text-slate-500">{{ chatStore.activeChannel?.memberCount.toLocaleString() }}명 참여중</p>
      </div>
    </div>

    <!-- 채널 없음 -->
    <div v-if="!chatStore.activeChannel" class="flex-1 flex items-center justify-center">
      <p class="text-slate-400">채널을 찾을 수 없습니다.</p>
    </div>

    <template v-else>
      <!-- 메시지 목록 -->
      <div ref="messagesEl" class="flex-1 overflow-y-auto px-4 py-4 flex flex-col gap-3">
        <div
          v-for="msg in chatStore.activeMessages"
          :key="msg.id"
          class="flex items-start gap-3"
        >
          <div class="w-8 h-8 rounded-full bg-slate-200 flex items-center justify-center shrink-0 mt-0.5">
            <span class="text-slate-600 text-xs font-medium">{{ msg.authorName[0] }}</span>
          </div>
          <div>
            <div class="flex items-baseline gap-2">
              <span class="text-sm font-medium text-slate-900">{{ msg.authorName }}</span>
              <span class="text-xs text-slate-400">{{ formatTime(msg.createdAt) }}</span>
            </div>
            <p class="text-sm text-slate-700 mt-0.5">{{ msg.content }}</p>
          </div>
        </div>
      </div>

      <!-- 메시지 입력 -->
      <div class="bg-white border-t border-slate-200 px-4 py-3">
        <form class="flex gap-2" @submit.prevent="handleSend">
          <input
            v-model="inputText"
            type="text"
            :placeholder="`#${chatStore.activeChannel.name} 채널에 메시지 보내기`"
            class="flex-1 px-4 py-2 border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-sky-500 text-sm"
          />
          <button
            type="submit"
            :disabled="!inputText.trim()"
            class="bg-sky-500 text-white px-4 py-2 rounded-lg font-medium hover:bg-sky-600 disabled:opacity-40 disabled:cursor-not-allowed transition-colors text-sm"
          >
            전송
          </button>
        </form>
      </div>
    </template>
  </main>
</template>

<script setup lang="ts">
import { useChatStore } from "@/stores/chat";
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
