<!--
  R11 Reservation Lab — fixture handler + 컴포넌트 시각 검증 라우트.

  /dev/r11-reservation-lab. dev 빌드 한정 (router에서 import.meta.env.DEV 가드).
  도메인 store / API import 0. UI/UX 역할 격리 유지.

  Sections:
    1. PeakSeasonBanner phase 3종 (pre / imminent / open)
    2. Queue Lobby 시나리오 (WAITING → ADMITTED 진행도)
    3. Reservation FSM 라벨 표 (사용자 라벨 vs 내부 enum)
    4. Toss Payments status 매핑 표
-->
<script setup lang="ts">
import {
	fixturePaymentStart,
	fixtureQueueStatus,
} from "@/api/fixtures/reservation";
import PeakSeasonBanner from "@/components/marketing/PeakSeasonBanner.vue";
import {
	type ReservationState,
	STATE_COLOR_TOKEN,
	TOSS_TO_INTERNAL,
	type TossStatus,
	USER_STATE_LABEL,
} from "@/types/reservation";
import { computed, ref } from "vue";

// ── 1. 배너 phase 시뮬레이션 — 시각 검증용 3종 시각.
const now = Date.now();
const bannerPre = new Date(
	now + 2 * 24 * 60 * 60 * 1000 + 14 * 3600 * 1000,
).toISOString(); // D-2 14h
const bannerImminent = new Date(now + 3 * 60 * 1000).toISOString(); // 3분 후
const bannerOpen = new Date(now - 60 * 1000).toISOString(); // 이미 오픈

// ── 2. Queue lobby 시뮬레이션 — tick slider.
const tickSeconds = ref(0);
const queueResp = computed(() =>
	fixtureQueueStatus({ tickSeconds: tickSeconds.value, initialPosition: 1247 }),
);
const queueProgressPct = computed(() => {
	const data = queueResp.value.data;
	if (!data) return 0;
	const start = 1247;
	return Math.round(((start - data.position) / start) * 100);
});

// ── 3. FSM 표 데이터.
const fsmStates: ReservationState[] = [
	"QUEUED",
	"ADMITTED",
	"INVENTORY_RESERVED",
	"PAYMENT_PENDING",
	"AUTHORIZED",
	"CAPTURED",
	"CONFIRMED",
	"REFUND_PENDING",
	"REFUNDED",
	"REJECTED",
	"CANCELLED",
	"FAILED",
	"REVERSED",
];

// ── 4. Toss 매핑 표.
const tossStatuses: TossStatus[] = [
	"READY",
	"IN_PROGRESS",
	"DONE",
	"ABORTED",
	"EXPIRED",
	"CANCELED",
	"PARTIAL_CANCELED",
];

// 선택된 Toss status에 대한 fixture payment 응답 (학습용 시각 확인).
const selectedToss = ref<TossStatus>("READY");
const paymentResp = computed(() =>
	fixturePaymentStart("TR-2026-LAB-001", selectedToss.value),
);
</script>

<template>
  <div class="mx-auto max-w-3xl space-y-8 p-4 sm:p-6">
    <header class="space-y-1">
      <h1 class="text-2xl font-bold tracking-tight">R11 예약 시퀀스 Lab</h1>
      <p class="text-sm text-muted-foreground">
        PeakSeasonBanner / Queue fixture / FSM 라벨 매핑 / Toss 매핑 시각 검증.
        dev 빌드 한정. 도메인 store/API import 0.
      </p>
    </header>

    <!-- ─── 1. PeakSeasonBanner 3종 ──────────────────────────────────── -->
    <section class="space-y-4" aria-label="PeakSeasonBanner phase 3종">
      <h2 class="text-lg font-semibold">1. PeakSeasonBanner — phase 3종</h2>

      <div>
        <p class="mb-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">phase: pre</p>
        <PeakSeasonBanner
          :open-at="bannerPre"
          :listing-count="230"
          :subscriber-count="1247"
          region-label="강원 동해안"
        />
      </div>

      <div>
        <p class="mb-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">phase: imminent (3분 전)</p>
        <PeakSeasonBanner
          :open-at="bannerImminent"
          :listing-count="42"
          :subscriber-count="2845"
          region-label="제주 서귀포"
          cancel-policy="체크인 3일 전까지 무료 취소"
        />
      </div>

      <div>
        <p class="mb-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">phase: open</p>
        <PeakSeasonBanner
          :open-at="bannerOpen"
          :listing-count="186"
          :subscriber-count="1923"
          region-label="부산 해운대"
        />
      </div>
    </section>

    <!-- ─── 2. Queue lobby fixture ───────────────────────────────────── -->
    <section class="space-y-3" aria-label="Queue lobby fixture 시뮬레이션">
      <h2 class="text-lg font-semibold">2. Queue Lobby fixture</h2>
      <label class="block text-sm">
        <span class="mr-2 text-muted-foreground">tick (s): {{ tickSeconds }}</span>
        <input
          type="range"
          min="0"
          max="260"
          step="10"
          v-model.number="tickSeconds"
          class="w-full"
          aria-label="가상 경과 시간 (초)"
        />
      </label>

      <div class="rounded-lg border border-border bg-card p-4">
        <p class="text-sm">
          status:
          <strong class="font-mono">{{ queueResp.data?.status }}</strong>
          · position
          <strong class="font-mono">{{ queueResp.data?.position }}</strong>
          · pollAfterMs
          <span class="font-mono text-muted-foreground">{{ queueResp.data?.pollAfterMs }}</span>
        </p>

        <!-- progress bar — --queue-progress-{from,to} 그라디언트. -->
        <div
          class="mt-3 h-2 w-full overflow-hidden rounded-full bg-muted"
          role="progressbar"
          :aria-valuenow="queueProgressPct"
          aria-valuemin="0"
          aria-valuemax="100"
          :aria-label="`대기 진행도 ${queueProgressPct}퍼센트`"
        >
          <div
            class="h-full transition-[width] duration-[var(--motion-base)] ease-[var(--ease-standard)]"
            :style="{
              width: `${queueProgressPct}%`,
              background: `linear-gradient(90deg, hsl(var(--queue-progress-from)), hsl(var(--queue-progress-to)))`,
            }"
          />
        </div>

        <p class="mt-3 text-xs text-muted-foreground">
          queueTokenExpiresAt:
          <span class="font-mono">{{ queueResp.data?.queueTokenExpiresAt }}</span>
        </p>
        <p class="text-xs text-muted-foreground">
          admissionExpiresAt:
          <span class="font-mono">{{ queueResp.data?.admissionExpiresAt ?? "(WAITING — null)" }}</span>
        </p>
        <p class="mt-2 text-xs text-muted-foreground">
          💡 queue token TTL은 서버 heartbeat로 연장되며, admission TTL은 5분 고정 (Codex #2).
        </p>
      </div>
    </section>

    <!-- ─── 3. FSM 사용자 라벨 매핑 표 ──────────────────────────────── -->
    <section class="space-y-3" aria-label="예약 FSM 사용자 라벨 매핑">
      <h2 class="text-lg font-semibold">3. FSM 사용자 라벨 매핑</h2>
      <p class="text-sm text-muted-foreground">
        내부 enum은 Timeline 접기에만 노출. UI가 직접 보여주는 텍스트는 사용자 라벨만 사용 (Codex #3).
      </p>
      <div class="overflow-x-auto rounded-lg border border-border">
        <table class="w-full text-sm">
          <thead class="bg-muted text-left text-xs uppercase tracking-wider text-muted-foreground">
            <tr>
              <th class="px-3 py-2">내부 state</th>
              <th class="px-3 py-2">사용자 라벨</th>
              <th class="px-3 py-2">색 토큰</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="s in fsmStates" :key="s" class="border-t border-border">
              <td class="px-3 py-2 font-mono text-xs">{{ s }}</td>
              <td class="px-3 py-2">
                <span
                  class="inline-block rounded-full px-2 py-0.5 text-xs font-medium"
                  :style="{
                    background: `hsl(var(${STATE_COLOR_TOKEN[s]}) / 0.15)`,
                    color: `hsl(var(${STATE_COLOR_TOKEN[s]}))`,
                  }"
                >
                  {{ USER_STATE_LABEL[s] }}
                </span>
              </td>
              <td class="px-3 py-2 font-mono text-xs text-muted-foreground">{{ STATE_COLOR_TOKEN[s] }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- ─── 4. Toss 매핑 표 + payment fixture ───────────────────────── -->
    <section class="space-y-3" aria-label="Toss Payments 상태 매핑">
      <h2 class="text-lg font-semibold">4. Toss Payments 매핑</h2>
      <p class="text-sm text-muted-foreground">
        외부 PG status → 내부 FSM state. 실 PG redirect/webhook은 BE-14/15 task (Codex #8 — UI mock 한정).
      </p>
      <div class="overflow-x-auto rounded-lg border border-border">
        <table class="w-full text-sm">
          <thead class="bg-muted text-left text-xs uppercase tracking-wider text-muted-foreground">
            <tr>
              <th class="px-3 py-2">Toss status</th>
              <th class="px-3 py-2">→ 내부 state</th>
              <th class="px-3 py-2">→ 사용자 라벨</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="t in tossStatuses" :key="t" class="border-t border-border">
              <td class="px-3 py-2 font-mono text-xs">{{ t }}</td>
              <td class="px-3 py-2 font-mono text-xs">{{ TOSS_TO_INTERNAL[t] }}</td>
              <td class="px-3 py-2 text-xs">{{ USER_STATE_LABEL[TOSS_TO_INTERNAL[t]] }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <label class="block text-sm">
        <span class="mr-2 text-muted-foreground">payment fixture 시뮬레이션:</span>
        <select v-model="selectedToss" class="rounded border border-input bg-background px-2 py-1 text-sm">
          <option v-for="t in tossStatuses" :key="t" :value="t">{{ t }}</option>
        </select>
      </label>
      <pre class="overflow-x-auto rounded-lg border border-border bg-muted p-3 text-xs">{{ JSON.stringify(paymentResp, null, 2) }}</pre>
    </section>
  </div>
</template>
