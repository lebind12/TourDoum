<script setup lang="ts">
import { Avatar } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useToast } from "@/composables/useToast";
import { useChatStore } from "@/stores/chat";
import { ChevronLeft, Send } from "lucide-vue-next";
import { nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { RouterLink, useRoute } from "vue-router";

const route = useRoute();
const chatStore = useChatStore();
const { error: toastError } = useToast();
const inputText = ref("");
const messagesEl = ref<HTMLElement | null>(null);
const topSentinelEl = ref<HTMLElement | null>(null);

const channelId = String(route.params.channelId);

let _observer: IntersectionObserver | null = null;
let _loadingOlder = false;
let _lastError: string | null = null;

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

/**
 * 상단 sentinel 진입 → fetchOlder() → prepend 후 scrollTop 보정.
 * prepend 로 인해 list height 가 증가한 만큼 scrollTop 을 더해 사용자 시야 고정.
 */
async function loadOlderWithAnchor() {
	if (_loadingOlder) return;
	if (chatStore.hasMoreOlder[channelId] === false) return;
	if (!messagesEl.value) return;
	_loadingOlder = true;
	const prevHeight = messagesEl.value.scrollHeight;
	const prevTop = messagesEl.value.scrollTop;
	const added = await chatStore.fetchOlder(channelId);
	await nextTick();
	if (added > 0 && messagesEl.value) {
		const delta = messagesEl.value.scrollHeight - prevHeight;
		messagesEl.value.scrollTop = prevTop + delta;
	}
	_loadingOlder = false;
}

function setupObserver() {
	if (typeof IntersectionObserver === "undefined") return;
	if (!topSentinelEl.value || !messagesEl.value) return;
	_observer?.disconnect();
	_observer = new IntersectionObserver(
		(entries) => {
			for (const entry of entries) {
				if (entry.isIntersecting) {
					loadOlderWithAnchor();
				}
			}
		},
		{ root: messagesEl.value, threshold: 0.1 },
	);
	_observer.observe(topSentinelEl.value);
}

watch(
	() => chatStore.activeMessages.length,
	async () => {
		await nextTick();
		// 새 메시지가 append 된 경우만 bottom 스크롤. prepend 시엔 _loadingOlder 가 true.
		if (!_loadingOlder) scrollToBottom();
	},
);

// cursor 변조 토스트 — store.error 가 cursor 관련 메시지일 때 알림.
watch(
	() => chatStore.error,
	(msg) => {
		if (msg && msg !== _lastError && msg.startsWith("잘못된 페이지 정보")) {
			_lastError = msg;
			toastError(msg);
		}
	},
);

onMounted(async () => {
	await chatStore.setActiveChannel(channelId);
	await nextTick();
	scrollToBottom();
	setupObserver();
});

onUnmounted(() => {
	chatStore.stopPolling();
	_observer?.disconnect();
	_observer = null;
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
        <p class="text-xs text-muted-foreground">
          {{ chatStore.activeChannel?.type === 'DM' ? '다이렉트 메시지' : '공개 채널' }}
        </p>
      </div>
    </header>

    <!-- 채널 없음 -->
    <div v-if="chatStore.loading && !chatStore.activeChannel" class="flex-1 flex items-center justify-center">
      <p class="text-muted-foreground">채널 정보를 불러오는 중...</p>
    </div>
    <div v-else-if="!chatStore.activeChannel" class="flex-1 flex items-center justify-center">
      <p class="text-muted-foreground">채널을 찾을 수 없습니다.</p>
    </div>

    <template v-else>
      <!-- 메시지 목록 -->
      <div ref="messagesEl" class="flex-1 overflow-y-auto px-4 py-4 flex flex-col gap-4 bg-muted/20">
        <!-- 상단 sentinel — IntersectionObserver 가 진입 감지 → fetchOlder -->
        <div
          ref="topSentinelEl"
          aria-hidden="true"
          class="h-1 -mt-1 shrink-0"
          data-testid="chat-top-sentinel"
        />
        <p
          v-if="chatStore.hasMoreOlder[channelId] === false && chatStore.activeMessages.length > 0"
          class="text-center text-xs text-muted-foreground py-2"
        >
          더 이상 이전 메시지가 없습니다.
        </p>

        <div
          v-for="msg in chatStore.activeMessages"
          :key="msg.id"
          class="flex items-start gap-3"
        >
          <Avatar :name="msg.senderId" variant="slate" size="sm" class="mt-0.5" />
          <div>
            <div class="flex items-baseline gap-2">
              <span class="text-sm font-medium">사용자 {{ msg.senderId }}</span>
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
