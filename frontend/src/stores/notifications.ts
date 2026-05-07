import { get, post } from "@/api/client";
import { defineStore } from "pinia";
import { computed, ref } from "vue";

// ── FE 타입 ───────────────────────────────────────────────────────────────────
/** BE NotificationType enum (소문자 FE 표현) */
export type NotificationType =
	| "review_reply"
	| "reservation_confirmed"
	| "reservation_canceled"
	| "system";

export interface Notification {
	id: string; // BE Long → string
	type: NotificationType;
	title: string;
	body: string; // BE body (same field name)
	link?: string; // BE linkUrl
	isRead: boolean; // !unread
	createdAt: string;
}

// ── BE API 응답 타입 ─────────────────────────────────────────────────────────
export interface NotificationApiResponse {
	id: number;
	memberId: number;
	type: string; // 'REVIEW_REPLY' | 'RESERVATION_CONFIRMED' | 'RESERVATION_CANCELED' | 'SYSTEM'
	title: string;
	body: string;
	linkUrl: string | null;
	unread: boolean;
	createdAt: string;
	readAt: string | null;
}

export interface PageApiResponse<T> {
	content: T[];
	page: number;
	size: number;
	totalElements: number;
	totalPages: number;
	last: boolean;
}

export interface UnreadCountApiResponse {
	count: number;
}

// ── 매핑 함수 ─────────────────────────────────────────────────────────────────
function mapApiToNotification(r: NotificationApiResponse): Notification {
	return {
		id: r.id.toString(),
		type: r.type.toLowerCase() as NotificationType,
		title: r.title,
		body: r.body,
		link: r.linkUrl ?? undefined,
		isRead: !r.unread,
		createdAt: r.createdAt,
	};
}

// ── 폴링 간격 (ms) ────────────────────────────────────────────────────────────
const POLL_INTERVAL_MS = 15_000;

// ── Store ────────────────────────────────────────────────────────────────────
export const useNotificationsStore = defineStore("notifications", () => {
	const notifications = ref<Notification[]>([]);
	const unreadCount = ref(0);
	const loading = ref(false);
	const error = ref<string | null>(null);

	/** 폴링 interval ID */
	let _pollTimer: ReturnType<typeof setInterval> | null = null;
	/** visibilitychange 핸들러 참조 (cleanup용) */
	let _visibilityHandler: (() => void) | null = null;

	// ── computed ───────────────────────────────────────────────────────────────
	/** 최신 5건 (드로어용) */
	const recent = computed(() =>
		[...notifications.value]
			.sort(
				(a, b) =>
					new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
			)
			.slice(0, 5),
	);

	/** 날짜 그룹 (오늘/어제/이전) */
	function groupByDate(): {
		today: Notification[];
		yesterday: Notification[];
		earlier: Notification[];
	} {
		const now = new Date();
		const todayStr = now.toDateString();
		const yesterday = new Date(now);
		yesterday.setDate(yesterday.getDate() - 1);
		const yesterdayStr = yesterday.toDateString();

		const sorted = [...notifications.value].sort(
			(a, b) =>
				new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
		);

		return {
			today: sorted.filter(
				(n) => new Date(n.createdAt).toDateString() === todayStr,
			),
			yesterday: sorted.filter(
				(n) => new Date(n.createdAt).toDateString() === yesterdayStr,
			),
			earlier: sorted.filter(
				(n) =>
					new Date(n.createdAt).toDateString() !== todayStr &&
					new Date(n.createdAt).toDateString() !== yesterdayStr,
			),
		};
	}

	// ── API 액션 ───────────────────────────────────────────────────────────────
	/**
	 * GET /api/notifications — 알림 목록 (페이징)
	 * 기본 page=0, size=20
	 */
	async function fetchNotifications(page = 0, size = 20): Promise<void> {
		loading.value = true;
		error.value = null;

		const result = await get<PageApiResponse<NotificationApiResponse>>(
			`/api/notifications?page=${page}&size=${size}`,
		);
		loading.value = false;

		if (result.error || !result.data) {
			error.value = result.error ?? "알림을 불러오지 못했습니다.";
			return;
		}

		notifications.value = result.data.content.map(mapApiToNotification);
		// unreadCount도 함께 갱신
		unreadCount.value = notifications.value.filter((n) => !n.isRead).length;
	}

	/**
	 * GET /api/notifications/unread-count — 미읽음 수 폴링용
	 */
	async function fetchUnreadCount(): Promise<void> {
		const result = await get<UnreadCountApiResponse>(
			"/api/notifications/unread-count",
		);
		if (result.data) {
			unreadCount.value = result.data.count;
		}
	}

	/**
	 * POST /api/notifications/{id}/read — 단건 읽음 처리
	 */
	async function markAsRead(id: string): Promise<void> {
		// 낙관적 로컬 업데이트
		notifications.value = notifications.value.map((n) =>
			n.id === id ? { ...n, isRead: true } : n,
		);
		unreadCount.value = Math.max(0, unreadCount.value - 1);

		const result = await post<NotificationApiResponse>(
			`/api/notifications/${id}/read`,
			{},
		);
		if (result.error) {
			// 롤백
			notifications.value = notifications.value.map((n) =>
				n.id === id ? { ...n, isRead: false } : n,
			);
			unreadCount.value = unreadCount.value + 1;
			error.value = result.error;
		}
	}

	/**
	 * POST /api/notifications/read-all — 전체 읽음 처리
	 */
	async function markAllAsRead(): Promise<void> {
		// 낙관적 로컬 업데이트
		const prev = notifications.value.map((n) => ({ ...n }));
		const prevCount = unreadCount.value;
		notifications.value = notifications.value.map((n) => ({
			...n,
			isRead: true,
		}));
		unreadCount.value = 0;

		const result = await post<UnreadCountApiResponse>(
			"/api/notifications/read-all",
			{},
		);
		if (result.error) {
			// 롤백
			notifications.value = prev;
			unreadCount.value = prevCount;
			error.value = result.error;
		}
	}

	// ── 폴링 ──────────────────────────────────────────────────────────────────
	/**
	 * unread-count 폴링 시작.
	 * 15초 interval + visibilitychange 시 즉시 fetch.
	 * 중복 호출 방지 (이미 실행 중이면 noop).
	 */
	function startPolling(): void {
		if (_pollTimer !== null) return;

		// 즉시 1회 fetch
		fetchUnreadCount();

		_pollTimer = setInterval(() => {
			fetchUnreadCount();
		}, POLL_INTERVAL_MS);

		// visibilitychange: 탭 포커스 복귀 시 즉시 fetch
		_visibilityHandler = () => {
			if (document.visibilityState === "visible") {
				fetchUnreadCount();
			}
		};
		document.addEventListener("visibilitychange", _visibilityHandler);
	}

	/**
	 * 폴링 중단 (로그아웃, 언마운트 시 호출).
	 */
	function stopPolling(): void {
		if (_pollTimer !== null) {
			clearInterval(_pollTimer);
			_pollTimer = null;
		}
		if (_visibilityHandler !== null) {
			document.removeEventListener("visibilitychange", _visibilityHandler);
			_visibilityHandler = null;
		}
	}

	return {
		notifications,
		unreadCount,
		loading,
		error,
		recent,
		groupByDate,
		fetchNotifications,
		fetchUnreadCount,
		markAsRead,
		markAllAsRead,
		startPolling,
		stopPolling,
	};
});
