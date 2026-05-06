import { useAuthStore } from "@/stores/auth";
import HomeView from "@/views/HomeView.vue";
import { createRouter, createWebHistory } from "vue-router";

/** 인증이 필요한 라우트에 설정하는 메타 필드 */
declare module "vue-router" {
	interface RouteMeta {
		requiresAuth?: boolean;
	}
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
	],
});

router.beforeEach(async (to) => {
	const auth = useAuthStore();

	// 아직 사용자 정보를 불러오지 않은 경우 /api/me 를 시도
	if (auth.currentUser === null && !auth.loading) {
		await auth.fetchMe();
	}

	if (to.meta.requiresAuth && auth.currentUser === null) {
		return { name: "login" };
	}
});

export default router;
