<template>
  <main class="min-h-screen bg-slate-50">
    <div class="max-w-2xl mx-auto px-4 py-8">
      <!-- 헤더 -->
      <div class="mb-6">
        <h1 class="text-3xl font-bold text-slate-900">채팅</h1>
        <p class="text-slate-500 mt-1">여행자들과 정보를 공유하세요</p>
      </div>

      <!-- 탭 -->
      <div class="flex gap-1 bg-slate-100 rounded-xl p-1 mb-6">
        <button
          type="button"
          :class="[
            'flex-1 py-2 rounded-lg text-sm font-medium transition-colors',
            activeTab === 'channels' ? 'bg-white text-sky-600 shadow-sm' : 'text-slate-500 hover:text-slate-700'
          ]"
          @click="activeTab = 'channels'"
        >
          공개 채널
        </button>
        <button
          type="button"
          :class="[
            'flex-1 py-2 rounded-lg text-sm font-medium transition-colors',
            activeTab === 'dm' ? 'bg-white text-sky-600 shadow-sm' : 'text-slate-500 hover:text-slate-700'
          ]"
          @click="activeTab = 'dm'"
        >
          다이렉트 메시지
        </button>
      </div>

      <!-- 공개 채널 목록 -->
      <template v-if="activeTab === 'channels'">
        <div class="flex flex-col gap-3">
          <RouterLink
            v-for="channel in chatStore.channels"
            :key="channel.id"
            :to="{ name: 'chat-channel', params: { channelId: channel.id } }"
            class="block bg-white rounded-xl border border-slate-200 p-4 hover:shadow-md transition-shadow"
          >
            <div class="flex items-start justify-between">
              <div class="flex items-center gap-3">
                <div class="w-10 h-10 rounded-full bg-sky-100 flex items-center justify-center shrink-0">
                  <span class="text-sky-600 font-bold">#</span>
                </div>
                <div>
                  <h2 class="font-semibold text-slate-900">{{ channel.name }}</h2>
                  <p class="text-xs text-slate-500">{{ channel.description }}</p>
                </div>
              </div>
              <span class="text-xs text-slate-400 shrink-0">{{ channel.memberCount.toLocaleString() }}명</span>
            </div>
            <div class="mt-3 flex items-center gap-2">
              <p class="text-sm text-slate-600 truncate flex-1">{{ channel.lastMessage }}</p>
              <span class="text-xs text-slate-400 shrink-0">{{ formatTime(channel.lastMessageAt) }}</span>
            </div>
          </RouterLink>
        </div>
      </template>

      <!-- DM 목록 -->
      <template v-else>
        <div class="flex flex-col gap-3">
          <RouterLink
            v-for="[partnerId, thread] in Object.entries(chatStore.dmThreads)"
            :key="partnerId"
            :to="{ name: 'dm', params: { userId: partnerId } }"
            class="block bg-white rounded-xl border border-slate-200 p-4 hover:shadow-md transition-shadow"
          >
            <div class="flex items-center gap-3">
              <div class="w-10 h-10 rounded-full bg-emerald-100 flex items-center justify-center shrink-0">
                <span class="text-emerald-600 font-bold text-sm">{{ thread.partnerName[0] }}</span>
              </div>
              <div class="flex-1 min-w-0">
                <div class="flex items-center gap-2">
                  <h2 class="font-semibold text-slate-900 text-sm">{{ thread.partnerName }}</h2>
                  <span class="text-xs bg-emerald-100 text-emerald-700 rounded-full px-2">호스트</span>
                </div>
                <p class="text-xs text-slate-500 truncate">{{ thread.accommodationName }}</p>
                <p class="text-sm text-slate-600 truncate mt-1">
                  {{ thread.messages[thread.messages.length - 1]?.content ?? '' }}
                </p>
              </div>
            </div>
          </RouterLink>
        </div>

        <div v-if="Object.keys(chatStore.dmThreads).length === 0" class="bg-white rounded-xl border border-slate-200 p-12 text-center">
          <p class="text-slate-400">다이렉트 메시지가 없습니다.</p>
          <p class="text-slate-400 text-sm mt-1">숙소 상세 페이지에서 호스트에게 메시지를 보내보세요.</p>
        </div>
      </template>
    </div>
  </main>
</template>

<script setup lang="ts">
import { useChatStore } from "@/stores/chat";
import { onMounted, ref } from "vue";
import { RouterLink } from "vue-router";

const chatStore = useChatStore();
const activeTab = ref<"channels" | "dm">("channels");

function formatTime(iso: string): string {
	const d = new Date(iso);
	const now = new Date();
	const diff = now.getTime() - d.getTime();
	if (diff < 60 * 60 * 1000) return `${Math.floor(diff / 60000)}분 전`;
	if (diff < 24 * 60 * 60 * 1000) return `${Math.floor(diff / 3600000)}시간 전`;
	return `${d.getMonth() + 1}/${d.getDate()}`;
}

onMounted(() => {
	chatStore.fetchChannels();
});
</script>
