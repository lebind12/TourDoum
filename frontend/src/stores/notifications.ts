import { defineStore } from "pinia";
import { computed, ref, watch } from "vue";

export const NOTIFICATIONS_STORAGE_KEY = "tourdoum-notifications-v1"; // gitleaks:allow

export type NotificationType =
	| "welcome"
	| "dm"
	| "reservation"
	| "review"
	| "system";

export interface Notification {
	id: string;
	type: NotificationType;
	title: string;
	body: string;
	link?: string;
	createdAt: string;
	isRead: boolean;
}

const SEED_NOTIFICATIONS: Notification[] = [
	{
		id: "notif-001",
		type: "welcome",
		title: "TourDoum에 오신 것을 환영합니다!",
		body: "회원가입을 완료하셨습니다. 다양한 여행지를 탐색해보세요.",
		link: "/attractions",
		createdAt: "2026-05-07T08:00:00Z",
		isRead: false,
	},
	{
		id: "notif-002",
		type: "dm",
		title: "새 DM이 도착했습니다",
		body: "호스트 김철수님이 메시지를 보냈습니다.",
		link: "/chat",
		createdAt: "2026-05-07T09:30:00Z",
		isRead: false,
	},
	{
		id: "notif-003",
		type: "dm",
		title: "숙박 호스트가 메시지를 보냈습니다",
		body: "제주 오션뷰 펜션 호스트: '체크인 관련 안내드립니다.'",
		link: "/chat",
		createdAt: "2026-05-06T14:00:00Z",
		isRead: false,
	},
	{
		id: "notif-004",
		type: "reservation",
		title: "예약이 확정되었습니다",
		body: "부산 해운대 게스트하우스 예약(7월 15일~17일)이 확정되었습니다.",
		link: "/me",
		createdAt: "2026-05-06T10:00:00Z",
		isRead: true,
	},
	{
		id: "notif-005",
		type: "review",
		title: "후기 작성을 추천합니다",
		body: "경복궁 방문은 어떠셨나요? 후기를 남겨보세요.",
		link: "/attractions/1",
		createdAt: "2026-05-05T18:00:00Z",
		isRead: true,
	},
	{
		id: "notif-006",
		type: "dm",
		title: "새 DM이 도착했습니다",
		body: "여행 메이트 이지연님이 여행 계획을 공유하고 싶어합니다.",
		link: "/chat",
		createdAt: "2026-05-05T11:00:00Z",
		isRead: true,
	},
	{
		id: "notif-007",
		type: "system",
		title: "서비스 점검 안내",
		body: "5월 8일 새벽 2시~4시 서버 점검이 예정되어 있습니다.",
		createdAt: "2026-05-04T09:00:00Z",
		isRead: true,
	},
	{
		id: "notif-008",
		type: "reservation",
		title: "체크인 D-1 알림",
		body: "내일 제주 오션뷰 펜션 체크인입니다. 즐거운 여행 되세요!",
		link: "/me",
		createdAt: "2026-05-03T08:00:00Z",
		isRead: true,
	},
];

function loadFromStorage(): Notification[] {
	try {
		const raw = localStorage.getItem(NOTIFICATIONS_STORAGE_KEY);
		if (!raw) return [...SEED_NOTIFICATIONS];
		return JSON.parse(raw) as Notification[];
	} catch {
		return [...SEED_NOTIFICATIONS];
	}
}

export const useNotificationsStore = defineStore("notifications", () => {
	const notifications = ref<Notification[]>(loadFromStorage());

	watch(
		notifications,
		(val) => {
			localStorage.setItem(NOTIFICATIONS_STORAGE_KEY, JSON.stringify(val));
		},
		{ deep: true },
	);

	const unreadCount = computed(
		() => notifications.value.filter((n) => !n.isRead).length,
	);

	const recent = computed(() =>
		[...notifications.value]
			.sort(
				(a, b) =>
					new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
			)
			.slice(0, 5),
	);

	function markAsRead(id: string) {
		notifications.value = notifications.value.map((n) =>
			n.id === id ? { ...n, isRead: true } : n,
		);
	}

	function markAllAsRead() {
		notifications.value = notifications.value.map((n) => ({
			...n,
			isRead: true,
		}));
	}

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

	return {
		notifications,
		unreadCount,
		recent,
		markAsRead,
		markAllAsRead,
		groupByDate,
	};
});
