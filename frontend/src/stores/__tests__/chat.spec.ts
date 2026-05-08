/**
 * useChatStore — keyset paging 단위 테스트 (ADR-0012 FE-1)
 *
 * BE 엔드포인트 mock:
 *   GET  /api/chat/channels/{id}/messages/older?[beforeCursor=]&limit=20  → ChatMessagePage
 *   GET  /api/chat/channels/{id}/messages?afterCursor=&limit=20          → ChatMessagePage
 *   GET  /api/chat/channels/{id}/messages?sinceId=N                       → 한시 호환 List
 *   POST /api/chat/channels/{id}/messages                                 → ChatMessageResponse
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

// ── 픽스처 ────────────────────────────────────────────────────────────────────
const channelFixture: ChatChannelApiResponse = {
	id: 1,
	name: "제주여행",
	type: "PUBLIC",
	createdAt: "2026-05-01T00:00:00",
	lastMessageId: 200,
	lastMessageAt: "2026-05-07T12:00:00",
};

const channelFixtureNullLast: ChatChannelApiResponse = {
	id: 2,
	name: "신규채널",
	type: "PUBLIC",
	createdAt: "2026-05-08T00:00:00",
	lastMessageId: null,
	lastMessageAt: null,
};

const dmChannelFixture: ChatChannelApiResponse = {
	id: 10,
	name: "DM-user101",
	type: "DM",
	createdAt: "2026-05-05T00:00:00",
	lastMessageId: 150,
	lastMessageAt: "2026-05-06T10:00:00",
};

function makeMessage(
	id: number,
	createdAt = "2026-05-07T10:00:00",
	channelId = 1,
): ChatMessageApiResponse {
	return {
		id,
		channelId,
		senderId: 42,
		content: `msg-${id}`,
		createdAt,
	};
}

function page(
	items: ChatMessageApiResponse[],
	nextCursor: string | null,
	hasMore = false,
): ChatMessagePageApiResponse {
	return { items, nextCursor, appliedLimit: 20, hasMore };
}

afterEach(() => {
	vi.resetAllMocks();
});

// ── fetchChannels — lastMessageAt DESC + NULL fallback ──────────────────────
describe("fetchChannels — 정렬 (lastMessageAt DESC, id DESC)", () => {
	beforeEach(() => setActivePinia(createPinia()));

	it("lastMessageAt 이 큰 채널이 먼저 온다", async () => {
		mockGet.mockResolvedValueOnce({
			data: [
				{ ...dmChannelFixture, lastMessageAt: "2026-05-06T10:00:00" },
				{ ...channelFixture, lastMessageAt: "2026-05-07T12:00:00" },
			],
			error: null,
		});
		const store = useChatStore();
		await store.fetchChannels();
		// channelFixture (id=1, lastMessageAt=2026-05-07) 가 sortedChannels 의 첫 번째.
		expect(store.sortedChannels[0].id).toBe("1");
		expect(store.sortedChannels[1].id).toBe("10");
	});

	it("lastMessageAt 이 null 인 채널은 createdAt 으로 비교한다", async () => {
		// channelFixtureNullLast: lastMessageAt=null, createdAt=2026-05-08 (가장 최근).
		// channelFixture: lastMessageAt=2026-05-07, createdAt=2026-05-01.
		mockGet.mockResolvedValueOnce({
			data: [channelFixture, channelFixtureNullLast],
			error: null,
		});
		const store = useChatStore();
		await store.fetchChannels();
		// null 채널의 fallback createdAt(2026-05-08) > channelFixture lastMessageAt(2026-05-07).
		expect(store.sortedChannels[0].id).toBe("2");
		expect(store.sortedChannels[1].id).toBe("1");
	});

	it("BE 응답의 lastMessageId/At 필드가 store 에 매핑된다", async () => {
		mockGet.mockResolvedValueOnce({
			data: [channelFixture, channelFixtureNullLast],
			error: null,
		});
		const store = useChatStore();
		await store.fetchChannels();
		const ch1 = store.channels.find((c) => c.id === "1");
		expect(ch1?.lastMessageId).toBe("200");
		expect(ch1?.lastMessageAt).toBe("2026-05-07T12:00:00");
		const ch2 = store.channels.find((c) => c.id === "2");
		expect(ch2?.lastMessageId).toBeNull();
		expect(ch2?.lastMessageAt).toBeNull();
	});
});

// ── fetchMessagesInitial — older 진입 (cursor 없음) ─────────────────────────
describe("fetchMessagesInitial — 채널 진입", () => {
	beforeEach(() => setActivePinia(createPinia()));

	it("messages 교체 + olderCursor 박제 + lastSeenId 박제 + hasMoreOlder", async () => {
		const items = [
			makeMessage(100, "2026-05-07T10:00:00"),
			makeMessage(101, "2026-05-07T10:01:00"),
		];
		mockGet.mockResolvedValueOnce({
			data: page(items, "older-cursor-abc", true),
			error: null,
		});
		const store = useChatStore();
		await store.fetchMessagesInitial("1");

		expect(store.messages["1"]).toHaveLength(2);
		expect(store.messages["1"][0].id).toBe("100");
		expect(store.olderCursors["1"]).toBe("older-cursor-abc");
		expect(store.newerCursors["1"]).toBeNull();
		expect(store.lastSeenIds["1"]).toBe("101");
		expect(store.hasMoreOlder["1"]).toBe(true);
	});

	it("정확한 path/limit 호출 — limit=20, beforeCursor 미포함", async () => {
		mockGet.mockResolvedValueOnce({
			data: page([], null, false),
			error: null,
		});
		const store = useChatStore();
		await store.fetchMessagesInitial("42");
		expect(mockGet).toHaveBeenCalledWith(
			"/api/chat/channels/42/messages/older?limit=20",
		);
	});

	it("hasMore=false 응답 — 추가 older 호출 정지", async () => {
		mockGet.mockResolvedValueOnce({
			data: page([makeMessage(100)], null, false),
			error: null,
		});
		const store = useChatStore();
		await store.fetchMessagesInitial("1");
		expect(store.hasMoreOlder["1"]).toBe(false);
		expect(store.olderCursors["1"]).toBeNull();
	});
});

// ── fetchOlder — 스크롤 업 (prepend + dedupe) ───────────────────────────────
describe("fetchOlder — 스크롤 업", () => {
	beforeEach(() => setActivePinia(createPinia()));

	it("prepend + dedupe + olderCursor 갱신", async () => {
		// 초기: id 200,201
		mockGet.mockResolvedValueOnce({
			data: page(
				[
					makeMessage(200, "2026-05-07T11:00:00"),
					makeMessage(201, "2026-05-07T11:01:00"),
				],
				"cursor-1",
				true,
			),
			error: null,
		});
		// older 페이지: id 198, 199 + 200(중복)
		mockGet.mockResolvedValueOnce({
			data: page(
				[
					makeMessage(198, "2026-05-07T10:58:00"),
					makeMessage(199, "2026-05-07T10:59:00"),
					makeMessage(200, "2026-05-07T11:00:00"), // 중복
				],
				"cursor-2",
				true,
			),
			error: null,
		});

		const store = useChatStore();
		await store.fetchMessagesInitial("1");
		const added = await store.fetchOlder("1");

		// 198, 199 만 신규 prepend (200 dedupe). 최종 [198, 199, 200, 201].
		expect(added).toBe(2);
		expect(store.messages["1"].map((m) => m.id)).toEqual([
			"198",
			"199",
			"200",
			"201",
		]);
		expect(store.olderCursors["1"]).toBe("cursor-2");
		expect(store.hasMoreOlder["1"]).toBe(true);
	});

	it("hasMoreOlder=false 시 호출 자체 skip — 네트워크 호출 0회", async () => {
		mockGet.mockResolvedValueOnce({
			data: page([makeMessage(100)], null, false),
			error: null,
		});
		const store = useChatStore();
		await store.fetchMessagesInitial("1");
		expect(store.hasMoreOlder["1"]).toBe(false);

		mockGet.mockClear();
		const added = await store.fetchOlder("1");
		expect(added).toBe(0);
		expect(mockGet).not.toHaveBeenCalled();
	});

	it("올바른 path — beforeCursor URL-encoded + limit=20", async () => {
		mockGet.mockResolvedValueOnce({
			data: page([makeMessage(100)], "raw cursor+/=", true),
			error: null,
		});
		const store = useChatStore();
		await store.fetchMessagesInitial("1");

		mockGet.mockResolvedValueOnce({
			data: page([], null, false),
			error: null,
		});
		await store.fetchOlder("1");
		expect(mockGet).toHaveBeenLastCalledWith(
			`/api/chat/channels/1/messages/older?beforeCursor=${encodeURIComponent("raw cursor+/=")}&limit=20`,
		);
	});

	it("status=400 (cursor 변조) → 폐기 + 초기 재로드 + error 메시지", async () => {
		// 초기
		mockGet.mockResolvedValueOnce({
			data: page([makeMessage(200)], "cursor-1", true),
			error: null,
		});
		// older 호출 = 400
		mockGet.mockResolvedValueOnce({
			data: null,
			error: "HTTP 400",
			status: 400,
		});
		// fallback 초기 재로드
		mockGet.mockResolvedValueOnce({
			data: page([makeMessage(300)], null, false),
			error: null,
		});

		const store = useChatStore();
		await store.fetchMessagesInitial("1");
		const added = await store.fetchOlder("1");

		expect(added).toBe(0);
		expect(store.error).toMatch(/잘못된 페이지 정보/);
		// 재로드된 결과가 적용됨.
		expect(store.messages["1"][0].id).toBe("300");
		expect(store.hasMoreOlder["1"]).toBe(false);
	});
});

// ── pollForward — 폴링 (append + dedupe + cursor 갱신) ──────────────────────
describe("pollForward — 폴링", () => {
	beforeEach(() => setActivePinia(createPinia()));

	it("newerCursor 가 없으면 sinceId 한시 호환 사용", async () => {
		// 초기 → lastSeenId=100, newerCursor=null.
		mockGet.mockResolvedValueOnce({
			data: page([makeMessage(100)], "older-cur", false),
			error: null,
		});
		const store = useChatStore();
		await store.fetchMessagesInitial("1");

		mockGet.mockResolvedValueOnce({
			data: page([makeMessage(101)], "newer-cur-1", false),
			error: null,
		});
		await store.pollForward("1");

		expect(mockGet).toHaveBeenLastCalledWith(
			"/api/chat/channels/1/messages?sinceId=100",
		);
		expect(store.messages["1"]).toHaveLength(2);
		expect(store.newerCursors["1"]).toBe("newer-cur-1");
		expect(store.lastSeenIds["1"]).toBe("101");
	});

	it("newerCursor 가 있으면 afterCursor 사용", async () => {
		mockGet.mockResolvedValueOnce({
			data: page([makeMessage(100)], "older-cur", false),
			error: null,
		});
		const store = useChatStore();
		await store.fetchMessagesInitial("1");

		// 첫 폴 — newer-cur-1 박제.
		mockGet.mockResolvedValueOnce({
			data: page([makeMessage(101)], "newer-cur-1", false),
			error: null,
		});
		await store.pollForward("1");

		// 두 번째 폴 — afterCursor=newer-cur-1
		mockGet.mockResolvedValueOnce({
			data: page([makeMessage(102)], "newer-cur-2", false),
			error: null,
		});
		await store.pollForward("1");

		expect(mockGet).toHaveBeenLastCalledWith(
			`/api/chat/channels/1/messages?afterCursor=${encodeURIComponent("newer-cur-1")}&limit=20`,
		);
		expect(store.newerCursors["1"]).toBe("newer-cur-2");
	});

	it("dedupe — 같은 id 메시지는 두 번 append 되지 않음", async () => {
		mockGet.mockResolvedValueOnce({
			data: page([makeMessage(100)], "older-cur", false),
			error: null,
		});
		const store = useChatStore();
		await store.fetchMessagesInitial("1");

		// 폴 응답에 100 재포함(race) + 101 신규.
		mockGet.mockResolvedValueOnce({
			data: page([makeMessage(100), makeMessage(101)], "newer-1", false),
			error: null,
		});
		await store.pollForward("1");
		expect(store.messages["1"].map((m) => m.id)).toEqual(["100", "101"]);
	});

	it("status=400 cursor 변조 → 초기 재로드", async () => {
		mockGet.mockResolvedValueOnce({
			data: page([makeMessage(100)], "older-cur", false),
			error: null,
		});
		const store = useChatStore();
		await store.fetchMessagesInitial("1");
		// 첫 폴 성공으로 newerCursor 박제.
		mockGet.mockResolvedValueOnce({
			data: page([makeMessage(101)], "newer-cur-1", false),
			error: null,
		});
		await store.pollForward("1");

		// 두 번째 폴 — 400.
		mockGet.mockResolvedValueOnce({
			data: null,
			error: "HTTP 400",
			status: 400,
		});
		// fallback 재로드.
		mockGet.mockResolvedValueOnce({
			data: page([makeMessage(200)], null, false),
			error: null,
		});

		await store.pollForward("1");
		expect(store.error).toMatch(/잘못된 페이지 정보/);
		expect(store.messages["1"][0].id).toBe("200");
	});

	it("초기 로드 전 폴링은 skip — 네트워크 호출 0회", async () => {
		const store = useChatStore();
		await store.pollForward("1");
		expect(mockGet).not.toHaveBeenCalled();
	});
});

// ── sendMessage ──────────────────────────────────────────────────────────────
describe("sendMessage", () => {
	beforeEach(() => setActivePinia(createPinia()));

	it("성공 시 append + lastSeenId 갱신", async () => {
		mockGet.mockResolvedValueOnce({
			data: page([makeMessage(100)], "older-cur", false),
			error: null,
		});
		const store = useChatStore();
		await store.fetchMessagesInitial("1");

		mockPost.mockResolvedValueOnce({
			data: makeMessage(150, "2026-05-07T12:00:00"),
			error: null,
		});
		await store.sendMessage("1", "안녕");
		expect(store.messages["1"]).toHaveLength(2);
		expect(store.messages["1"][1].id).toBe("150");
		expect(store.lastSeenIds["1"]).toBe("150");
	});

	it("실패 시 error 기록 + messages 미변경", async () => {
		mockPost.mockResolvedValueOnce({ data: null, error: "전송 실패" });
		const store = useChatStore();
		await store.sendMessage("1", "hello");
		expect(store.error).toBe("전송 실패");
		expect(store.messages["1"]).toBeUndefined();
	});

	it("send + 폴링 dedupe — 폴링 응답에 send 한 메시지 포함되어도 1건만 유지", async () => {
		mockGet.mockResolvedValueOnce({
			data: page([makeMessage(100)], "older-cur", false),
			error: null,
		});
		const store = useChatStore();
		await store.fetchMessagesInitial("1");

		mockPost.mockResolvedValueOnce({
			data: makeMessage(150, "2026-05-07T12:00:00"),
			error: null,
		});
		await store.sendMessage("1", "안녕");

		// 폴링 응답에 150 다시 포함.
		mockGet.mockResolvedValueOnce({
			data: page([makeMessage(150, "2026-05-07T12:00:00")], "newer-1", false),
			error: null,
		});
		await store.pollForward("1");

		expect(store.messages["1"].filter((m) => m.id === "150")).toHaveLength(1);
	});
});

// ── openDm ───────────────────────────────────────────────────────────────────
describe("openDm", () => {
	beforeEach(() => setActivePinia(createPinia()));

	it("성공 시 DM 채널을 반환하고 channels 에 추가한다", async () => {
		mockPost.mockResolvedValueOnce({ data: dmChannelFixture, error: null });
		const store = useChatStore();
		const ch = await store.openDm(101);
		expect(ch?.id).toBe("10");
		expect(ch?.type).toBe("DM");
		expect(store.channels).toHaveLength(1);
	});

	it("이미 channels 에 있는 경우 중복 추가하지 않는다", async () => {
		mockPost.mockResolvedValue({ data: dmChannelFixture, error: null });
		const store = useChatStore();
		await store.openDm(101);
		await store.openDm(101);
		expect(store.channels).toHaveLength(1);
	});
});

// ── 폴링 ────────────────────────────────────────────────────────────────────
describe("startPolling / stopPolling", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
		vi.useFakeTimers();
	});
	afterEach(() => {
		vi.useRealTimers();
	});

	it("2초 tick 으로 pollForward 호출 (sinceId fallback 사용)", async () => {
		// 초기 lastSeenId 박제.
		const store = useChatStore();
		store.lastSeenIds["1"] = "99";

		mockGet.mockResolvedValue({
			data: page([], null, false),
			error: null,
		});
		store.startPolling("1");

		await vi.advanceTimersByTimeAsync(6_000);
		expect(mockGet).toHaveBeenCalledTimes(3);
		expect(mockGet).toHaveBeenLastCalledWith(
			"/api/chat/channels/1/messages?sinceId=99",
		);
	});

	it("stopPolling 후 더 이상 호출 안 함", async () => {
		const store = useChatStore();
		store.lastSeenIds["1"] = "99";
		mockGet.mockResolvedValue({ data: page([], null, false), error: null });
		store.startPolling("1");
		store.stopPolling();
		await vi.advanceTimersByTimeAsync(6_000);
		expect(mockGet).not.toHaveBeenCalled();
	});

	it("중복 startPolling 호출 시 타이머 1개만 유지", async () => {
		const store = useChatStore();
		store.lastSeenIds["1"] = "99";
		mockGet.mockResolvedValue({ data: page([], null, false), error: null });
		store.startPolling("1");
		store.startPolling("1");
		await vi.advanceTimersByTimeAsync(4_000);
		expect(mockGet).toHaveBeenCalledTimes(2);
	});
});

// ── setActiveChannel ────────────────────────────────────────────────────────
describe("setActiveChannel", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
		vi.useFakeTimers();
	});
	afterEach(() => vi.useRealTimers());

	it("activeChannelId 설정 + 초기 older 로드 + cursor 박제", async () => {
		mockGet.mockResolvedValueOnce({
			data: page([makeMessage(100)], "older-cur", true),
			error: null,
		});
		const store = useChatStore();
		await store.setActiveChannel("1");
		expect(store.activeChannelId).toBe("1");
		expect(store.messages["1"]).toHaveLength(1);
		expect(store.olderCursors["1"]).toBe("older-cur");
	});
});
