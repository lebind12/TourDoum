import { expect, test } from "@playwright/test";

const RUN = process.env.E2E_BACKEND === "1";

test.skip(!RUN, "E2E_BACKEND=1 환경변수 없음 — 실제 BE 필요. 스킵.");

function addDays(date: Date, days: number): string {
	const next = new Date(date);
	next.setDate(next.getDate() + days);
	return next.toISOString().slice(0, 10);
}

test("회원가입/로그인 후 숙박 예약 완료, 마이페이지 노출, 새로고침 유지", async ({
	page,
}) => {
	const ts = Date.now();
	const email = `reservation_${ts}@tourdoum.test`;
	const password = "E2eTestSecure!9x";
	const nickname = `예약자${ts % 10000}`;
	const checkIn = addDays(new Date(), 10);
	const checkOut = addDays(new Date(), 12);

	await page.goto("/signup");
	await page.fill('input[type="email"]', email);
	await page.fill('input[type="password"]', password);
	await page.fill("#nickname", nickname);
	await page.click('button[type="submit"]');
	await page.waitForURL("/");

	await page.goto("/accommodations/1");
	await page.getByRole("button", { name: "예약하기" }).click();
	await expect(page).toHaveURL(/\/reservations\/new\/1\/dates$/);

	await page.fill("#check-in", checkIn);
	await page.fill("#check-out", checkOut);
	await page.getByRole("button", { name: "성인 증가" }).click();
	await page.getByRole("button", { name: "결제 수단 선택으로 이동" }).click();
	await expect(page).toHaveURL(/\/reservations\/new\/1\/payment$/);

	await page.getByText("간편결제").click();
	await page.getByRole("button", { name: "예약 확인 및 결제" }).click();
	await expect(page).toHaveURL(/\/reservations\/new\/1\/complete\/R-/);
	await expect(page.getByLabel("예약 영수증")).toContainText(
		"제주 해변 리조트",
	);

	await page.getByRole("button", { name: "내 예약 목록 보기" }).click();
	await expect(page).toHaveURL("/me");
	await expect(page.locator("#my-reservations-heading")).toContainText(
		"내 예약",
	);

	const reservations = page.locator(
		'section[aria-labelledby="my-reservations-heading"]',
	);
	await expect(reservations).toContainText("제주 해변 리조트");

	await page.reload();
	await expect(reservations).toContainText("제주 해변 리조트");
});
