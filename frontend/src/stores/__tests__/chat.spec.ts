/**
 * useChatStore — API 연결 단위 테스트
 *
 * api/client.ts의 get/post를 vi.mock으로 교체.
 * setInterval/clearInterval은 vi.useFakeTimers()로 제어.
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
const channelFixture: ChatChannelApiResponse = {
	id: 1,
	name: "제주여행",
	type: "PUBLIC",
	createdAt: "2026-05-01T00:00:00",
};

const dmChannelFixture: ChatChannelApiResponse = {
	id: 10,
	name: "DM-user101",
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

const msgFixture2: ChatMessageApiResponse = {
	id: 101,
	channelId: 1,
	senderId: 43,
	content: "반갑습니다!",
	createdAt: "2026-05-07T10:01:00",
};

// ── 초기 상태 ─────────────────────────────────────────────────────────────────
describe("useChatStore — 초기 상태", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
	});

	it("channels가 빈 배열이어야 한다", () => {
		const store = useChatStore();
		expect(store.channels).toEqual([]);
	});

	it("activeChannelId가 null이어야 한다", () => {
		const store = useChatStore();
		expect(store.activeChannelId).toBeNull();
	});

	it("loading이 false여야 한다", () => {
		const store = useChatStore();
		expect(store.loading).toBe(false);
	});

	it("publicChannels / dmChannels가 빈 배열이어야 한다", () => {
		const store = useChatStore();
		expect(store.publicChannels).toEqual([]);
		expect(store.dmChannels).toEqual([]);
	});
});

// ── fetchChannels ─────────────────────────────────────────────────────────────
describe("fetchChannels", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
	});

	afterEach(() => {
		vi.resetAllMocks();
	});

	it("성공 시 channels를 채운다", async () => {
		mockGet.mockResolvedValueOnce({
			data: [channelFixture, dmChannelFixture],
			error: null,
		});
		const store = useChatStore();
		await store.fetchChannels();
		expect(store.channels).toHaveLength(2);
		expect(store.publicChannels).toHaveLength(1);
		expect(store.dmChannels).toHaveLength(1);
	});

	it("PUBLIC 채널의 필드를 올바르게 매핑한다", async () => {
		mockGet.mockResolvedValueOnce({ data: [channelFixture], error: null });
		const store = useChatStore();
		await store.fetchChannels();
		const ch = store.channels[0];
		expect(ch.id).toBe("1");
		expect(ch.name).toBe("제주여행");
		expect(ch.type).toBe("PUBLIC");
	});

	it("실패 시 error를 기록하고 channels를 변경하지 않는다", async () => {
		mockGet.mockResolvedValueOnce({ data: null, error: "서버 오류" });
		const store = useChatStore();
		await store.fetchChannels();
		expect(store.error).toBe("서버 오류");
		expect(store.channels).toEqual([]);
	});
});

// ── fetchMessages ─────────────────────────────────────────────────────────────
describe("fetchMessages", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
	});

	afterEach(() => {
		vi.resetAllMocks();
	});

	it("sinceId=0 시 messages를 교체한다", async () => {
		mockGet.mockResolvedValueOnce({ data: [msgFixture], error: null });
		const store = useChatStore();
		await store.fetchMessages("1", "0");
		expect(store.messages["1"]).toHaveLength(1);
		expect(store.messages["1"][0].content).toBe("안녕하세요!");
	});

	it("sinceId 지정 시 기존 목록에 추가한다", async () => {
		// 초기 로드
		mockGet.mockResolvedValueOnce({ data: [msgFixture], error: null });
		const store = useChatStore();
		await store.fetchMessages("1", "0");

		// 증분 폴링
		mockGet.mockResolvedValueOnce({ data: [msgFixture2], error: null });
		await store.fetchMessages("1", "100");
		expect(store.messages["1"]).toHaveLength(2);
	});

	it("lastMessageIds를 마지막 메시지 id로 갱신한다", async () => {
		mockGet.mockResolvedValueOnce({
			data: [msgFixture, msgFixture2],
			error: null,
		});
		const store = useChatStore();
		await store.fetchMessages("1", "0");
		expect(store.lastMessageIds["1"]).toBe("101");
	});

	it("응답이 빈 배열이면 messages를 변경하지 않는다", async () => {
		mockGet.mockResolvedValueOnce({ data: [msgFixture], error: null });
		const store = useChatStore();
		await store.fetchMessages("1", "0");

		mockGet.mockResolvedValueOnce({ data: [], error: null });
		await store.fetchMessages("1", "100");
		expect(store.messages["1"]).toHaveLength(1);
	});

	it("sinceId=0 실패 시 error를 기록한다", async () => {
		mockGet.mockResolvedValueOnce({ data: null, error: "권한 없음" });
		const store = useChatStore();
		await store.fetchMessages("1", "0");
		expect(store.error).toBe("권한 없음");
	});
});

// ── sendMessage ───────────────────────────────────────────────────────────────
describe("sendMessage", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
	});

	afterEach(() => {
		vi.resetAllMocks();
	});

	it("성공 시 메시지를 로컬에 추가하고 lastMessageId를 갱신한다", async () => {
		mockPost.mockResolvedValueOnce({ data: msgFixture, error: null });
		const store = useChatStore();
		await store.sendMessage("1", "안녕하세요!");
		expect(store.messages["1"]).toHaveLength(1);
		expect(store.lastMessageIds["1"]).toBe("100");
	});

	it("실패 시 error를 기록한다", async () => {
		mockPost.mockResolvedValueOnce({ data: null, error: "전송 실패" });
		const store = useChatStore();
		await store.sendMessage("1", "hello");
		expect(store.error).toBe("전송 실패");
		expect(store.messages["1"]).toBeUndefined();
	});
});

// ── openDm ────────────────────────────────────────────────────────────────────
describe("openDm", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
	});

	afterEach(() => {
		vi.resetAllMocks();
	});

	it("성공 시 DM 채널을 반환하고 channels에 추가한다", async () => {
		mockPost.mockResolvedValueOnce({ data: dmChannelFixture, error: null });
		const store = useChatStore();
		const channel = await store.openDm(101);
		expect(channel).not.toBeNull();
		expect(channel?.id).toBe("10");
		expect(channel?.type).toBe("DM");
		expect(store.channels).toHaveLength(1);
	});

	it("이미 channels에 있는 경우 중복 추가하지 않는다", async () => {
		mockPost.mockResolvedValue({ data: dmChannelFixture, error: null });
		const store = useChatStore();
		await store.openDm(101);
		await store.openDm(101);
		expect(store.channels).toHaveLength(1);
	});

	it("실패 시 null을 반환하고 error를 기록한다", async () => {
		mockPost.mockResolvedValueOnce({
			data: null,
			error: "자기 자신과 DM 불가",
		});
		const store = useChatStore();
		const channel = await store.openDm(0);
		expect(channel).toBeNull();
		expect(store.error).toBe("자기 자신과 DM 불가");
	});
});

// ── 폴링 ──────────────────────────────────────────────────────────────────────
describe("startPolling / stopPolling", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
		vi.useFakeTimers();
	});

	afterEach(() => {
		vi.useRealTimers();
		vi.resetAllMocks();
	});

	it("2초마다 fetchMessages를 호출한다", async () => {
		mockGet.mockResolvedValue({ data: [], error: null });
		const store = useChatStore();
		store.startPolling("1");

		vi.advanceTimersByTime(6_000);
		// 3 ticks at 2000ms
		expect(mockGet).toHaveBeenCalledTimes(3);
	});

	it("stopPolling 호출 후 더 이상 폴링하지 않는다", async () => {
		mockGet.mockResolvedValue({ data: [], error: null });
		const store = useChatStore();
		store.startPolling("1");
		store.stopPolling();

		vi.advanceTimersByTime(6_000);
		expect(mockGet).not.toHaveBeenCalled();
	});

	it("중복 startPolling 호출 시 타이머를 하나만 유지한다", async () => {
		mockGet.mockResolvedValue({ data: [], error: null });
		const store = useChatStore();
		store.startPolling("1");
		store.startPolling("1"); // 중복

		vi.advanceTimersByTime(4_000);
		// 2초 * 2 = 2 calls (not 4)
		expect(mockGet).toHaveBeenCalledTimes(2);
	});

	it("sinceId가 갱신되면 이후 폴에서 해당 sinceId를 사용한다", async () => {
		// 초기 lastMessageId 세팅
		mockGet.mockResolvedValue({ data: [], error: null });
		const store = useChatStore();
		store.lastMessageIds["1"] = "99";
		store.startPolling("1");

		vi.advanceTimersByTime(2_000);
		expect(mockGet).toHaveBeenCalledWith(
			"/api/chat/channels/1/messages?sinceId=99",
		);
	});
});

// ── setActiveChannel ──────────────────────────────────────────────────────────
describe("setActiveChannel", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
		vi.useFakeTimers();
	});

	afterEach(() => {
		vi.useRealTimers();
		vi.resetAllMocks();
	});

	it("activeChannelId를 설정하고 초기 메시지를 로드한다", async () => {
		mockGet.mockResolvedValueOnce({ data: [msgFixture], error: null });
		const store = useChatStore();
		await store.setActiveChannel("1");
		expect(store.activeChannelId).toBe("1");
		expect(store.messages["1"]).toHaveLength(1);
	});
});
