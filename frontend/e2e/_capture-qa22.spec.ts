/**
 * QA-22 캡처 전용 보조 spec (CAPTURE_QA22=1 일 때만 실행).
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { expect, test } from "@playwright/test";
import { signupAndLogin } from "./_helpers/auth";

const RUN = process.env.CAPTURE_QA22 === "1";
test.skip(!RUN, "CAPTURE_QA22=1 일 때만 실행");

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const OUT = path.resolve(__dirname, "../../docs/screenshots/qa-22");

test("capture: review form / submit / list", async ({ page }) => {
	fs.mkdirSync(OUT, { recursive: true });

	await signupAndLogin(page, "qacap");

	// 첫 attraction detail
	await page.goto("/attractions");
	await expect(
		page.locator('button[aria-label="즐겨찾기 추가"]').first(),
	).toBeVisible({ timeout: 10000 });
	await page.locator("h2").first().click();
	await expect(page).toHaveURL(/\/attractions\/\d+/);

	await page.screenshot({
		path: path.join(OUT, "01-detail-before-form.png"),
		fullPage: false,
	});

	// 폼 토글
	await page.getByRole("button", { name: "후기 작성" }).click();
	const textarea = page.locator("#review-comment");
	await expect(textarea).toBeVisible();

	const comment = `e2e 캡처 후기 ${Date.now()}`;
	await textarea.fill(comment);
	await page.screenshot({
		path: path.join(OUT, "02-form-filled.png"),
		fullPage: false,
	});

	// 제출 → 성공 메시지
	await page.getByRole("button", { name: "후기 등록" }).click();
	await expect(page.getByText("후기가 등록되었습니다!")).toBeVisible();
	await page.screenshot({
		path: path.join(OUT, "03-after-submit.png"),
		fullPage: true,
	});

	// 새로고침 → 목록 잔존
	await page.reload();
	await expect(page.locator('ul[aria-label="후기 목록"]')).toBeVisible({
		timeout: 5000,
	});
	await page.screenshot({
		path: path.join(OUT, "04-list-after-reload.png"),
		fullPage: true,
	});
});
