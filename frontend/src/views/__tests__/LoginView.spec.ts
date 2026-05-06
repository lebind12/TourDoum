import { mount } from "@vue/test-utils";
import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { createPinia, setActivePinia } from "pinia";
import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";
import { createRouter, createWebHistory } from "vue-router";
import LoginView from "../LoginView.vue";

// MSW 서버 설정 — /api/auth/login 엔드포인트를 모킹
// BE 응답 계약 (확정): {"id", "email", "nickname", "role": "ROLE_USER"}
const server = setupServer(
	http.post("http://localhost:8080/api/auth/login", () => {
		return HttpResponse.json({
			id: 1,
			email: "test@example.com",
			nickname: "테스터",
			role: "ROLE_USER",
		});
	}),
);

beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

function makeRouter() {
	return createRouter({
		history: createWebHistory(),
		routes: [
			{ path: "/", component: { template: "<div>Home</div>" } },
			{ path: "/login", component: LoginView },
			{ path: "/signup", component: { template: "<div>Signup</div>" } },
		],
	});
}

describe("LoginView", () => {
	it("이메일과 비밀번호 입력 후 로그인 버튼 클릭 시 성공하면 홈으로 이동한다", async () => {
		const pinia = createPinia();
		setActivePinia(pinia);

		const router = makeRouter();
		await router.push("/login");
		await router.isReady();

		const wrapper = mount(LoginView, {
			global: {
				plugins: [pinia, router],
			},
		});

		// 폼 입력
		await wrapper.find('input[type="email"]').setValue("test@example.com");
		await wrapper.find('input[type="password"]').setValue("password123");

		// 제출
		await wrapper.find("form").trigger("submit");

		// 비동기 처리 대기
		await new Promise((r) => setTimeout(r, 100));
		await wrapper.vm.$nextTick();

		// 라우터가 홈("/")으로 이동했는지 확인
		expect(router.currentRoute.value.path).toBe("/");
	});
});
