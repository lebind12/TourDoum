/**
 * QA-26 캡처 전용 보조 spec (CAPTURE_QA26=1).
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { expect, test } from "@playwright/test";
import { signupAndLogin } from "./_helpers/auth";

const RUN = process.env.CAPTURE_QA26 === "1";
const BASE = process.env.VITE_API_BASE_URL ?? "http://localhost:30080";
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const OUT = path.resolve(__dirname, "../../docs/screenshots/qa-26");

function dateAfter(days: number): string {
	const d = new Date();
	d.setDate(d.getDate() + days);
	return d.toISOString().slice(0, 10);
}

test.describe("qa-26 캡처", () => {
	test.skip(!RUN, "CAPTURE_QA26=1 일 때만 실행");

	test("capture: plan 생성 / items / reorder / delete", async ({ page }) => {
		fs.mkdirSync(OUT, { recursive: true });
		await signupAndLogin(page, "qa26cap");

		// /plans/new 폼 캡처
		await page.goto("/plans/new");
		await page.locator("#plan-title").fill(`캡처 plan ${Date.now()}`);
		await page.locator("#plan-start").fill(dateAfter(7));
		await page.locator("#plan-end").fill(dateAfter(9));
		await page.screenshot({
			path: path.join(OUT, "01-plan-new-form.png"),
			fullPage: true,
		});
		await page.getByRole("button", { name: "계획 만들기" }).click();
		await expect(page).toHaveURL(/\/plans\/\d+/, { timeout: 10000 });
		const planId = page.url().split("/").pop();

		// API로 item 3개 추가 (FE에 add UI는 attraction detail 모달 경로라 우회)
		const attrRes = await page.request.get(`${BASE}/api/attractions?size=3`);
		const ids = (await attrRes.json()).content.map(
			(a: { id: number }) => a.id,
		);
		for (let i = 0; i < 3; i++) {
			await page.request.post(`${BASE}/api/plans/${planId}/items`, {
				data: {
					dayIndex: 0,
					orderIndex: i,
					targetType: "ATTRACTION",
					targetId: ids[i],
					memo: null,
				},
			});
		}
		await page.reload();
		await expect(
			page.locator("ol li[draggable='true']").first(),
		).toBeVisible();
		await page.screenshot({
			path: path.join(OUT, "02-plan-detail-3-items.png"),
			fullPage: true,
		});

		// reorder 후 캡처
		const detail = await (
			await page.request.get(`${BASE}/api/plans/${planId}`)
		).json();
		const items =
			detail.items ??
			detail.days?.flatMap((d: { items: { id: number }[] }) => d.items) ??
			[];
		const [a, b, c] = items;
		await page.request.patch(`${BASE}/api/plans/${planId}/items/reorder`, {
			data: {
				items: [
					{ id: Number(b.id), dayIndex: 0, orderIndex: 0 },
					{ id: Number(c.id), dayIndex: 0, orderIndex: 1 },
					{ id: Number(a.id), dayIndex: 0, orderIndex: 2 },
				],
			},
		});
		await page.reload();
		// fetchPlan 응답 도착 후 li 3개 렌더 보장 (캡처 race 방지)
		await expect(page.locator("ol li[draggable='true']")).toHaveCount(3);
		await page.screenshot({
			path: path.join(OUT, "03-plan-detail-after-reorder.png"),
			fullPage: true,
		});

		// 첫 item 삭제 후
		await page
			.locator("ol li[draggable='true']")
			.first()
			.locator('button[aria-label$="일정에서 제거"]')
			.click();
		await expect(page.locator("ol li[draggable='true']")).toHaveCount(2);
		await page.screenshot({
			path: path.join(OUT, "04-plan-detail-after-delete.png"),
			fullPage: true,
		});
	});
});
