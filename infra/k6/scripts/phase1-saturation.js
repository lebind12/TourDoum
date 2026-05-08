import { check, sleep } from "k6";
import { SharedArray } from "k6/data";
/**
 * QA-K6-1 — Phase 1 saturation curve (ADR-0013 §결정 (15) Phase 1 도달 신호 측정).
 *
 * 단일 Pod baseline:
 *   - BE 1 인스턴스 (localhost:30080, dev profile, 30080)
 *   - MySQL 1 (3306) + Redis 1 (6379)
 *
 * 시나리오: ramping-arrival-rate (closed model 아님 — 목표 RPS 직접 지정).
 *   100 → 3000 RPS 단계 ramp, 단계마다 30s 안정화. 총 약 5분.
 *
 * 측정 endpoint: `POST /api/reservations` (Idempotency-Key 헤더 + JWT Bearer).
 *   FSM trigger → reservations INSERT + transition_log INSERT + outbox INSERT (5요소).
 *
 * 인증: setup() 단계에서 N=20 dedicated 사용자 signup + login → access token 풀 회수.
 *   각 VU iteration이 랜덤 토큰으로 confirm 호출.
 *
 * 임계값:
 *   - http_req_duration p(95) < 200ms (ADR-0013 SLA)
 *   - http_req_failed rate < 1%
 *
 * 외부 검증 (k6 외):
 *   - outbox.claim_state distribution: PENDING/CLAIMED/DONE/FAILED 비율
 *   - outbox drain rate (events/sec, publisher 500ms tick × batch size)
 *
 * 실행:
 *   k6 run --summary-trend-stats="avg,min,med,p(95),p(99),max" \
 *          infra/k6/scripts/phase1-saturation.js
 *
 * 옵션:
 *   BASE_URL=http://localhost:30080 (default)
 *   POOL_SIZE=20 (default — pre-signup 사용자 수)
 *   ACCOMMODATION_ID=1 (default — DB seed 36건 중 첫 카드)
 */
import http from "k6/http";
import { Counter, Trend } from "k6/metrics";

// ── 환경 ─────────────────────────────────────────────────────────────
const BASE_URL = __ENV.BASE_URL || "http://localhost:30080";
const POOL_SIZE = Number.parseInt(__ENV.POOL_SIZE || "20", 10);
const ACCOMMODATION_ID = Number.parseInt(__ENV.ACCOMMODATION_ID || "1", 10);

// ── 커스텀 메트릭 ────────────────────────────────────────────────────
const reservationCreated = new Counter("reservation_created");
const reservationFailed = new Counter("reservation_failed");
const confirmLatency = new Trend("confirm_latency_ms", true);

// ── 시나리오 ─────────────────────────────────────────────────────────
export const options = {
	scenarios: {
		saturation: {
			executor: "ramping-arrival-rate",
			startRate: 50,
			timeUnit: "1s",
			preAllocatedVUs: 50,
			maxVUs: 500,
			stages: [
				{ target: 100, duration: "30s" },
				{ target: 300, duration: "30s" },
				{ target: 600, duration: "30s" },
				{ target: 1000, duration: "30s" },
				{ target: 1500, duration: "30s" },
				{ target: 2000, duration: "30s" },
				{ target: 2500, duration: "30s" },
				{ target: 3000, duration: "30s" },
				{ target: 3000, duration: "30s" }, // hold
			],
		},
	},
	thresholds: {
		http_req_duration: ["p(95)<200"], // ADR-0013 SLA
		http_req_failed: ["rate<0.01"], // <1% error
		// 단계별 abort 는 안 걸음 — 측정 목적이라 끝까지 돌려서 saturation point 확인.
	},
	summaryTrendStats: ["avg", "min", "med", "p(95)", "p(99)", "max"],
};

// ── BE-4 password 정책 통과 + 유사값 회피 ─────────────────────────────
const POOL_PASSWORD = "K6LoadTestPass!9x";

// ── pool: setup 에서 1회 생성, 이후 default 함수가 SharedArray 로 회수 ──
//   k6 SharedArray 는 init 단계에서만 채워질 수 있다. setup() 결과를
//   default 함수에서 사용할 수 있도록 `data` 인자로 전달한다.
function uuid() {
	return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
		const r = (Math.random() * 16) | 0;
		const v = c === "x" ? r : (r & 0x3) | 0x8;
		return v.toString(16);
	});
}

function dateAfter(days) {
	const d = new Date();
	d.setDate(d.getDate() + days);
	return d.toISOString().slice(0, 10);
}

export function setup() {
	const tokens = [];
	const ts = Date.now();
	// CSRF — `/api/health` 응답 Set-Cookie 에서 회수. mutation 호출에 X-XSRF-TOKEN +
	// Cookie 헤더 동봉 필요 (qa #28 §CSRF 박제).
	const healthRes = http.get(`${BASE_URL}/api/health`);
	const setCookie = healthRes.headers["Set-Cookie"] || "";
	const csrfMatch = setCookie.match(/XSRF-TOKEN=([^;\s]+)/);
	const csrfToken = csrfMatch ? csrfMatch[1] : null;
	if (!csrfToken) {
		throw new Error("setup: XSRF-TOKEN cookie 미설정 — BE-3 csrf 설정 확인");
	}
	for (let i = 0; i < POOL_SIZE; i++) {
		const email = `k6-${ts}-${i}@phase1.local`;
		const nickname = `k6u${ts}${i}`.slice(0, 20);
		const sup = http.post(
			`${BASE_URL}/api/members/signup`,
			JSON.stringify({ email, password: POOL_PASSWORD, nickname }),
			{ headers: { "Content-Type": "application/json" } },
		);
		if (sup.status !== 200 && sup.status !== 201) {
			console.warn(
				`[setup] signup ${i} failed: status=${sup.status} body=${sup.body?.slice(0, 200)}`,
			);
			continue;
		}
		const login = http.post(
			`${BASE_URL}/api/auth/login`,
			JSON.stringify({ email, password: POOL_PASSWORD }),
			{ headers: { "Content-Type": "application/json" } },
		);
		if (login.status !== 200) {
			console.warn(
				`[setup] login ${i} failed: status=${login.status} body=${login.body?.slice(0, 200)}`,
			);
			continue;
		}
		const body = JSON.parse(login.body);
		tokens.push(body.accessToken);
	}
	console.log(
		`[setup] tokens=${tokens.length}/${POOL_SIZE} csrf=${csrfToken.slice(0, 8)}... acc=${ACCOMMODATION_ID}`,
	);
	if (tokens.length === 0) {
		throw new Error(
			"setup: no tokens — abort. BE 30080 + V18 마이그레이션 + BE-4 정책 확인.",
		);
	}
	return { tokens, csrfToken };
}

export default function (data) {
	const token = data.tokens[Math.floor(Math.random() * data.tokens.length)];
	// 매 VU iteration 별 unique 날짜 + Idempotency-Key — UNIQUE / FK 충돌 회피.
	const offset = Math.floor(Math.random() * 350) + 7; // 7~357 일후
	const checkIn = dateAfter(offset);
	const checkOut = dateAfter(offset + 1);
	const idemKey = uuid();

	const payload = JSON.stringify({
		accommodationId: ACCOMMODATION_ID,
		checkIn,
		checkOut,
		guests: 1,
		paymentMethod: "CARD",
	});
	const headers = {
		"Content-Type": "application/json",
		Authorization: `Bearer ${token}`,
		"Idempotency-Key": idemKey,
		"X-XSRF-TOKEN": data.csrfToken,
		Cookie: `XSRF-TOKEN=${data.csrfToken}`,
	};

	const res = http.post(`${BASE_URL}/api/reservations`, payload, { headers });
	confirmLatency.add(res.timings.duration);

	const ok = check(res, {
		"status 201": (r) => r.status === 201,
		"has reservationId": (r) => {
			try {
				return JSON.parse(r.body).id > 0;
			} catch {
				return false;
			}
		},
	});
	if (ok) {
		reservationCreated.add(1);
	} else {
		reservationFailed.add(1);
	}
}

export function teardown(_data) {
	// 정리 — k6 종료 후 별도 SQL 로 outbox/reservation 카운트 확인.
	// (mysql client 호출은 k6 sandbox 외부, infra/k6/run-phase1.sh 에서 수행)
}
