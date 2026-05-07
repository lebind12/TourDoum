import {
	NOTIFICATIONS_STORAGE_KEY,
	useNotificationsStore,
} from "@/stores/notifications";
import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const localStorageMock = (() => {
	let store: Record<string, string> = {};
	return {
		getItem: vi.fn((key: string) => store[key] ?? null),
		setItem: vi.fn((key: string, value: string) => {
			store[key] = value;
		}),
		removeItem: vi.fn((key: string) => {
			delete store[key];
		}),
		clear: vi.fn(() => {
			store = {};
		}),
	};
})();

vi.stubGlobal("localStorage", localStorageMock);

describe("useNotificationsStore", () => {
	beforeEach(() => {
		localStorageMock.clear();
		setActivePinia(createPinia());
	});

	afterEach(() => {
		vi.clearAllMocks();
	});

	it("시드 데이터 8건이 초기 로드된다", () => {
		const store = useNotificationsStore();
		expect(store.notifications).toHaveLength(8);
	});

	it("unreadCount — 시드 중 미읽음 개수를 반환한다", () => {
		const store = useNotificationsStore();
		const expected = store.notifications.filter((n) => !n.isRead).length;
		expect(store.unreadCount).toBe(expected);
	});

	it("markAsRead() — 해당 알림을 읽음 처리한다", () => {
		const store = useNotificationsStore();
		const unread = store.notifications.find((n) => !n.isRead);
		if (!unread) throw new Error("미읽음 알림 없음");
		const beforeCount = store.unreadCount;
		store.markAsRead(unread.id);
		expect(store.unreadCount).toBe(beforeCount - 1);
		expect(store.notifications.find((n) => n.id === unread.id)?.isRead).toBe(
			true,
		);
	});

	it("markAllAsRead() — 모든 알림을 읽음 처리한다", () => {
		const store = useNotificationsStore();
		store.markAllAsRead();
		expect(store.unreadCount).toBe(0);
		expect(store.notifications.every((n) => n.isRead)).toBe(true);
	});

	it("recent — 최신 5건을 시간 내림차순으로 반환한다", () => {
		const store = useNotificationsStore();
		expect(store.recent.length).toBeLessThanOrEqual(5);
		for (let i = 0; i < store.recent.length - 1; i++) {
			expect(
				new Date(store.recent[i].createdAt).getTime(),
			).toBeGreaterThanOrEqual(
				new Date(store.recent[i + 1].createdAt).getTime(),
			);
		}
	});

	it("groupByDate() — 오늘/어제/이전 그룹을 반환한다", () => {
		const store = useNotificationsStore();
		const groups = store.groupByDate();
		const total =
			groups.today.length + groups.yesterday.length + groups.earlier.length;
		expect(total).toBe(store.notifications.length);
	});

	it("markAllAsRead 후 localStorage에 저장된다", async () => {
		const store = useNotificationsStore();
		store.markAllAsRead();
		await new Promise((r) => setTimeout(r, 0));
		expect(localStorageMock.setItem).toHaveBeenCalledWith(
			NOTIFICATIONS_STORAGE_KEY,
			expect.any(String),
		);
	});
});
