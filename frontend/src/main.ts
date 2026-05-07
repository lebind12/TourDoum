import { createPinia } from "pinia";
import { createApp } from "vue";

import App from "./App.vue";
import {
	registerRefreshHandler,
	registerUnauthorizedHandler,
} from "./api/auth-token";
import "./index.css";
import router from "./router";
import { useAuthStore } from "./stores/auth";

const app = createApp(App);
const pinia = createPinia();

app.use(pinia);
app.use(router);

// ── ADR-0011 FE-1 ─────────────────────────────────────────────────────────
// API client가 401을 만나면 호출되는 후크. 메모리 토큰 + user를 비우고 /login으로 보낸다.
// pinia 등록 이후에 useAuthStore()를 호출할 수 있어야 하므로 mount 직전에 등록.
// 라우터 가드의 fetchMe()와 중복 호출되어도 결과는 동일(이미 클리어된 상태).
// refresh 자동 갱신 후에도 401 이거나 refresh 가 실패하면 본 핸들러가 최종 처리.
registerUnauthorizedHandler(() => {
	const auth = useAuthStore();
	auth.clearSession();
	if (router.currentRoute.value.name !== "login") {
		router.push({ name: "login" });
	}
});

// ── ADR-0011 FE-1 보강 (be #63 BE-2 contract) ─────────────────────────────
// 401 만료 응답 시 client.ts 가 호출. auth store 의 refresh() 가 새 access/refresh 토큰을
// 발급받아 모듈 스코프에 박제하면 client.ts 가 원 요청을 1회 재시도한다. 동시 401 다발은
// auth-token.ts 의 in-flight Promise 단일화로 refresh 1회만 발사된다.
registerRefreshHandler(() => useAuthStore().refresh());

app.mount("#app");
