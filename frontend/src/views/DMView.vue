<script setup lang="ts">
/**
 * DMView — 특정 사용자와의 DM 채널을 열고 ChatChannelView로 리다이렉트.
 *
 * /dm/:userId 로 진입 시 openDm(userId)를 호출해 채널을 열거나 생성한 뒤,
 * /chat/:channelId 로 이동한다. DM 메시지 렌더링은 ChatChannelView가 담당.
 */
import { Button } from "@/components/ui/button";
import { useChatStore } from "@/stores/chat";
import { onMounted, ref } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";

const route = useRoute();
const router = useRouter();
const chatStore = useChatStore();
const redirecting = ref(true);
const errorMsg = ref<string | null>(null);

onMounted(async () => {
	const userId = Number(route.params.userId);
	if (!Number.isInteger(userId) || userId <= 0) {
		errorMsg.value = "유효하지 않은 사용자 ID입니다.";
		redirecting.value = false;
		return;
	}

	const channel = await chatStore.openDm(userId);
	if (!channel) {
		errorMsg.value = chatStore.error ?? "DM 채널을 열 수 없습니다.";
		redirecting.value = false;
		return;
	}

	await router.replace({
		name: "chat-channel",
		params: { channelId: channel.id },
	});
});
</script>

<template>
  <div class="flex flex-col items-center justify-center h-64 gap-4">
    <template v-if="redirecting">
      <p class="text-muted-foreground">대화 채널을 여는 중...</p>
    </template>
    <template v-else>
      <p class="text-destructive">{{ errorMsg }}</p>
      <RouterLink to="/chat">
        <Button variant="outline">채팅 목록으로</Button>
      </RouterLink>
    </template>
  </div>
</template>
