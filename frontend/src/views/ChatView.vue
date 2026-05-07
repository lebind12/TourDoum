<script setup lang="ts">
import { Avatar } from "@/components/ui/avatar";
import { Card, CardContent } from "@/components/ui/card";
import { useChatStore } from "@/stores/chat";
import { onMounted, ref } from "vue";
import { RouterLink } from "vue-router";

const chatStore = useChatStore();
const activeTab = ref<"channels" | "dm">("channels");

onMounted(() => {
	chatStore.fetchChannels();
});
</script>

<template>
  <div class="max-w-2xl mx-auto space-y-6">
    <!-- 헤더 -->
    <div>
      <h1 class="text-3xl font-bold tracking-tight">채팅</h1>
      <p class="text-muted-foreground mt-1">여행자들과 정보를 공유하세요</p>
    </div>

    <!-- 탭 -->
    <div class="flex gap-1 bg-muted rounded-xl p-1" role="tablist">
      <button
        type="button"
        role="tab"
        :aria-selected="activeTab === 'channels'"
        :class="[
          'flex-1 py-2 rounded-lg text-sm font-medium transition-colors',
          activeTab === 'channels'
            ? 'bg-background text-primary shadow-sm'
            : 'text-muted-foreground hover:text-foreground',
        ]"
        @click="activeTab = 'channels'"
      >
        공개 채널
      </button>
      <button
        type="button"
        role="tab"
        :aria-selected="activeTab === 'dm'"
        :class="[
          'flex-1 py-2 rounded-lg text-sm font-medium transition-colors',
          activeTab === 'dm'
            ? 'bg-background text-primary shadow-sm'
            : 'text-muted-foreground hover:text-foreground',
        ]"
        @click="activeTab = 'dm'"
      >
        다이렉트 메시지
      </button>
    </div>

    <!-- 공개 채널 목록 -->
    <template v-if="activeTab === 'channels'">
      <div v-if="chatStore.loading" class="flex justify-center py-8">
        <p class="text-muted-foreground text-sm">채널 목록을 불러오는 중...</p>
      </div>
      <div v-else-if="chatStore.publicChannels.length === 0">
        <Card>
          <CardContent class="p-12 text-center">
            <p class="text-muted-foreground">참여 중인 공개 채널이 없습니다.</p>
          </CardContent>
        </Card>
      </div>
      <div v-else class="flex flex-col gap-3">
        <RouterLink
          v-for="channel in chatStore.publicChannels"
          :key="channel.id"
          :to="{ name: 'chat-channel', params: { channelId: channel.id } }"
          class="block"
        >
          <Card class="hover:shadow-md transition-shadow cursor-pointer">
            <CardContent class="p-4">
              <div class="flex items-center gap-3">
                <Avatar name="#" variant="sky" size="md" />
                <div>
                  <h2 class="font-semibold">{{ channel.name }}</h2>
                  <p class="text-xs text-muted-foreground">
                    채널 #{{ channel.id }}
                  </p>
                </div>
              </div>
            </CardContent>
          </Card>
        </RouterLink>
      </div>
    </template>

    <!-- DM 목록 -->
    <template v-else>
      <div v-if="chatStore.dmChannels.length === 0">
        <Card>
          <CardContent class="p-12 text-center">
            <p class="text-muted-foreground">다이렉트 메시지가 없습니다.</p>
            <p class="text-muted-foreground text-sm mt-1">숙소 상세 페이지에서 호스트에게 메시지를 보내보세요.</p>
          </CardContent>
        </Card>
      </div>

      <div v-else class="flex flex-col gap-3">
        <RouterLink
          v-for="channel in chatStore.dmChannels"
          :key="channel.id"
          :to="{ name: 'chat-channel', params: { channelId: channel.id } }"
          class="block"
        >
          <Card class="hover:shadow-md transition-shadow cursor-pointer">
            <CardContent class="p-4">
              <div class="flex items-center gap-3">
                <Avatar :name="channel.name" variant="emerald" size="md" />
                <div class="flex-1 min-w-0">
                  <h2 class="font-semibold text-sm">{{ channel.name }}</h2>
                  <p class="text-xs text-muted-foreground">다이렉트 메시지</p>
                </div>
              </div>
            </CardContent>
          </Card>
        </RouterLink>
      </div>
    </template>
  </div>
</template>
