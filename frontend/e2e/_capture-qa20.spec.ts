/**
 * QA-20 캡처 전용 보조 spec (CAPTURE_QA20=1 일 때만 실행).
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { type Page, expect, test } from "@playwright/test";

const RUN = process.env.CAPTURE_QA20 === "1";
test.skip(!RUN, "CAPTURE_QA20=1 일 때만 실행");

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const OUT = path.resolve(__dirname, "../../docs/screenshots/qa-20");

async function signupAndLogin(page: Page): Promise<string> {
	const ts = Date.now();
	const rand = Math.floor(Math.random() * 10000);
	const email = `qa-fav-cap-${ts}-${rand}@example.com`;
	const nickname = `qafc${ts}${rand}`.slice(0, 20);
	await page.goto("/signup");
	await page.getByLabel(/이메일|email/i).fill(email);
	await page
		.locator('form[aria-label="회원가입 폼"]')
		.getByLabel(/비밀번호|password/i)
		.fill("E2eTestSecure!9x");
	await page.getByLabel(/닉네임|nickname/i).fill(nickname);
	await page.getByTestId("signup-submit").click();
	await expect(page).toHaveURL("/");
	return nickname;
}

test("capture: favorites empty / add / list / remove", async ({ page }) => {
	fs.mkdirSync(OUT, { recursive: true });

	await signupAndLogin(page);

	// 1. 빈 상태
	await page.goto("/favorites");
	await expect(page.getByText("아직 즐겨찾기한 여행지가 없어요")).toBeVisible();
	await page.screenshot({
		path: path.join(OUT, "01-favorites-empty.png"),
		fullPage: true,
	});

	// 2. /attractions 즐겨찾기 추가
	await page.goto("/attractions");
	const addButton = page.locator('button[aria-label="즐겨찾기 추가"]').first();
	await expect(addButton).toBeVisible({ timeout: 10000 });
	await addButton.click();
	await page.screenshot({
		path: path.join(OUT, "02-attractions-after-add.png"),
		fullPage: false,
	});

	// 3. /favorites 1건 노출
	await page.goto("/favorites");
	await expect(page.locator("div.grid").first()).toBeVisible();
	await page.screenshot({
		path: path.join(OUT, "03-favorites-list.png"),
		fullPage: true,
	});

	// 4. 제거 후 빈 상태 복귀
	await page.locator('button[aria-label$="즐겨찾기 제거"]').first().click();
	await expect(page.getByText("아직 즐겨찾기한 여행지가 없어요")).toBeVisible();
	await page.screenshot({
		path: path.join(OUT, "04-favorites-after-remove.png"),
		fullPage: true,
	});
});
