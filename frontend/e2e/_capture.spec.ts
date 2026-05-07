/**
 * QA-18 캡처 전용 보조 spec — full e2e 통과 후 스크린샷 + 네트워크 trace 박제용.
 * 일회성. 기본 실행에서 스킵 (CAPTURE_QA18=1 일 때만).
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { expect, test } from "@playwright/test";

const RUN = process.env.CAPTURE_QA18 === "1";
test.skip(!RUN, "CAPTURE_QA18=1 일 때만 실행");

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const OUT = path.resolve(__dirname, "../../docs/screenshots/qa-18");

test("capture: accommodations API 호출 + 헤더 trace", async ({ page }) => {
	fs.mkdirSync(OUT, { recursive: true });

	// 네트워크 trace 수집
	const trace: Array<Record<string, unknown>> = [];
	page.on("response", async (resp) => {
		const url = resp.url();
		if (!url.includes("/api/accommodations")) return;
		trace.push({
			url,
			status: resp.status(),
			method: resp.request().method(),
			requestHeaders: {
				origin: resp.request().headers()["origin"] ?? null,
				referer: resp.request().headers()["referer"] ?? null,
				cookie: resp.request().headers()["cookie"] ? "<redacted>" : null,
			},
			responseHeaders: {
				"access-control-allow-origin":
					resp.headers()["access-control-allow-origin"] ?? null,
				"access-control-allow-credentials":
					resp.headers()["access-control-allow-credentials"] ?? null,
				"content-type": resp.headers()["content-type"] ?? null,
			},
		});
	});

	await page.goto("/accommodations");
	// 결과 그리드가 떠야 API 호출이 끝난 것
	await expect(page.locator("div.grid").first()).toBeVisible({ timeout: 10000 });
	await page.waitForTimeout(500);

	await page.screenshot({
		path: path.join(OUT, "01-accommodations-list.png"),
		fullPage: true,
	});

	// 검색 + 필터 인터랙션 캡처
	await page
		.locator("input[placeholder*='숙소 이름 또는 지역']")
		.fill("서울");
	await page.waitForTimeout(300);
	await page.screenshot({
		path: path.join(OUT, "02-accommodations-search-seoul.png"),
		fullPage: true,
	});

	// 홈 #39 4섹션 캡처
	await page.goto("/");
	await expect(
		page.locator("section[aria-label='Hero — 서비스 소개']"),
	).toBeVisible();
	await page.screenshot({
		path: path.join(OUT, "03-home-landing-hero.png"),
		fullPage: false,
	});

	fs.writeFileSync(
		path.join(OUT, "network-trace.json"),
		JSON.stringify(trace, null, 2),
	);

	expect(trace.length).toBeGreaterThan(0);
	expect(trace[0]?.status).toBe(200);
});
