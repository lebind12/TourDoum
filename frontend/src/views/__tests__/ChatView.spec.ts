/**
 * ChatView — store 연동 단위 테스트 (wire-7 기준)
 *
 * 이전 mockup 기반 spec을 API 연결 store 구조에 맞게 재작성.
 * - channels는 BE API에서 fetch (초기 빈 배열)
 * - publicChannels / dmChannels computed로 분리
 * - DmThread 개념 없음: DM = type:"DM" 채널
 * - setActiveChannel(string), sendMessage(string, string)
 */
import {
	type ChatChannelApiResponse,
	type ChatMessageApiResponse,
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

// ── 픽스처 ────────────────────────────────────────────────────────────────────
const publicChannelFixture: ChatChannelApiResponse = {
	id: 1,
	name: "제주여행",
	type: "PUBLIC",
	createdAt: "2026-05-01T00:00:00",
};

const dmChannelFixture: ChatChannelApiResponse = {
	id: 10,
	name: "DM-김민준",
	type: "DM",
	createdAt: "2026-05-05T00:00:00",
};

const msgFixture: ChatMessageApiResponse = {
	id: 100,
	channelId: 1,
	senderId: 42,
	content: "안녕하세요!",
	createdAt: "2026-05-07T10:00:00",
};

// ── 초기 상태 ─────────────────────────────────────────────────────────────────
describe("ChatStore (wire-7 API 연결) — 초기 상태", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
	});
	afterEach(() => vi.resetAllMocks());

	it("초기 channels는 빈 배열이다 (SEED 없음, API fetch 필요)", () => {
		const store = useChatStore();
		expect(store.channels).toHaveLength(0);
	});

	it("publicChannels / dmChannels도 초기에는 비어 있다", () => {
		const store = useChatStore();
		expect(store.publicChannels).toHaveLength(0);
		expect(store.dmChannels).toHaveLength(0);
	});

	it("activeChannel은 초기에 null이다", () => {
		const store = useChatStore();
		expect(store.activeChannel).toBeNull();
	});
});

// ── fetchChannels → publicChannels / dmChannels 분리 ─────────────────────────
describe("ChatStore — fetchChannels 후 publicChannels / dmChannels", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
	});
	afterEach(() => vi.resetAllMocks());

	it("PUBLIC 채널은 publicChannels에, DM 채널은 dmChannels에 들어간다", async () => {
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

	it("채널 id는 string으로 매핑된다 (BE Long → FE string)", async () => {
		mockGet.mockResolvedValueOnce({
			data: [publicChannelFixture],
			error: null,
		});
		const store = useChatStore();
		await store.fetchChannels();
		expect(store.publicChannels[0].id).toBe("1");
	});
});

// ── setActiveChannel (string id, API 기반) ────────────────────────────────────
describe("ChatStore — setActiveChannel", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
		vi.useFakeTimers();
	});
	afterEach(() => {
		vi.useRealTimers();
		vi.resetAllMocks();
	});

	it("setActiveChannel 이 activeChannelId를 변경하고 메시지를 로드한다", async () => {
		mockGet.mockResolvedValueOnce({
			data: [publicChannelFixture],
			error: null,
		});
		mockGet.mockResolvedValueOnce({ data: [msgFixture], error: null });

		const store = useChatStore();
		await store.fetchChannels();
		await store.setActiveChannel("1");

		expect(store.activeChannelId).toBe("1");
		expect(store.activeChannel?.name).toBe("제주여행");
		expect(store.activeMessages).toHaveLength(1);
		expect(store.activeMessages[0].content).toBe("안녕하세요!");
	});
});

// ── sendMessage (API 연결) ─────────────────────────────────────────────────────
describe("ChatStore — sendMessage", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
		vi.useFakeTimers();
	});
	afterEach(() => {
		vi.useRealTimers();
		vi.resetAllMocks();
	});

	it("sendMessage 가 메시지를 BE에 전송하고 activeMessages에 추가한다", async () => {
		// fetchChannels
		mockGet.mockResolvedValueOnce({
			data: [publicChannelFixture],
			error: null,
		});
		// setActiveChannel initial load
		mockGet.mockResolvedValueOnce({ data: [], error: null });
		// sendMessage
		mockPost.mockResolvedValueOnce({ data: msgFixture, error: null });

		const store = useChatStore();
		await store.fetchChannels();
		await store.setActiveChannel("1");

		const before = store.activeMessages.length;
		await store.sendMessage("1", "안녕하세요!");

		expect(mockPost).toHaveBeenCalledWith("/api/chat/channels/1/messages", {
			content: "안녕하세요!",
		});
		expect(store.activeMessages.length).toBe(before + 1);
		expect(store.activeMessages.at(-1)?.content).toBe("안녕하세요!");
	});
});

// ── openDm (DmThread 없음 — 채널 기반) ───────────────────────────────────────
describe("ChatStore — openDm (채널 기반 DM)", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
	});
	afterEach(() => vi.resetAllMocks());

	it("openDm 이 DM 채널을 반환하고 dmChannels에 추가한다", async () => {
		mockPost.mockResolvedValueOnce({ data: dmChannelFixture, error: null });

		const store = useChatStore();
		const channel = await store.openDm(101);

		expect(channel).not.toBeNull();
		expect(channel?.type).toBe("DM");
		expect(channel?.name).toBe("DM-김민준");
		expect(store.dmChannels).toHaveLength(1);
	});

	it("같은 대상으로 openDm 두 번 호출 시 채널이 중복되지 않는다", async () => {
		mockPost.mockResolvedValue({ data: dmChannelFixture, error: null });

		const store = useChatStore();
		await store.openDm(101);
		await store.openDm(101);

		expect(store.channels).toHaveLength(1);
	});
});
