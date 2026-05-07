import { createPinia } from "pinia";
import { createApp } from "vue";

import App from "./App.vue";
import { registerUnauthorizedHandler } from "./api/auth-token";
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
// refresh 자동 갱신은 BE-2 머지 후 본 핸들러에서 후속 호출로 보강한다.
registerUnauthorizedHandler(() => {
	const auth = useAuthStore();
	auth.clearSession();
	if (router.currentRoute.value.name !== "login") {
		router.push({ name: "login" });
	}
});

app.mount("#app");
