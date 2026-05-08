/**
 * useChatStore — Chat keyset paging (ADR-0012 FE-1).
 *
 * BE 엔드포인트:
 *   GET  /api/chat/channels                                          — 내 채널 목록
 *   GET  /api/chat/channels/{id}/messages/older?beforeCursor=&limit= — 스크롤 업 (backward)
 *   GET  /api/chat/channels/{id}/messages?afterCursor=&limit=        — 폴링 (forward)
 *   GET  /api/chat/channels/{id}/messages?sinceId=N                  — 한시 호환
 *   POST /api/chat/channels/{id}/messages                            — 전송
 *   POST /api/chat/dm                                                — DM 채널 열기
 *
 * keyset state:
 *   - olderCursor[ch]  — 다음 backward 호출에 사용할 cursor (response.nextCursor / older)
 *   - newerCursor[ch]  — 다음 forward 호출에 사용할 cursor (response.nextCursor / forward)
 *   - lastSeenId[ch]   — newerCursor 가 아직 없을 때 sinceId 한시 호환에 사용
 *   - hasMoreOlder[ch] — 더 이상 older 가 없으면 false (IntersectionObserver fetch 정지)
 *
 * cursor 변조 fallback: status===400 (또는 cursor 관련 에러) 발생 시 해당 cursor 폐기 + 초기 재로드.
 *
 * 채널 목록 정렬: BE-2 가 lastMessageAt DESC, id DESC 로 응답. FE 도 같은 키로 fallback 정렬.
 */
import { type ApiResponse, get, post } from "@/api/client";
import { defineStore } from "pinia";
import { computed, ref } from "vue";

// ── BE API 응답 타입 ────────────────────────────────────────────────────────
export interface ChatChannelApiResponse {
	id: number;
	name: string;
	type: "PUBLIC" | "DM";
	createdAt: string;
	lastMessageId?: number | null;
	lastMessageAt?: string | null;
}

export interface ChatMessageApiResponse {
	id: number;
	channelId: number;
	senderId: number;
	content: string;
	createdAt: string;
}

export interface ChatMessagePageApiResponse {
	items: ChatMessageApiResponse[];
	nextCursor: string | null;
	appliedLimit: number;
	hasMore: boolean;
}

// ── FE 도메인 타입 ──────────────────────────────────────────────────────────
export interface ChatChannel {
	id: string;
	name: string;
	type: "PUBLIC" | "DM";
	createdAt: string;
	lastMessageId: string | null;
	lastMessageAt: string | null;
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
		lastMessageId: r.lastMessageId != null ? r.lastMessageId.toString() : null,
		lastMessageAt: r.lastMessageAt ?? null,
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

/** lastMessageAt DESC, id DESC. NULL 은 createdAt fallback (= 채널 생성 직후 빈 채널). */
function compareChannelsDesc(a: ChatChannel, b: ChatChannel): number {
	const aKey = a.lastMessageAt ?? a.createdAt;
	const bKey = b.lastMessageAt ?? b.createdAt;
	if (aKey === bKey) {
		// id desc tie-break.
		return Number(b.id) - Number(a.id);
	}
	return aKey < bKey ? 1 : -1;
}

const POLL_INTERVAL_MS = 2_000;
const PAGE_LIMIT = 20;

export const useChatStore = defineStore("chat", () => {
	// ── 상태 ────────────────────────────────────────────────────────────────
	const channels = ref<ChatChannel[]>([]);
	const messages = ref<Record<string, ChatMessage[]>>({});
	const activeChannelId = ref<string | null>(null);
	const loading = ref(false);
	const error = ref<string | null>(null);

	/** 채널별 cursor 상태 (keyset paging). */
	const olderCursors = ref<Record<string, string | null>>({});
	const newerCursors = ref<Record<string, string | null>>({});
	const hasMoreOlder = ref<Record<string, boolean>>({});
	/** 폴링 fallback 용 — newerCursor 부재 시 sinceId 한시 호환에 사용. */
	const lastSeenIds = ref<Record<string, string>>({});

	let _pollTimer: ReturnType<typeof setInterval> | null = null;
	let _pollingChannelId: string | null = null;

	// ── computed ─────────────────────────────────────────────────────────
	const sortedChannels = computed(() =>
		[...channels.value].sort(compareChannelsDesc),
	);
	const publicChannels = computed(() =>
		sortedChannels.value.filter((c) => c.type === "PUBLIC"),
	);
	const dmChannels = computed(() =>
		sortedChannels.value.filter((c) => c.type === "DM"),
	);

	const activeChannel = computed(
		() => channels.value.find((c) => c.id === activeChannelId.value) ?? null,
	);

	const activeMessages = computed<ChatMessage[]>(() =>
		activeChannelId.value !== null
			? (messages.value[activeChannelId.value] ?? [])
			: [],
	);

	// ── 내부 헬퍼 ────────────────────────────────────────────────────────
	/** dedupe append: 기존 messages 끝에 새 items 를 붙이되 id 중복은 skip. */
	function appendDeduped(channelId: string, incoming: ChatMessage[]): number {
		if (incoming.length === 0) return 0;
		const list = messages.value[channelId] ?? [];
		const existing = new Set(list.map((m) => m.id));
		const fresh = incoming.filter((m) => !existing.has(m.id));
		if (fresh.length === 0) return 0;
		messages.value[channelId] = [...list, ...fresh];
		return fresh.length;
	}

	/** dedupe prepend: 기존 messages 앞에 새 items 를 붙이되 id 중복은 skip. */
	function prependDeduped(channelId: string, incoming: ChatMessage[]): number {
		if (incoming.length === 0) return 0;
		const list = messages.value[channelId] ?? [];
		const existing = new Set(list.map((m) => m.id));
		const fresh = incoming.filter((m) => !existing.has(m.id));
		if (fresh.length === 0) return 0;
		messages.value[channelId] = [...fresh, ...list];
		return fresh.length;
	}

	/** cursor 변조/유효성 에러 — status===400 또는 본문에 'HTTP 400' 마커. */
	function isCursorRejected<T>(res: ApiResponse<T>): boolean {
		if (res.status === 400) return true;
		if (res.error?.includes("HTTP 400")) return true;
		return false;
	}

	// ── 채널 목록 ────────────────────────────────────────────────────────
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

	// ── 메시지: 초기 로드 (older 진입, cursor 없음) ─────────────────────
	/**
	 * 채널 진입 시 호출. `messages/older?limit=20` (cursor 미지정) 으로 최신 N건 ASC 응답.
	 * messages 교체 + olderCursor=response.nextCursor + lastSeenId=last item.
	 * newerCursor 는 첫 폴링 응답에서 채워질 때까지 null (그 사이 sinceId fallback).
	 */
	async function fetchMessagesInitial(channelId: string): Promise<void> {
		const result = await get<ChatMessagePageApiResponse>(
			`/api/chat/channels/${channelId}/messages/older?limit=${PAGE_LIMIT}`,
		);
		if (result.error || !result.data) {
			error.value = result.error;
			messages.value[channelId] = [];
			olderCursors.value[channelId] = null;
			newerCursors.value[channelId] = null;
			hasMoreOlder.value[channelId] = false;
			return;
		}
		const page = result.data;
		const mapped = page.items.map(mapMessage);
		messages.value[channelId] = mapped;
		olderCursors.value[channelId] = page.nextCursor;
		newerCursors.value[channelId] = null;
		hasMoreOlder.value[channelId] = page.hasMore;
		const last = mapped[mapped.length - 1];
		if (last) lastSeenIds.value[channelId] = last.id;
	}

	// ── 메시지: 스크롤 업 (older 추가) ──────────────────────────────────
	/**
	 * IntersectionObserver 가 상단 sentinel 진입 시 호출.
	 * `?beforeCursor=olderCursor` 로 더 오래된 N건 ASC 응답 → prepend (dedupe).
	 * 변조 cursor → cursor 폐기 + hasMore=false 로 정지 (UI 토스트 표시).
	 *
	 * 반환: prepend 된 메시지 수 (UI 가 scrollTop 보정 결정에 사용).
	 */
	async function fetchOlder(channelId: string): Promise<number> {
		if (hasMoreOlder.value[channelId] === false) return 0;
		const cursor = olderCursors.value[channelId];
		if (!cursor) {
			// olderCursor 없으면 더 호출하지 않음 (초기 로드 결과가 hasMore=false 이거나 cursor=null).
			hasMoreOlder.value[channelId] = false;
			return 0;
		}
		const result = await get<ChatMessagePageApiResponse>(
			`/api/chat/channels/${channelId}/messages/older?beforeCursor=${encodeURIComponent(cursor)}&limit=${PAGE_LIMIT}`,
		);
		if (isCursorRejected(result)) {
			// cursor 변조/만료 — 폐기 후 초기 재로드.
			error.value = "잘못된 페이지 정보입니다. 메시지를 다시 불러옵니다.";
			olderCursors.value[channelId] = null;
			hasMoreOlder.value[channelId] = false;
			await fetchMessagesInitial(channelId);
			return 0;
		}
		if (result.error || !result.data) {
			error.value = result.error;
			return 0;
		}
		const page = result.data;
		const mapped = page.items.map(mapMessage);
		const added = prependDeduped(channelId, mapped);
		olderCursors.value[channelId] = page.nextCursor;
		hasMoreOlder.value[channelId] = page.hasMore && added > 0;
		return added;
	}

	// ── 메시지: 폴링 forward ───────────────────────────────────────────
	/**
	 * 폴링 1 tick — `?afterCursor=newerCursor` 또는 `?sinceId=lastSeenId` 한시 호환.
	 * append (dedupe) + newerCursor / lastSeenId 갱신.
	 *
	 * cold-start fallback (FE-1.1): 초기 로드가 items=[]로 끝난 신규 DM 같은 채널에서는
	 * newerCursor / lastSeenId 둘 다 없어 영원히 폴링이 정지하던 회귀(qa #30)를 차단한다.
	 * `messages.value[channelId]` 가 정의돼 있으면 init 이후이므로
	 * `?afterCursor=&limit=N` 빈 cursor 호출로 BE 의 "최신 limit건 ASC" 경로(ChatService:80)를 재호출한다.
	 * 빈 채널에서는 items=[]로 무비용 유지, 메시지가 추가되면 즉시 회수되며 lastSeenId가 박제되어
	 * 다음 tick부터 정상 sinceId/cursor 경로로 수렴한다.
	 */
	async function pollForward(channelId: string): Promise<number> {
		const newer = newerCursors.value[channelId];
		const lastId = lastSeenIds.value[channelId];
		let url: string;
		if (newer) {
			url = `/api/chat/channels/${channelId}/messages?afterCursor=${encodeURIComponent(newer)}&limit=${PAGE_LIMIT}`;
		} else if (lastId) {
			url = `/api/chat/channels/${channelId}/messages?sinceId=${lastId}`;
		} else if (messages.value[channelId] !== undefined) {
			// cold-start: init 완료(messages 정의) + cursor/lastId 미박제 → 빈 afterCursor로 forward bootstrap.
			url = `/api/chat/channels/${channelId}/messages?afterCursor=&limit=${PAGE_LIMIT}`;
		} else {
			// 초기 로드 전 — skip.
			return 0;
		}
		const result = await get<ChatMessagePageApiResponse>(url);
		if (isCursorRejected(result)) {
			error.value = "잘못된 페이지 정보입니다. 메시지를 다시 불러옵니다.";
			newerCursors.value[channelId] = null;
			await fetchMessagesInitial(channelId);
			return 0;
		}
		if (result.error || !result.data) {
			// 폴링 에러는 silent — 다음 tick 에서 재시도.
			return 0;
		}
		const page = result.data;
		const mapped = page.items.map(mapMessage);
		const added = appendDeduped(channelId, mapped);
		if (page.nextCursor) newerCursors.value[channelId] = page.nextCursor;
		const last = mapped[mapped.length - 1];
		if (last) lastSeenIds.value[channelId] = last.id;
		return added;
	}

	// ── 메시지 전송 ──────────────────────────────────────────────────────
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
			appendDeduped(channelId, [msg]);
			lastSeenIds.value[channelId] = msg.id;
			// newerCursor 는 다음 폴 응답에서 갱신. (BE 응답에 cursor 미포함 — items 1건짜리 page 대체 응답이 아니므로.)
		}
	}

	// ── DM 채널 ──────────────────────────────────────────────────────────
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
	function startPolling(channelId: string) {
		stopPolling();
		_pollingChannelId = channelId;
		_pollTimer = setInterval(() => {
			if (_pollingChannelId) {
				pollForward(_pollingChannelId);
			}
		}, POLL_INTERVAL_MS);
	}

	function stopPolling() {
		if (_pollTimer !== null) {
			clearInterval(_pollTimer);
			_pollTimer = null;
		}
		_pollingChannelId = null;
	}

	// ── 채널 활성화 ──────────────────────────────────────────────────────
	async function setActiveChannel(channelId: string): Promise<void> {
		stopPolling();
		activeChannelId.value = channelId;
		error.value = null;
		await fetchMessagesInitial(channelId);
		startPolling(channelId);
	}

	return {
		channels,
		messages,
		activeChannelId,
		loading,
		error,
		olderCursors,
		newerCursors,
		hasMoreOlder,
		lastSeenIds,
		sortedChannels,
		publicChannels,
		dmChannels,
		activeChannel,
		activeMessages,
		fetchChannels,
		fetchMessagesInitial,
		fetchOlder,
		pollForward,
		sendMessage,
		openDm,
		setActiveChannel,
		startPolling,
		stopPolling,
	};
});
