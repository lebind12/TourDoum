import { useAuthStore } from "@/stores/auth";
import {
	useReservationsStore,
	validateReservationDates,
} from "@/stores/reservations";
import HomeView from "@/views/HomeView.vue";
import {
	type RouteLocationNormalized,
	createRouter,
	createWebHistory,
} from "vue-router";

type ReservationStep = "dates" | "payment" | "complete";

/** 인증이 필요한 라우트에 설정하는 메타 필드 */
declare module "vue-router" {
	interface RouteMeta {
		requiresAuth?: boolean;
		reservationStep?: ReservationStep;
	}
}

function getAccommodationId(to: RouteLocationNormalized): number | null {
	const raw = to.params.accommodationId;
	const id = Number(Array.isArray(raw) ? raw[0] : raw);
	return Number.isInteger(id) && id > 0 ? id : null;
}

function hasCompletedDraft(accommodationId: number): boolean {
	const reservations = useReservationsStore();
	const draft = reservations.current;

	return (
		draft?.accommodationId === accommodationId &&
		typeof draft.checkIn === "string" &&
		typeof draft.checkOut === "string" &&
		validateReservationDates(draft.checkIn, draft.checkOut) === null &&
		typeof draft.adults === "number" &&
		draft.adults >= 1 &&
		typeof draft.children === "number" &&
		draft.children >= 0
	);
}

const router = createRouter({
	history: createWebHistory(import.meta.env.BASE_URL),
	routes: [
		{
			path: "/",
			name: "home",
			component: HomeView,
		},
		{
			path: "/login",
			name: "login",
			component: () => import("@/views/LoginView.vue"),
		},
		{
			path: "/signup",
			name: "signup",
			component: () => import("@/views/SignupView.vue"),
		},
		{
			path: "/me",
			name: "me",
			component: () => import("@/views/MeView.vue"),
			meta: { requiresAuth: true },
		},
		// ── 여행 계획 (batch 2-A) ─────────────────────────────────────────
		{
			path: "/plans",
			name: "plans",
			component: () => import("@/views/PlansView.vue"),
			meta: { requiresAuth: true },
		},
		{
			path: "/plans/new",
			name: "plan-new",
			component: () => import("@/views/PlanNewView.vue"),
			meta: { requiresAuth: true },
		},
		{
			path: "/plans/:id",
			name: "plan-detail",
			component: () => import("@/views/PlanDetailView.vue"),
			meta: { requiresAuth: true },
		},
		// ── Mockup 화면 (Task #4) ───────────────────────────────────────────
		{
			path: "/attractions",
			name: "attractions",
			component: () => import("@/views/AttractionsView.vue"),
		},
		{
			path: "/attractions/:id",
			name: "attraction-detail",
			component: () => import("@/views/AttractionDetailView.vue"),
		},
		{
			path: "/favorites",
			name: "favorites",
			component: () => import("@/views/FavoritesView.vue"),
			meta: { requiresAuth: true },
		},
		{
			path: "/accommodations",
			name: "accommodations",
			component: () => import("@/views/AccommodationsView.vue"),
		},
		{
			path: "/accommodations/:id",
			name: "accommodation-detail",
			component: () => import("@/views/AccommodationDetailView.vue"),
		},
		{
			path: "/reservations/new/:accommodationId/dates",
			name: "reservation-dates",
			component: () => import("@/views/ReservationDatesView.vue"),
			meta: { requiresAuth: true, reservationStep: "dates" },
		},
		{
			path: "/reservations/new/:accommodationId/payment",
			name: "reservation-payment",
			component: () => import("@/views/ReservationPaymentView.vue"),
			meta: { requiresAuth: true, reservationStep: "payment" },
		},
		{
			path: "/reservations/new/:accommodationId/complete/:reservationId",
			name: "reservation-complete",
			component: () => import("@/views/ReservationCompleteView.vue"),
			meta: { requiresAuth: true, reservationStep: "complete" },
		},
		{
			path: "/chat",
			name: "chat",
			component: () => import("@/views/ChatView.vue"),
		},
		{
			path: "/chat/:channelId",
			name: "chat-channel",
			component: () => import("@/views/ChatChannelView.vue"),
		},
		{
			path: "/dm/:userId",
			name: "dm",
			component: () => import("@/views/DMView.vue"),
			meta: { requiresAuth: true },
		},
		// ── 통합 검색 / 알림 (batch 2-B) ────────────────────────────────────
		{
			path: "/search",
			name: "search",
			component: () => import("@/views/SearchView.vue"),
		},
		{
			path: "/notifications",
			name: "notifications",
			component: () => import("@/views/NotificationsView.vue"),
			meta: { requiresAuth: true },
		},
	],
});

// ── dev-only: 디자인 시스템 카탈로그 (Round 4 / G) ─────────────────────
// production build(import.meta.env.DEV === false)에서는 등록되지 않는다.
// 도메인 store / API import 0 — UI/UX 역할 격리 가드.
if (import.meta.env.DEV) {
	router.addRoute({
		path: "/dev/ui-catalog",
		name: "dev-ui-catalog",
		component: () => import("@/views/dev/UiCatalogView.vue"),
	});
	// Round 7: `/me` mock fixture — auth 없이 다크 contrast 점검.
	router.addRoute({
		path: "/dev/me-mock",
		name: "dev-me-mock",
		component: () => import("@/views/dev/MeMockView.vue"),
	});
}

router.beforeEach(async (to) => {
	const auth = useAuthStore();

	// 아직 사용자 정보를 불러오지 않은 경우 /api/me 를 시도
	if (auth.currentUser === null && !auth.loading) {
		await auth.fetchMe();
	}

	if (to.meta.requiresAuth && auth.currentUser === null) {
		return { name: "login" };
	}

	if (to.meta.reservationStep) {
		const accommodationId = getAccommodationId(to);
		if (accommodationId === null) return { name: "accommodations" };

		if (
			to.meta.reservationStep === "payment" &&
			!hasCompletedDraft(accommodationId)
		) {
			return { name: "reservation-dates", params: { accommodationId } };
		}

		if (to.meta.reservationStep === "complete") {
			const reservationId = String(to.params.reservationId ?? "");
			const reservations = useReservationsStore();

			const matches = (r: { id: string; accommodationId: number }) =>
				r.id === reservationId && r.accommodationId === accommodationId;

			// 1) 직전 confirm() 결과 — happy path
			let ok =
				reservations.lastConfirmed !== null &&
				matches(reservations.lastConfirmed);

			// 2) 이미 메모리에 적재된 내 예약 목록
			if (!ok) ok = reservations.myReservations.some(matches);

			// 3) 새로고침/직접 진입으로 store가 비어 있으면 1회 fetch 후 재검증
			if (!ok && reservations.myReservations.length === 0) {
				await reservations.fetchMyReservations();
				ok = reservations.myReservations.some(matches);
			}

			if (!ok) {
				return { name: "reservation-dates", params: { accommodationId } };
			}
		}
	}
});

export default router;
