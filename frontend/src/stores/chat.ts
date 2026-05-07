/**
 * useChatStore — Chat API 연결 버전
 *
 * BE: GET /api/chat/channels, GET /api/chat/channels/{id}/messages?sinceId=
 *     POST /api/chat/channels/{id}/messages, POST /api/chat/dm
 * 폴링: 활성 채널 2초 간격, sinceId 기반 증분 fetch
 */
import { get, post } from "@/api/client";
import { defineStore } from "pinia";
import { computed, ref } from "vue";

// ── BE API 응답 타입 ────────────────────────────────────────────────────────
export interface ChatChannelApiResponse {
	id: number;
	name: string;
	type: "PUBLIC" | "DM";
	createdAt: string;
}

export interface ChatMessageApiResponse {
	id: number;
	channelId: number;
	senderId: number;
	content: string;
	createdAt: string;
}

// ── FE 도메인 타입 ──────────────────────────────────────────────────────────
export interface ChatChannel {
	id: string;
	name: string;
	type: "PUBLIC" | "DM";
	createdAt: string;
}

export interface ChatMessage {
	id: string;
	channelId: string;
	senderId: string;
	content: string;
	createdAt: string;
}

// ── 매핑 함수 ──────────────────────────────────────────────────────────────
function mapChannel(r: ChatChannelApiResponse): ChatChannel {
	return {
		id: r.id.toString(),
		name: r.name,
		type: r.type,
		createdAt: r.createdAt,
	};
}

function mapMessage(r: ChatMessageApiResponse): ChatMessage {
	return {
		id: r.id.toString(),
		channelId: r.channelId.toString(),
		senderId: r.senderId.toString(),
		content: r.content,
		createdAt: r.createdAt,
	};
}

const POLL_INTERVAL_MS = 2_000;

export const useChatStore = defineStore("chat", () => {
	// ── 상태 ────────────────────────────────────────────────────────────────
	const channels = ref<ChatChannel[]>([]);
	const messages = ref<Record<string, ChatMessage[]>>({});
	const activeChannelId = ref<string | null>(null);
	const loading = ref(false);
	const error = ref<string | null>(null);

	/** 채널별 마지막 메시지 id (sinceId 폴링용) */
	const lastMessageIds = ref<Record<string, string>>({});

	let _pollTimer: ReturnType<typeof setInterval> | null = null;
	let _pollingChannelId: string | null = null;

	// ── computed ─────────────────────────────────────────────────────────
	const publicChannels = computed(() =>
		channels.value.filter((c) => c.type === "PUBLIC"),
	);
	const dmChannels = computed(() =>
		channels.value.filter((c) => c.type === "DM"),
	);

	const activeChannel = computed(
		() => channels.value.find((c) => c.id === activeChannelId.value) ?? null,
	);

	const activeMessages = computed<ChatMessage[]>(() =>
		activeChannelId.value !== null
			? (messages.value[activeChannelId.value] ?? [])
			: [],
	);

	// ── API ──────────────────────────────────────────────────────────────
	/** 내 채널 목록 (PUBLIC + DM) 조회 */
	async function fetchChannels(): Promise<void> {
		loading.value = true;
		error.value = null;
		const result = await get<ChatChannelApiResponse[]>("/api/chat/channels");
		loading.value = false;
		if (result.error) {
			error.value = result.error;
			return;
		}
		channels.value = (result.data ?? []).map(mapChannel);
	}

	/**
	 * 채널 메시지 조회.
	 * sinceId="0" → 최근 50건 교체, 그 외 → 기존 목록에 증분 추가.
	 */
	async function fetchMessages(
		channelId: string,
		sinceId = "0",
	): Promise<void> {
		const result = await get<ChatMessageApiResponse[]>(
			`/api/chat/channels/${channelId}/messages?sinceId=${sinceId}`,
		);
		if (result.error) {
			if (sinceId === "0") error.value = result.error;
			return;
		}
		const newMsgs = (result.data ?? []).map(mapMessage);
		if (newMsgs.length === 0) return;

		if (sinceId === "0") {
			messages.value[channelId] = newMsgs;
		} else {
			if (!messages.value[channelId]) messages.value[channelId] = [];
			messages.value[channelId].push(...newMsgs);
		}

		const last = newMsgs[newMsgs.length - 1];
		if (last) lastMessageIds.value[channelId] = last.id;
	}

	/** 메시지 전송. 성공 시 로컬에 즉시 반영 */
	async function sendMessage(
		channelId: string,
		content: string,
	): Promise<void> {
		const result = await post<ChatMessageApiResponse>(
			`/api/chat/channels/${channelId}/messages`,
			{
				content,
			},
		);
		if (result.error) {
			error.value = result.error;
			return;
		}
		if (result.data) {
			const msg = mapMessage(result.data);
			if (!messages.value[channelId]) messages.value[channelId] = [];
			messages.value[channelId].push(msg);
			lastMessageIds.value[channelId] = msg.id;
		}
	}

	/**
	 * DM 채널 열기.
	 * 이미 존재하면 기존 채널을 반환하고, 없으면 신규 생성.
	 * 반환된 채널을 채널 목록에 반영.
	 */
	async function openDm(otherMemberId: number): Promise<ChatChannel | null> {
		loading.value = true;
		error.value = null;
		const result = await post<ChatChannelApiResponse>("/api/chat/dm", {
			otherMemberId,
		});
		loading.value = false;
		if (result.error) {
			error.value = result.error;
			return null;
		}
		if (!result.data) return null;
		const channel = mapChannel(result.data);
		if (!channels.value.find((c) => c.id === channel.id)) {
			channels.value.push(channel);
		}
		return channel;
	}

	// ── 폴링 ─────────────────────────────────────────────────────────────
	/** 활성 채널 sinceId 폴링 시작 (중복 호출 방지) */
	function startPolling(channelId: string) {
		stopPolling();
		_pollingChannelId = channelId;
		_pollTimer = setInterval(() => {
			if (_pollingChannelId) {
				const sinceId = lastMessageIds.value[_pollingChannelId] ?? "0";
				fetchMessages(_pollingChannelId, sinceId);
			}
		}, POLL_INTERVAL_MS);
	}

	/** 폴링 중단 */
	function stopPolling() {
		if (_pollTimer !== null) {
			clearInterval(_pollTimer);
			_pollTimer = null;
		}
		_pollingChannelId = null;
	}

	// ── 채널 활성화 ──────────────────────────────────────────────────────
	/** 채널 진입: 초기 메시지 로드 후 폴링 시작 */
	async function setActiveChannel(channelId: string): Promise<void> {
		stopPolling();
		activeChannelId.value = channelId;
		error.value = null;
		await fetchMessages(channelId, "0");
		startPolling(channelId);
	}

	return {
		channels,
		messages,
		activeChannelId,
		loading,
		error,
		lastMessageIds,
		publicChannels,
		dmChannels,
		activeChannel,
		activeMessages,
		fetchChannels,
		fetchMessages,
		sendMessage,
		openDm,
		setActiveChannel,
		startPolling,
		stopPolling,
	};
});
