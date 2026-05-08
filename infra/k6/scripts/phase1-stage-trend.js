import { check, group } from "k6";
/**
 * QA-K6-stage-trend — 단계별 latency 분리 (signup → login → reserve).
 *
 * 목적: phase1-saturation 의 통합 p95 1.25s 가 어느 stage 에서 발생하는지 분리.
 *   - signup: BE-4 Argon2id 해시 (CPU bound, latency 큰 후보)
 *   - login: Argon2id verify
 *   - reserve: FSM + transition_log + outbox INSERT (DB IO)
 *
 * 시나리오: closed model (constant-vus) — VU 수 = 동시 사용자 수. 단일 사용자 1회
 *   순서대로 signup → login → reserve(N회). 단계별 p95 분리 측정.
 *
 * 실행:
 *   k6 run --summary-trend-stats="avg,min,med,p(95),p(99),max" \
 *          infra/k6/scripts/phase1-stage-trend.js
 *
 * 옵션:
 *   BASE_URL=http://localhost:30080
 *   STAGE_VUS=20 (default — 동시 사용자)
 *   STAGE_DURATION=2m (default — 측정 시간)
 *   RESERVE_PER_USER=10 (default — 사용자당 reserve 호출 수)
 */
import http from "k6/http";
import { Trend } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://localhost:30080";
const STAGE_VUS = Number.parseInt(__ENV.STAGE_VUS || "20", 10);
const STAGE_DURATION = __ENV.STAGE_DURATION || "2m";
const RESERVE_PER_USER = Number.parseInt(__ENV.RESERVE_PER_USER || "10", 10);
const PASSWORD = "K6LoadTestPass!9x";

// ── per-stage trend 분리 ─────────────────────────────────────────────
const signupLatency = new Trend("stage_signup_ms", true);
const loginLatency = new Trend("stage_login_ms", true);
const reserveLatency = new Trend("stage_reserve_ms", true);

export const options = {
	scenarios: {
		stage_trend: {
			executor: "constant-vus",
			vus: STAGE_VUS,
			duration: STAGE_DURATION,
		},
	},
	summaryTrendStats: ["avg", "min", "med", "p(95)", "p(99)", "max"],
};

let CSRF = null;

function getCsrf() {
	if (CSRF) return CSRF;
	const r = http.get(`${BASE_URL}/api/health`);
	const sc = r.headers["Set-Cookie"] || "";
	const m = sc.match(/XSRF-TOKEN=([^;\s]+)/);
	CSRF = m ? m[1] : null;
	return CSRF;
}

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

export default function () {
	const csrf = getCsrf();
	const ts = Date.now();
	const rand = Math.floor(Math.random() * 1e9);
	const email = `k6stg-${ts}-${rand}-${__VU}@stage.local`;
	const nickname = `k6s${ts}${rand}${__VU}`.slice(0, 20);

	let token = null;
	group("signup", () => {
		const r = http.post(
			`${BASE_URL}/api/members/signup`,
			JSON.stringify({ email, password: PASSWORD, nickname }),
			{ headers: { "Content-Type": "application/json" } },
		);
		signupLatency.add(r.timings.duration);
		check(r, { "signup 2xx": (x) => x.status === 200 || x.status === 201 });
	});

	group("login", () => {
		const r = http.post(
			`${BASE_URL}/api/auth/login`,
			JSON.stringify({ email, password: PASSWORD }),
			{ headers: { "Content-Type": "application/json" } },
		);
		loginLatency.add(r.timings.duration);
		const ok = check(r, { "login 200": (x) => x.status === 200 });
		if (ok) {
			try {
				token = JSON.parse(r.body).accessToken;
			} catch (_e) {
				token = null;
			}
		}
	});

	if (!token) return;

	group("reserve", () => {
		for (let i = 0; i < RESERVE_PER_USER; i++) {
			const offset = Math.floor(Math.random() * 350) + 7;
			const r = http.post(
				`${BASE_URL}/api/reservations`,
				JSON.stringify({
					accommodationId: 1,
					checkIn: dateAfter(offset),
					checkOut: dateAfter(offset + 1),
					guests: 1,
					paymentMethod: "CARD",
				}),
				{
					headers: {
						"Content-Type": "application/json",
						Authorization: `Bearer ${token}`,
						"Idempotency-Key": uuid(),
						"X-XSRF-TOKEN": csrf,
						Cookie: `XSRF-TOKEN=${csrf}`,
					},
				},
			);
			reserveLatency.add(r.timings.duration);
			check(r, { "reserve 201": (x) => x.status === 201 });
		}
	});
}
