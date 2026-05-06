<template>
  <main class="h-screen flex flex-col bg-slate-50">
    <!-- 헤더 -->
    <div class="bg-white border-b border-slate-200 px-4 py-3 flex items-center gap-3">
      <RouterLink to="/chat" class="text-slate-400 hover:text-slate-600 transition-colors">
        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
        </svg>
      </RouterLink>
      <template v-if="chatStore.activeDmThread">
        <div class="w-8 h-8 rounded-full bg-emerald-100 flex items-center justify-center">
          <span class="text-emerald-600 font-bold text-sm">{{ chatStore.activeDmThread.partnerName[0] }}</span>
        </div>
        <div>
          <div class="flex items-center gap-2">
            <h1 class="font-semibold text-slate-900">{{ chatStore.activeDmThread.partnerName }}</h1>
            <span class="text-xs bg-emerald-100 text-emerald-700 rounded-full px-2">호스트</span>
          </div>
          <p class="text-xs text-slate-500">{{ chatStore.activeDmThread.accommodationName }}</p>
        </div>
      </template>
      <template v-else>
        <h1 class="font-semibold text-slate-900">다이렉트 메시지</h1>
      </template>
    </div>

    <!-- 스레드 없음 -->
    <div v-if="!chatStore.activeDmThread" class="flex-1 flex items-center justify-center flex-col gap-3">
      <p class="text-slate-400">대화 상대를 찾을 수 없습니다.</p>
      <RouterLink to="/accommodations" class="text-sky-500 text-sm hover:underline">
        숙박 목록에서 호스트에게 메시지 보내기
      </RouterLink>
    </div>

    <template v-else>
      <!-- 메시지 목록 -->
      <div ref="messagesEl" class="flex-1 overflow-y-auto px-4 py-4 flex flex-col gap-3">
        <div
          v-for="msg in chatStore.activeDmThread.messages"
          :key="msg.id"
          :class="[
            'flex items-end gap-2',
            msg.senderId === 0 ? 'flex-row-reverse' : 'flex-row'
          ]"
        >
          <!-- 상대방 아바타 -->
          <div
            v-if="msg.senderId !== 0"
            class="w-7 h-7 rounded-full bg-emerald-100 flex items-center justify-center shrink-0"
          >
            <span class="text-emerald-600 text-xs font-medium">
              {{ chatStore.activeDmThread.partnerName[0] }}
            </span>
          </div>

          <!-- 말풍선 -->
          <div
            :class="[
              'max-w-xs rounded-2xl px-4 py-2 text-sm',
              msg.senderId === 0
                ? 'bg-sky-500 text-white rounded-br-sm'
                : 'bg-white text-slate-800 border border-slate-200 rounded-bl-sm'
            ]"
          >
            {{ msg.content }}
          </div>

          <!-- 시간 -->
          <span class="text-xs text-slate-400 shrink-0 mb-0.5">{{ formatTime(msg.createdAt) }}</span>
        </div>
      </div>

      <!-- 입력창 -->
      <div class="bg-white border-t border-slate-200 px-4 py-3">
        <form class="flex gap-2" @submit.prevent="handleSend">
          <input
            v-model="inputText"
            type="text"
            :placeholder="`${chatStore.activeDmThread.partnerName}님께 메시지 보내기`"
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
