<script setup lang="ts">
import { useAuthStore } from "@/stores/auth";
/**
 * MeMockView — `/me` mock fixture (dev-only, Round 7).
 *
 * 목적
 * - Round 5 다크 contrast 보강(--primary-foreground / --success-foreground / border 3.14:1)을
 *   인증 상태와 무관하게 시각 점검할 수 있게 한다.
 * - 본 회차에서는 BE-2 머지 후 FE-1 refresh interceptor가 미박제 → 일반 `/me` 진입은 401/redirect로
 *   다크 화면 캡처가 어렵다. 이 wrapper가 auth store를 mock user로 patch한 뒤 MeView를 그대로 렌더한다.
 *
 * 격리 (UI/UX 역할 제약)
 * - 도메인 store/api/router 신규 0. router는 `import.meta.env.DEV` 가드 1줄만 추가
 *   (R4 G `/dev/ui-catalog`와 동일 패턴).
 * - 본 컴포넌트는 production bundle에 등록되지 않는다.
 * - MeView 내부의 reservations/favorites/plans store fetch는 BE 부재 시 빈 배열을 반환 — 다크 contrast 검증
 *   목적엔 충분.
 */
import MeView from "@/views/MeView.vue";
import { onBeforeUnmount, onMounted } from "vue";

const auth = useAuthStore();

// 진입 시점에 가짜 user를 store에 박는다. localStorage/세션 토큰은 건드리지 않는다 — ADR-0011 메모리 정책 준수.
const previousUser = auth.currentUser;
onMounted(() => {
	auth.currentUser = {
		id: 0,
		email: "mock@tourdoum.dev",
		nickname: "다크모드 점검",
		role: "ROLE_MEMBER",
		createdAt: new Date().toISOString(),
	};
});

// dev 라우트를 떠나면 원복(다른 dev 라우트로 이동했을 때 부작용 0).
onBeforeUnmount(() => {
	auth.currentUser = previousUser;
});
</script>

<template>
  <div data-testid="dev-me-mock" class="min-h-screen bg-background text-foreground">
    <div class="border-b border-border bg-muted/40 px-4 py-2 text-xs text-muted-foreground">
      🛠 dev-only · `/dev/me-mock` — auth.currentUser를 mock으로 patch한 MeView wrapper.
      production 번들 미포함. (Round 7, R5 dark contrast 검증용)
    </div>
    <MeView />
  </div>
</template>
