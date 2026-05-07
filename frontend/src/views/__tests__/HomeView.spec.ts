// 학습 친화 모드: 신규 테스트는 사용자가 작성. 본 파일은 패턴 참고용.
// HomeView는 랜딩 페이지로 재설계됨 (Task #39) — 헬스체크 카드 제거, 4섹션 구성.
import { mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { createRouter, createWebHistory } from "vue-router";
import HomeView from "../HomeView.vue";

// jsdom에는 matchMedia / IntersectionObserver가 없으므로 mock 처리
Object.defineProperty(window, "matchMedia", {
	writable: true,
	value: vi.fn().mockImplementation((query: string) => ({
		matches: false,
		media: query,
		addEventListener: vi.fn(),
		removeEventListener: vi.fn(),
	})),
});

const router = createRouter({
	history: createWebHistory(),
	routes: [
		{ path: "/", component: HomeView },
		{ path: "/attractions", component: { template: "<div/>" } },
		{ path: "/accommodations", component: { template: "<div/>" } },
		{ path: "/login", component: { template: "<div/>" } },
		{ path: "/signup", component: { template: "<div/>" } },
		{ path: "/chat", component: { template: "<div/>" } },
		{ path: "/me", component: { template: "<div/>" } },
		{
			name: "attraction-detail",
			path: "/attractions/:id",
			component: { template: "<div/>" },
		},
		{
			name: "accommodation-detail",
			path: "/accommodations/:id",
			component: { template: "<div/>" },
		},
	],
});

describe("HomeView (랜딩 페이지)", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
	});

	it("Hero 섹션 헤딩이 렌더된다", async () => {
		const wrapper = mount(HomeView, {
			global: { plugins: [createPinia(), router] },
		});
		await router.isReady();
		expect(wrapper.text()).toContain("딱 맞는 하루");
	});

	it("Features 섹션의 차별점 카드가 4개 렌더된다", async () => {
		const wrapper = mount(HomeView, {
			global: { plugins: [createPinia(), router] },
		});
		await router.isReady();
		expect(wrapper.text()).toContain("취향 기반 추천");
		expect(wrapper.text()).toContain("동선 최적화");
		expect(wrapper.text()).toContain("일정 중심 경험");
		expect(wrapper.text()).toContain("즐겨찾기 & 기록");
	});

	it("Showcase 섹션에 인기 여행지 mock 데이터가 표시된다", async () => {
		const wrapper = mount(HomeView, {
			global: { plugins: [createPinia(), router] },
		});
		await router.isReady();
		expect(wrapper.text()).toContain("경복궁");
		expect(wrapper.text()).toContain("해운대 해수욕장");
	});

	it("CTA 섹션에 회원가입 버튼이 표시된다 (비로그인)", async () => {
		const wrapper = mount(HomeView, {
			global: { plugins: [createPinia(), router] },
		});
		await router.isReady();
		expect(wrapper.text()).toContain("무료로 시작하기");
	});
});
