/**
 * ChatView — store 연동 단위 테스트 (ADR-0012 FE-1)
 *
 * - channels 는 BE API fetch (lastMessageAt DESC 정렬, NULL fallback createdAt).
 * - publicChannels / dmChannels computed.
 * - DM = type:"DM" 채널 (DmThread 없음).
 * - setActiveChannel(id) 은 /messages/older?limit=20 으로 초기 로드 + 폴링.
 */
import {
	type ChatChannelApiResponse,
	type ChatMessageApiResponse,
	type ChatMessagePageApiResponse,
	useChatStore,
} from "@/stores/chat";
import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/api/client", () => ({
	get: vi.fn(),
	post: vi.fn(),
}));

import { get, post } from "@/api/client";
const mockGet = vi.mocked(get);
const mockPost = vi.mocked(post);

const publicChannelFixture: ChatChannelApiResponse = {
	id: 1,
	name: "제주여행",
	type: "PUBLIC",
	createdAt: "2026-05-01T00:00:00",
	lastMessageId: 200,
	lastMessageAt: "2026-05-07T12:00:00",
};

const dmChannelFixture: ChatChannelApiResponse = {
	id: 10,
	name: "DM-김민준",
	type: "DM",
	createdAt: "2026-05-05T00:00:00",
	lastMessageId: 150,
	lastMessageAt: "2026-05-06T10:00:00",
};

function makeMessage(id: number): ChatMessageApiResponse {
	return {
		id,
		channelId: 1,
		senderId: 42,
		content: `msg-${id}`,
		createdAt: "2026-05-07T10:00:00",
	};
}

function page(
	items: ChatMessageApiResponse[],
	nextCursor: string | null = null,
	hasMore = false,
): ChatMessagePageApiResponse {
	return { items, nextCursor, appliedLimit: 20, hasMore };
}

afterEach(() => vi.resetAllMocks());

describe("ChatStore — 초기 상태", () => {
	beforeEach(() => setActivePinia(createPinia()));

	it("초기 channels 는 빈 배열", () => {
		const store = useChatStore();
		expect(store.channels).toHaveLength(0);
	});

	it("publicChannels / dmChannels 도 비어 있음", () => {
		const store = useChatStore();
		expect(store.publicChannels).toHaveLength(0);
		expect(store.dmChannels).toHaveLength(0);
	});

	it("activeChannel 은 null", () => {
		const store = useChatStore();
		expect(store.activeChannel).toBeNull();
	});
});

describe("ChatStore — fetchChannels 후 publicChannels / dmChannels", () => {
	beforeEach(() => setActivePinia(createPinia()));

	it("PUBLIC 은 publicChannels, DM 은 dmChannels 에 들어간다", async () => {
		mockGet.mockResolvedValueOnce({
			data: [publicChannelFixture, dmChannelFixture],
			error: null,
		});
		const store = useChatStore();
		await store.fetchChannels();

		expect(store.publicChannels).toHaveLength(1);
		expect(store.publicChannels[0].name).toBe("제주여행");
		expect(store.dmChannels).toHaveLength(1);
		expect(store.dmChannels[0].name).toBe("DM-김민준");
	});

	it("채널 id 는 string 으로 매핑 (BE Long → FE string)", async () => {
		mockGet.mockResolvedValueOnce({
			data: [publicChannelFixture],
			error: null,
		});
		const store = useChatStore();
		await store.fetchChannels();
		expect(store.publicChannels[0].id).toBe("1");
	});
});

describe("ChatStore — setActiveChannel (keyset 초기 로드)", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
		vi.useFakeTimers();
	});
	afterEach(() => vi.useRealTimers());

	it("activeChannelId 설정 + /messages/older 호출 + 메시지 로드", async () => {
		mockGet.mockResolvedValueOnce({
			data: [publicChannelFixture],
			error: null,
		});
		mockGet.mockResolvedValueOnce({
			data: page([makeMessage(100)], "older-cur", false),
			error: null,
		});

		const store = useChatStore();
		await store.fetchChannels();
		await store.setActiveChannel("1");

		expect(store.activeChannelId).toBe("1");
		expect(store.activeChannel?.name).toBe("제주여행");
		expect(store.activeMessages).toHaveLength(1);
		expect(store.activeMessages[0].content).toBe("msg-100");
		// keyset state 박제.
		expect(store.olderCursors["1"]).toBe("older-cur");
	});
});

describe("ChatStore — sendMessage", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
		vi.useFakeTimers();
	});
	afterEach(() => vi.useRealTimers());

	it("BE 전송 + activeMessages append + lastSeenId 갱신", async () => {
		mockGet.mockResolvedValueOnce({
			data: [publicChannelFixture],
			error: null,
		});
		mockGet.mockResolvedValueOnce({
			data: page([], null, false),
			error: null,
		});
		mockPost.mockResolvedValueOnce({ data: makeMessage(100), error: null });

		const store = useChatStore();
		await store.fetchChannels();
		await store.setActiveChannel("1");

		const before = store.activeMessages.length;
		await store.sendMessage("1", "안녕");

		expect(mockPost).toHaveBeenCalledWith("/api/chat/channels/1/messages", {
			content: "안녕",
		});
		expect(store.activeMessages.length).toBe(before + 1);
		expect(store.activeMessages.at(-1)?.content).toBe("msg-100");
		expect(store.lastSeenIds["1"]).toBe("100");
	});
});

describe("ChatStore — openDm", () => {
	beforeEach(() => setActivePinia(createPinia()));

	it("DM 채널 반환 + dmChannels 에 추가", async () => {
		mockPost.mockResolvedValueOnce({ data: dmChannelFixture, error: null });
		const store = useChatStore();
		const ch = await store.openDm(101);
		expect(ch?.type).toBe("DM");
		expect(store.dmChannels).toHaveLength(1);
	});

	it("같은 대상 두 번 호출 시 중복되지 않음", async () => {
		mockPost.mockResolvedValue({ data: dmChannelFixture, error: null });
		const store = useChatStore();
		await store.openDm(101);
		await store.openDm(101);
		expect(store.channels).toHaveLength(1);
	});
});
