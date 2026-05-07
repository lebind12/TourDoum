/**
 * QA-24 캡처 전용 보조 spec (CAPTURE_QA24=1 일 때만 실행).
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { expect, test } from "@playwright/test";
import { signupAndLogin } from "./_helpers/auth";

const RUN = process.env.CAPTURE_QA24 === "1";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const OUT = path.resolve(__dirname, "../../docs/screenshots/qa-24");

function dateAfter(days: number): string {
	const d = new Date();
	d.setDate(d.getDate() + days);
	return d.toISOString().slice(0, 10);
}

test.describe("qa-24 캡처", () => {
	test.skip(!RUN, "CAPTURE_QA24=1 일 때만 실행");

	test("capture: dates / payment / idempotency trace / me", async ({
		page,
	}) => {
		fs.mkdirSync(OUT, { recursive: true });

		await signupAndLogin(page, "qa24cap");

		const r = await page.request.get(
			"http://localhost:8080/api/accommodations?size=1",
		);
		const accommodationId = (await r.json()).content[0].id;

		// Step 1
		await page.goto(`/reservations/new/${accommodationId}/dates`);
		await page.locator("#check-in").fill(dateAfter(20));
		await page.locator("#check-out").fill(dateAfter(22));
		await page.screenshot({
			path: path.join(OUT, "01-step1-dates.png"),
			fullPage: true,
		});
		await page
			.getByRole("button", { name: "결제 수단 선택으로 이동" })
			.click();

		// Step 2
		await expect(page.locator("h1")).toContainText("결제");
		await page.screenshot({
			path: path.join(OUT, "02-step2-payment.png"),
			fullPage: true,
		});

		// Idempotency trace
		const idempotencyKey = crypto.randomUUID();
		const checkIn = dateAfter(30);
		const checkOut = dateAfter(32);
		await page.request.post("http://localhost:8080/api/reservations/quote", {
			data: { accommodationId, checkIn, checkOut, guests: 2 },
		});
		const body = {
			accommodationId,
			checkIn,
			checkOut,
			guests: 2,
			paymentMethod: "CARD",
		};
		const r1 = await page.request.post(
			"http://localhost:8080/api/reservations",
			{ headers: { "Idempotency-Key": idempotencyKey }, data: body },
		);
		const r2 = await page.request.post(
			"http://localhost:8080/api/reservations",
			{ headers: { "Idempotency-Key": idempotencyKey }, data: body },
		);
		const trace = {
			idempotencyKey,
			body,
			call1: { status: r1.status(), body: await r1.json() },
			call2: { status: r2.status(), body: await r2.json() },
			sameId: (await r1.json()).id === (await r2.json()).id,
		};
		// 실제로 위 await는 stream 한 번씩만 가능 — 다시 캡처
		const c1 = await page.request.post(
			"http://localhost:8080/api/reservations",
			{
				headers: { "Idempotency-Key": idempotencyKey },
				data: body,
			},
		);
		const c1json = await c1.json();
		const c2 = await page.request.post(
			"http://localhost:8080/api/reservations",
			{
				headers: { "Idempotency-Key": idempotencyKey },
				data: body,
			},
		);
		const c2json = await c2.json();
		fs.writeFileSync(
			path.join(OUT, "idempotency-trace.json"),
			JSON.stringify(
				{
					idempotencyKey,
					call1: { status: c1.status(), id: c1json.id },
					call2: { status: c2.status(), id: c2json.id },
					sameReservationId: c1json.id === c2json.id,
				},
				null,
				2,
			),
		);

		// /me
		await page.goto("/me");
		await expect(
			page.locator(
				'section[aria-labelledby="my-reservations-heading"]',
			),
		).toBeVisible();
		await page.screenshot({
			path: path.join(OUT, "03-me-with-reservation.png"),
			fullPage: true,
		});
	});
});
