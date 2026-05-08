<!--
  PeakSeasonBanner — R11.2 성수기 폭주 시각화 (숙박앱 톤).

  Codex 5회차 정정 반영:
   #1 사실성: 티켓팅 톤(178k명) X. 자연수 1k~3k. 객실 수/날짜/인원/취소수수료/만료 hero 전면.
   #5 토큰: --banner-emphasis-{from,to} 그라디언트 + --countdown-warning-{bg,text}.
   #9 접근성: countdown은 aria-live=polite, CTA는 명시 라벨, 320px 줄바꿈 검증.

  본 컴포넌트는 lab 라우트(/dev/r11-reservation-lab) 시각 검증용. HomeView 실제
  mount는 R12 분할 — 본 라운드에서는 골격만 박제.
-->
<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";

interface Props {
	/** 오픈 시각 ISO. 카운트다운 기준. */
	openAt: string;
	/** 인기 숙소 수 — "230곳" 같은 자연수 톤. */
	listingCount: number;
	/** 알림 신청 사용자 수 — 1k~3k 범위 권장 (Codex #1). */
	subscriberCount: number;
	/** 인기 권역 라벨 (예: "강원 동해안"). */
	regionLabel: string;
	/** 취소 수수료 정책 한 줄 (Codex #1 — hero 전면). */
	cancelPolicy?: string;
}

const props = withDefaults(defineProps<Props>(), {
	cancelPolicy: "체크인 7일 전까지 무료 취소",
});

const now = ref(Date.now());
let timerId: number | null = null;

onMounted(() => {
	timerId = window.setInterval(() => {
		now.value = Date.now();
	}, 1000);
});

onUnmounted(() => {
	if (timerId !== null) window.clearInterval(timerId);
});

const remainingMs = computed(() =>
	Math.max(0, new Date(props.openAt).getTime() - now.value),
);

const phase = computed<"pre" | "imminent" | "open" | "closed">(() => {
	const ms = remainingMs.value;
	if (ms === 0) return "open";
	if (ms <= 5 * 60 * 1000) return "imminent";
	if (ms <= 7 * 24 * 60 * 60 * 1000) return "pre";
	return "pre";
});

const countdown = computed(() => {
	const ms = remainingMs.value;
	const totalSeconds = Math.floor(ms / 1000);
	const days = Math.floor(totalSeconds / 86400);
	const hours = Math.floor((totalSeconds % 86400) / 3600);
	const minutes = Math.floor((totalSeconds % 3600) / 60);
	const seconds = totalSeconds % 60;
	if (days > 0) {
		return `D-${days}  ${String(hours).padStart(2, "0")}:${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
	}
	return `${String(hours).padStart(2, "0")}:${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
});

const formattedSubscribers = computed(() =>
	props.subscriberCount.toLocaleString("ko-KR"),
);

const openDateLabel = computed(() => {
	const d = new Date(props.openAt);
	const month = d.getMonth() + 1;
	const day = d.getDate();
	const dayOfWeek = ["일", "월", "화", "수", "목", "금", "토"][d.getDay()];
	const hour = String(d.getHours()).padStart(2, "0");
	const minute = String(d.getMinutes()).padStart(2, "0");
	return `${month}/${day}(${dayOfWeek}) ${hour}:${minute} 동시 오픈`;
});
</script>

<template>
  <section
    class="peak-season-banner relative overflow-hidden rounded-2xl border border-border p-5 sm:p-6"
    :style="{
      background: `linear-gradient(135deg, hsl(var(--banner-emphasis-from)), hsl(var(--banner-emphasis-to)))`,
    }"
    aria-label="여행 성수기 예약 오픈 안내"
    :data-phase="phase"
  >
    <div class="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
      <div class="min-w-0 flex-1">
        <p class="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-primary">
          <span aria-hidden="true">🌊</span>
          여름 성수기 예약 오픈
        </p>
        <h2 class="mt-1.5 break-keep text-lg font-bold leading-tight text-foreground sm:text-xl">
          {{ regionLabel }} 인기 숙소 <span class="text-primary">{{ listingCount }}곳</span>
        </h2>
        <p class="mt-1 text-sm text-muted-foreground">{{ openDateLabel }}</p>
      </div>

      <!-- 카운트다운 — phase=imminent일 때 강조색 적용. -->
      <div
        class="shrink-0 self-start rounded-lg px-3 py-2 font-mono text-base font-bold tabular-nums"
        :class="
          phase === 'imminent'
            ? 'bg-[hsl(var(--countdown-warning-bg))] text-[hsl(var(--countdown-warning-text))]'
            : 'bg-card/70 text-foreground'
        "
        role="timer"
        aria-live="polite"
        :aria-label="`예약 오픈까지 남은 시간 ${countdown}`"
      >
        {{ phase === "open" ? "지금 오픈" : countdown }}
      </div>
    </div>

    <!-- 사실성 데이터: 알림 신청 수 + 취소 정책 (Codex #1). -->
    <dl class="mt-4 grid grid-cols-1 gap-2 text-sm sm:grid-cols-2">
      <div class="flex items-center gap-2">
        <dt class="shrink-0 text-muted-foreground" aria-hidden="true">📊</dt>
        <dd class="break-keep text-foreground">
          현재 <strong class="font-semibold">{{ formattedSubscribers }}명</strong>이 알림 신청
        </dd>
      </div>
      <div class="flex items-center gap-2">
        <dt class="shrink-0 text-muted-foreground" aria-hidden="true">💳</dt>
        <dd class="break-keep text-foreground">{{ cancelPolicy }}</dd>
      </div>
    </dl>

    <!-- CTA — phase별 라벨 변경. -->
    <div class="mt-4 flex flex-wrap gap-2">
      <button
        type="button"
        class="inline-flex h-10 items-center justify-center rounded-md border border-input bg-background px-4 text-sm font-medium hover:bg-accent hover:text-accent-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 transition-colors duration-[var(--motion-fast)] ease-[var(--ease-standard)]"
      >
        알림 받기
      </button>
      <button
        type="button"
        class="inline-flex h-10 items-center justify-center rounded-md bg-primary px-4 text-sm font-semibold text-primary-foreground shadow hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 transition-colors duration-[var(--motion-fast)] ease-[var(--ease-standard)] disabled:opacity-50"
        :disabled="phase === 'pre'"
        :aria-disabled="phase === 'pre'"
      >
        {{ phase === "open" || phase === "imminent" ? "지금 진입하기 →" : "오픈 임박 시 진입" }}
      </button>
    </div>

    <!-- 진입은 자동 대기열 순으로 처리 안내 — Codex #2 "순서 보존" 명시 -->
    <p class="mt-3 text-xs text-muted-foreground">
      💡 진입은 자동 대기열 순으로 처리되며, 새로고침 시 기존 순번이 보존됩니다.
    </p>
  </section>
</template>
