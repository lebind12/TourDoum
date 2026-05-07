/**
 * useNotificationsStore — API 연결 단위 테스트
 *
 * api/client.ts의 get/post를 vi.mock으로 교체.
 * setInterval/clearInterval/document.visibilityState는 vi.useFakeTimers로 제어.
 */
import {
	type NotificationApiResponse,
	type PageApiResponse,
	useNotificationsStore,
} from "@/stores/notifications";
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
const notifFixture: NotificationApiResponse = {
	id: 1,
	memberId: 10,
	type: "SYSTEM",
	title: "서비스 점검 안내",
	body: "5월 8일 새벽 2시~4시 서버 점검이 예정되어 있습니다.",
	linkUrl: null,
	unread: true,
	createdAt: "2026-05-07T10:00:00",
	readAt: null,
};

const notif2Fixture: NotificationApiResponse = {
	id: 2,
	memberId: 10,
	type: "RESERVATION_CONFIRMED",
	title: "예약이 확정되었습니다",
	body: "부산 해운대 게스트하우스 예약이 확정되었습니다.",
	linkUrl: "/me",
	unread: false,
	createdAt: "2026-05-06T10:00:00",
	readAt: "2026-05-06T11:00:00",
};

const pageFixture: PageApiResponse<NotificationApiResponse> = {
	content: [notifFixture, notif2Fixture],
	page: 0,
	size: 20,
	totalElements: 2,
	totalPages: 1,
	last: true,
};

describe("useNotificationsStore — 초기 상태", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
	});

	it("notifications 빈 배열, unreadCount 0", () => {
		const store = useNotificationsStore();
		expect(store.notifications).toHaveLength(0);
		expect(store.unreadCount).toBe(0);
		expect(store.loading).toBe(false);
		expect(store.error).toBeNull();
	});
});

describe("useNotificationsStore — fetchNotifications", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
		vi.clearAllMocks();
	});
	afterEach(() => vi.clearAllMocks());

	it("성공 시 notifications 갱신 + unreadCount 계산", async () => {
		mockGet.mockResolvedValueOnce({ data: pageFixture, error: null });

		const store = useNotificationsStore();
		await store.fetchNotifications();

		expect(mockGet).toHaveBeenCalledWith("/api/notifications?page=0&size=20");
		expect(store.notifications).toHaveLength(2);
		// unread: true → isRead: false
		expect(store.notifications[0].isRead).toBe(false);
		// unread: false → isRead: true
		expect(store.notifications[1].isRead).toBe(true);
		// unreadCount = 미읽음 수
		expect(store.unreadCount).toBe(1);
	});

	it("필드 매핑 — id Long→string, type 대소문자, linkUrl→link", async () => {
		mockGet.mockResolvedValueOnce({ data: pageFixture, error: null });

		const store = useNotificationsStore();
		await store.fetchNotifications();

		const n1 = store.notifications[0];
		expect(n1.id).toBe("1");
		expect(n1.type).toBe("system");
		expect(n1.body).toBe(notifFixture.body);
		expect(n1.link).toBeUndefined(); // linkUrl null → undefined

		const n2 = store.notifications[1];
		expect(n2.type).toBe("reservation_confirmed");
		expect(n2.link).toBe("/me"); // linkUrl → link
	});

	it("실패 시 error 세트", async () => {
		mockGet.mockResolvedValueOnce({ data: null, error: "HTTP 401" });

		const store = useNotificationsStore();
		await store.fetchNotifications();

		expect(store.error).toBe("HTTP 401");
		expect(store.notifications).toHaveLength(0);
	});

	it("page 파라미터 전달", async () => {
		mockGet.mockResolvedValueOnce({ data: pageFixture, error: null });

		const store = useNotificationsStore();
		await store.fetchNotifications(1, 10);

		expect(mockGet).toHaveBeenCalledWith("/api/notifications?page=1&size=10");
	});
});

describe("useNotificationsStore — fetchUnreadCount", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
		vi.clearAllMocks();
	});
	afterEach(() => vi.clearAllMocks());

	it("성공 시 unreadCount 갱신", async () => {
		mockGet.mockResolvedValueOnce({ data: { count: 5 }, error: null });

		const store = useNotificationsStore();
		await store.fetchUnreadCount();

		expect(mockGet).toHaveBeenCalledWith("/api/notifications/unread-count");
		expect(store.unreadCount).toBe(5);
	});
});

describe("useNotificationsStore — markAsRead", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
		vi.clearAllMocks();
	});
	afterEach(() => vi.clearAllMocks());

	it("성공 시 해당 알림 isRead=true, unreadCount 감소", async () => {
		mockGet.mockResolvedValueOnce({ data: pageFixture, error: null });
		const store = useNotificationsStore();
		await store.fetchNotifications();
		expect(store.unreadCount).toBe(1);

		mockPost.mockResolvedValueOnce({
			data: { ...notifFixture, unread: false, readAt: "2026-05-07T11:00:00" },
			error: null,
		});
		await store.markAsRead("1");

		expect(mockPost).toHaveBeenCalledWith("/api/notifications/1/read", {});
		expect(store.notifications.find((n) => n.id === "1")?.isRead).toBe(true);
		expect(store.unreadCount).toBe(0);
	});

	it("실패 시 롤백 — isRead 복원, unreadCount 복원", async () => {
		mockGet.mockResolvedValueOnce({ data: pageFixture, error: null });
		const store = useNotificationsStore();
		await store.fetchNotifications();

		mockPost.mockResolvedValueOnce({ data: null, error: "HTTP 403" });
		await store.markAsRead("1");

		// 롤백
		expect(store.notifications.find((n) => n.id === "1")?.isRead).toBe(false);
		expect(store.unreadCount).toBe(1);
		expect(store.error).toBe("HTTP 403");
	});
});

describe("useNotificationsStore — markAllAsRead", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
		vi.clearAllMocks();
	});
	afterEach(() => vi.clearAllMocks());

	it("성공 시 전체 isRead=true, unreadCount=0", async () => {
		mockGet.mockResolvedValueOnce({ data: pageFixture, error: null });
		const store = useNotificationsStore();
		await store.fetchNotifications();

		mockPost.mockResolvedValueOnce({ data: { count: 1 }, error: null });
		await store.markAllAsRead();

		expect(mockPost).toHaveBeenCalledWith("/api/notifications/read-all", {});
		expect(store.notifications.every((n) => n.isRead)).toBe(true);
		expect(store.unreadCount).toBe(0);
	});

	it("실패 시 롤백", async () => {
		mockGet.mockResolvedValueOnce({ data: pageFixture, error: null });
		const store = useNotificationsStore();
		await store.fetchNotifications();

		mockPost.mockResolvedValueOnce({ data: null, error: "HTTP 500" });
		await store.markAllAsRead();

		// 롤백: 기존 상태 복원
		expect(store.notifications.find((n) => n.id === "1")?.isRead).toBe(false);
		expect(store.unreadCount).toBe(1);
	});
});

describe("useNotificationsStore — computed (recent, groupByDate)", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
		vi.clearAllMocks();
	});

	it("recent — 최신 5건 이하 시간 내림차순", async () => {
		mockGet.mockResolvedValueOnce({ data: pageFixture, error: null });
		const store = useNotificationsStore();
		await store.fetchNotifications();

		const recent = store.recent;
		expect(recent.length).toBeLessThanOrEqual(5);
		for (let i = 0; i < recent.length - 1; i++) {
			expect(new Date(recent[i].createdAt).getTime()).toBeGreaterThanOrEqual(
				new Date(recent[i + 1].createdAt).getTime(),
			);
		}
	});

	it("groupByDate — 전체 합계 = notifications 길이", async () => {
		mockGet.mockResolvedValueOnce({ data: pageFixture, error: null });
		const store = useNotificationsStore();
		await store.fetchNotifications();

		const groups = store.groupByDate();
		const total =
			groups.today.length + groups.yesterday.length + groups.earlier.length;
		expect(total).toBe(store.notifications.length);
	});
});

describe("useNotificationsStore — 폴링 (startPolling/stopPolling)", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
		vi.useFakeTimers();
		vi.clearAllMocks();
	});
	afterEach(() => {
		vi.useRealTimers();
		vi.clearAllMocks();
	});

	it("startPolling — 즉시 fetchUnreadCount 호출", async () => {
		mockGet.mockResolvedValue({ data: { count: 3 }, error: null });
		const store = useNotificationsStore();
		store.startPolling();
		// Promise flush
		await Promise.resolve();

		expect(mockGet).toHaveBeenCalledWith("/api/notifications/unread-count");
		store.stopPolling();
	});

	it("startPolling — 15초마다 반복 호출", async () => {
		mockGet.mockResolvedValue({ data: { count: 0 }, error: null });
		const store = useNotificationsStore();
		store.startPolling();
		await Promise.resolve();

		const callsBefore = mockGet.mock.calls.length;

		vi.advanceTimersByTime(15_000);
		await Promise.resolve();

		expect(mockGet.mock.calls.length).toBeGreaterThan(callsBefore);
		store.stopPolling();
	});

	it("stopPolling — interval 중단", async () => {
		mockGet.mockResolvedValue({ data: { count: 0 }, error: null });
		const store = useNotificationsStore();
		store.startPolling();
		await Promise.resolve();
		store.stopPolling();

		const callsAfterStop = mockGet.mock.calls.length;
		vi.advanceTimersByTime(60_000);
		await Promise.resolve();

		// 중단 후 추가 호출 없음
		expect(mockGet.mock.calls.length).toBe(callsAfterStop);
	});

	it("startPolling 중복 호출 방지 — interval 1개만 생성", async () => {
		mockGet.mockResolvedValue({ data: { count: 0 }, error: null });
		const store = useNotificationsStore();
		store.startPolling();
		store.startPolling(); // 중복
		await Promise.resolve();

		const callsBefore = mockGet.mock.calls.length;
		vi.advanceTimersByTime(15_000);
		await Promise.resolve();

		// 2개 interval이면 2배 호출됨 — 1개만 있어야 함
		const callsAfter = mockGet.mock.calls.length;
		expect(callsAfter - callsBefore).toBe(1);
		store.stopPolling();
	});
});
