<script setup lang="ts">
import ReservationListCard from "@/components/reservation/ReservationListCard.vue";
import { AlertDialog } from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import {
	Card,
	CardContent,
	CardDescription,
	CardHeader,
	CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useAccommodationsStore } from "@/stores/accommodations";
import { useAuthStore } from "@/stores/auth";
import { useFavoritesStore } from "@/stores/favorites";
import { usePlansStore } from "@/stores/plans";
import { useReservationsStore } from "@/stores/reservations";
import { computed, onMounted, ref } from "vue";
import { RouterLink, useRouter } from "vue-router";

type ListReservationStatus = "confirmed" | "cancelled" | "completed";

const authStore = useAuthStore();
const accommodationsStore = useAccommodationsStore();
const reservationsStore = useReservationsStore();
const favoritesStore = useFavoritesStore();
const plansStore = usePlansStore();
const router = useRouter();

// ── 내 예약 ──────────────────────────────────────────────────────────
const myReservations = computed(() =>
	reservationsStore.myReservations
		.map((reservation) => {
			const accommodation = accommodationsStore.getById(
				reservation.accommodationId,
			);
			const status: ListReservationStatus =
				reservation.status === "cancelled" ? "cancelled" : "confirmed";

			return {
				reservationId: reservation.id,
				accommodationName:
					accommodation?.name ?? `숙소 #${reservation.accommodationId}`,
				checkIn: reservation.checkIn,
				checkOut: reservation.checkOut,
				status,
				totalPrice: reservation.totalPrice, // BE가 제공
				createdAt: reservation.createdAt,
			};
		})
		.sort(
			(a, b) =>
				new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
		),
);

async function cancelReservation(reservationId: string) {
	await reservationsStore.cancelReservation(reservationId);
}

function goToAccommodations() {
	router.push("/accommodations");
}

onMounted(() => {
	reservationsStore.fetchMyReservations();
});

// ── 즐겨찾기 요약 ──────────────────────────────────────────────────────
const favoritesPreview = computed(() =>
	favoritesStore.favoriteAttractions.slice(0, 5),
);

// ── 여행 계획 요약 ──────────────────────────────────────────────────────
const plansPreview = computed(() => plansStore.sortedPlans.slice(0, 3));

// ── 비밀번호 변경 (mock) ───────────────────────────────────────────────
const pwCurrent = ref("");
const pwNew = ref("");
const pwConfirm = ref("");
const pwError = ref<string | null>(null);
const pwSuccess = ref(false);
const pwSubmitting = ref(false);
const showPwForm = ref(false);

async function changePassword() {
	if (!pwCurrent.value) {
		pwError.value = "현재 비밀번호를 입력해 주세요.";
		return;
	}
	if (pwNew.value.length < 6) {
		pwError.value = "새 비밀번호는 6자 이상이어야 합니다.";
		return;
	}
	if (pwNew.value !== pwConfirm.value) {
		pwError.value = "새 비밀번호가 일치하지 않습니다.";
		return;
	}
	pwError.value = null;
	pwSubmitting.value = true;
	await new Promise((r) => setTimeout(r, 300));
	// mockup: BE 미구현 — store action만
	pwSubmitting.value = false;
	pwSuccess.value = true;
	pwCurrent.value = "";
	pwNew.value = "";
	pwConfirm.value = "";
	showPwForm.value = false;
}

// ── 회원 탈퇴 (mock) ───────────────────────────────────────────────────
const withdrawing = ref(false);
const showWithdrawConfirm = ref(false);

async function withdraw() {
	withdrawing.value = true;
	await new Promise((r) => setTimeout(r, 300));
	// mockup: 실제 BE 미구현
	withdrawing.value = false;
	showWithdrawConfirm.value = false;
	alert("탈퇴가 완료되었습니다. (mockup — 실제 BE 미구현)");
	await authStore.logout();
	router.push("/");
}
</script>

<template>
  <div class="mx-auto max-w-lg space-y-6">
    <div>
      <h2 class="text-2xl font-semibold tracking-tight">내 정보</h2>
      <p class="text-sm text-muted-foreground mt-1">계정 정보를 확인합니다.</p>
    </div>

    <!-- 로딩 -->
    <div v-if="authStore.loading" class="text-sm text-muted-foreground animate-pulse">
      불러오는 중...
    </div>

    <template v-else-if="authStore.currentUser">
      <!-- ── 사용자 정보 카드 ───────────────────────────────────────────── -->
      <Card>
        <CardHeader>
          <CardTitle class="text-base">계정 정보</CardTitle>
          <CardDescription>가입한 계정의 기본 정보입니다.</CardDescription>
        </CardHeader>
        <CardContent>
          <dl class="space-y-0 divide-y divide-border">
            <div class="flex items-center gap-4 py-3 first:pt-0 last:pb-0">
              <dt class="w-20 flex-shrink-0 text-sm font-medium text-muted-foreground">ID</dt>
              <dd class="text-sm">{{ authStore.currentUser.id }}</dd>
            </div>
            <div class="flex items-center gap-4 py-3">
              <dt class="w-20 flex-shrink-0 text-sm font-medium text-muted-foreground">이메일</dt>
              <dd class="text-sm">{{ authStore.currentUser.email }}</dd>
            </div>
            <div class="flex items-center gap-4 py-3">
              <dt class="w-20 flex-shrink-0 text-sm font-medium text-muted-foreground">닉네임</dt>
              <dd class="text-sm font-medium text-primary">{{ authStore.currentUser.nickname }}</dd>
            </div>
            <div class="flex items-center gap-4 py-3">
              <dt class="w-20 flex-shrink-0 text-sm font-medium text-muted-foreground">역할</dt>
              <dd class="text-sm">
                <span class="inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold">
                  {{ authStore.currentUser.role }}
                </span>
              </dd>
            </div>
            <div v-if="authStore.currentUser.createdAt" class="flex items-center gap-4 py-3 last:pb-0">
              <dt class="w-20 flex-shrink-0 text-sm font-medium text-muted-foreground">가입일</dt>
              <dd class="text-sm text-muted-foreground">{{ authStore.currentUser.createdAt }}</dd>
            </div>
          </dl>
        </CardContent>
      </Card>

      <!-- ── 프로필 이미지 (placeholder) ───────────────────────────────── -->
      <Card>
        <CardHeader>
          <CardTitle class="text-base">프로필 이미지</CardTitle>
          <CardDescription>BE 연동 후 실제 업로드가 가능합니다.</CardDescription>
        </CardHeader>
        <CardContent>
          <div class="flex items-center gap-4">
            <div
              class="w-16 h-16 rounded-full bg-muted flex items-center justify-center text-2xl border"
              aria-label="프로필 이미지 placeholder"
            >
              👤
            </div>
            <Button variant="outline" size="sm" disabled aria-disabled="true">
              이미지 변경 (준비 중)
            </Button>
          </div>
        </CardContent>
      </Card>

      <!-- ── 비밀번호 변경 ──────────────────────────────────────────────── -->
      <Card>
        <CardHeader>
          <div class="flex items-center justify-between">
            <CardTitle class="text-base">비밀번호 변경</CardTitle>
            <Button
              variant="ghost"
              size="sm"
              @click="showPwForm = !showPwForm; pwError = null; pwSuccess = false"
            >
              {{ showPwForm ? '접기' : '변경하기' }}
            </Button>
          </div>
        </CardHeader>
        <CardContent v-if="showPwForm || pwSuccess">
          <!-- 성공 메시지 -->
          <div
            v-if="pwSuccess"
            class="rounded-md bg-green-50 dark:bg-green-950/30 border border-green-200 dark:border-green-900 px-4 py-3 text-sm text-green-700 dark:text-green-400"
            role="status"
            aria-live="polite"
          >
            비밀번호가 변경되었습니다.
          </div>
          <!-- 폼 -->
          <form v-else class="space-y-3" @submit.prevent="changePassword">
            <div class="space-y-1.5">
              <Label for="pw-current">현재 비밀번호</Label>
              <Input
                id="pw-current"
                v-model="pwCurrent"
                type="password"
                autocomplete="current-password"
                :aria-invalid="pwError === '현재 비밀번호를 입력해 주세요.' ? 'true' : undefined"
                :aria-describedby="pwError === '현재 비밀번호를 입력해 주세요.' ? 'pw-error' : undefined"
                :class="pwError === '현재 비밀번호를 입력해 주세요.' ? 'border-destructive focus-visible:ring-destructive/50' : ''"
              />
              <p
                v-if="pwError === '현재 비밀번호를 입력해 주세요.'"
                id="pw-error"
                role="alert"
                class="text-xs text-destructive"
              >{{ pwError }}</p>
            </div>
            <div class="space-y-1.5">
              <Label for="pw-new">새 비밀번호</Label>
              <Input
                id="pw-new"
                v-model="pwNew"
                type="password"
                autocomplete="new-password"
                :aria-invalid="pwError === '새 비밀번호는 6자 이상이어야 합니다.' ? 'true' : undefined"
                :class="pwError === '새 비밀번호는 6자 이상이어야 합니다.' ? 'border-destructive focus-visible:ring-destructive/50' : ''"
              />
              <p
                v-if="pwError === '새 비밀번호는 6자 이상이어야 합니다.'"
                role="alert"
                class="text-xs text-destructive"
              >{{ pwError }}</p>
              <!-- 강도 힌트 -->
              <p v-if="pwNew.length > 0 && pwNew.length < 6" class="text-xs text-amber-600 dark:text-amber-400">
                6자 이상 입력해 주세요 ({{ pwNew.length }}/6)
              </p>
            </div>
            <div class="space-y-1.5">
              <Label for="pw-confirm">새 비밀번호 확인</Label>
              <Input
                id="pw-confirm"
                v-model="pwConfirm"
                type="password"
                autocomplete="new-password"
                :aria-invalid="pwError === '새 비밀번호가 일치하지 않습니다.' ? 'true' : undefined"
                :class="pwError === '새 비밀번호가 일치하지 않습니다.' ? 'border-destructive focus-visible:ring-destructive/50' : (pwConfirm && pwConfirm === pwNew ? 'border-green-500' : '')"
              />
              <p
                v-if="pwError === '새 비밀번호가 일치하지 않습니다.'"
                role="alert"
                class="text-xs text-destructive"
              >{{ pwError }}</p>
              <p v-else-if="pwConfirm && pwConfirm === pwNew" class="text-xs text-green-600 dark:text-green-400">
                비밀번호가 일치합니다 ✓
              </p>
            </div>
            <Button type="submit" class="w-full" :disabled="pwSubmitting">
              <span
                v-if="pwSubmitting"
                class="mr-1.5 inline-block h-3.5 w-3.5 animate-spin rounded-full border-2 border-current border-t-transparent"
                aria-hidden="true"
              />
              {{ pwSubmitting ? '변경 중...' : '비밀번호 변경' }}
            </Button>
          </form>
        </CardContent>
      </Card>

      <!-- ── 내 즐겨찾기 요약 ───────────────────────────────────────────── -->
      <section aria-labelledby="my-favorites-heading">
        <div class="flex items-center justify-between mb-3">
          <h2 id="my-favorites-heading" class="text-lg font-semibold tracking-tight">내 즐겨찾기</h2>
          <RouterLink to="/favorites" class="text-sm text-muted-foreground hover:text-primary transition-colors">
            더보기 →
          </RouterLink>
        </div>
        <div v-if="favoritesPreview.length > 0" class="space-y-2">
          <RouterLink
            v-for="fav in favoritesPreview"
            :key="fav.id"
            :to="{ name: 'attraction-detail', params: { id: fav.id } }"
            class="flex items-center gap-3 rounded-lg border p-2.5 hover:bg-muted/50 transition-colors"
          >
            <img
              :src="fav.imageUrl"
              :alt="fav.name"
              class="w-12 h-12 rounded-md object-cover shrink-0"
              loading="lazy"
            />
            <div class="min-w-0">
              <p class="text-sm font-medium truncate">{{ fav.name }}</p>
              <p class="text-xs text-muted-foreground">{{ fav.category }} · {{ fav.sido }}</p>
            </div>
          </RouterLink>
        </div>
        <div
          v-else
          class="rounded-lg border border-dashed py-6 text-center text-sm text-muted-foreground"
          role="status"
        >
          즐겨찾기한 여행지가 없습니다.
          <RouterLink to="/attractions" class="underline underline-offset-2 ml-1 text-primary">여행지 보기</RouterLink>
        </div>
      </section>

      <!-- ── 내 여행 계획 요약 ──────────────────────────────────────────── -->
      <section aria-labelledby="my-plans-heading">
        <div class="flex items-center justify-between mb-3">
          <h2 id="my-plans-heading" class="text-lg font-semibold tracking-tight">내 여행 계획</h2>
          <RouterLink to="/plans" class="text-sm text-muted-foreground hover:text-primary transition-colors">
            전체 보기 →
          </RouterLink>
        </div>
        <div v-if="plansPreview.length > 0" class="space-y-2">
          <RouterLink
            v-for="plan in plansPreview"
            :key="plan.id"
            :to="{ name: 'plan-detail', params: { id: plan.id } }"
            class="flex items-center gap-3 rounded-lg border p-3 hover:bg-muted/50 transition-colors"
          >
            <span class="text-2xl shrink-0" aria-hidden="true">🗺️</span>
            <div class="min-w-0">
              <p class="text-sm font-medium truncate">{{ plan.title }}</p>
              <p class="text-xs text-muted-foreground">
                {{ new Date(plan.startDate).toLocaleDateString('ko-KR') }} ~
                {{ new Date(plan.endDate).toLocaleDateString('ko-KR') }}
                · {{ plan.days.length - 1 }}박 {{ plan.days.length }}일
              </p>
            </div>
          </RouterLink>
        </div>
        <div
          v-else
          class="rounded-lg border border-dashed py-6 text-center text-sm text-muted-foreground"
          role="status"
        >
          여행 계획이 없습니다.
          <RouterLink to="/plans/new" class="underline underline-offset-2 ml-1 text-primary">새 계획 만들기</RouterLink>
        </div>
      </section>

      <!-- ── 내 예약 섹션 ───────────────────────────────────────────── -->
      <section aria-labelledby="my-reservations-heading">
        <div class="flex items-center justify-between mb-3">
          <h2 id="my-reservations-heading" class="text-lg font-semibold tracking-tight">내 예약</h2>
        </div>

        <!-- 예약 있음 -->
        <div v-if="myReservations.length > 0" class="space-y-3">
          <ReservationListCard
            v-for="r in myReservations"
            :key="r.reservationId"
            :reservation-id="r.reservationId"
            :accommodation-name="r.accommodationName"
            :check-in="r.checkIn"
            :check-out="r.checkOut"
            :status="r.status"
            :total-price="r.totalPrice"
            @cancel="cancelReservation"
          />
        </div>

        <!-- 빈 상태 -->
        <div
          v-else
          class="flex flex-col items-center gap-3 rounded-lg border border-dashed py-10 px-4 text-center"
          role="status"
          aria-label="예약 없음"
        >
          <span class="text-4xl" aria-hidden="true">🗺️</span>
          <div class="space-y-1">
            <p class="text-sm font-medium">아직 예약한 숙소가 없습니다.</p>
            <p class="text-xs text-muted-foreground">여행을 시작해 보세요!</p>
          </div>
          <button
            type="button"
            class="mt-1 inline-flex items-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground
                   hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring transition-colors"
            aria-label="숙소 목록으로 이동하여 여행 시작하기"
            @click="goToAccommodations"
          >
            여행 시작하기
          </button>
        </div>
      </section>

      <!-- ── 회원 탈퇴 ──────────────────────────────────────────────────── -->
      <section aria-labelledby="withdraw-heading" class="pt-2 pb-4">
        <h2 id="withdraw-heading" class="sr-only">계정 삭제</h2>
        <div class="rounded-lg border border-destructive/20 bg-destructive/5 p-4">
          <div class="flex items-center justify-between gap-4">
            <div>
              <p class="text-sm font-medium text-destructive">계정 삭제</p>
              <p class="text-xs text-muted-foreground mt-0.5">탈퇴 후 모든 데이터가 영구 삭제됩니다.</p>
            </div>
            <Button
              variant="destructive"
              size="sm"
              class="shrink-0"
              @click="showWithdrawConfirm = true"
            >
              회원 탈퇴
            </Button>
          </div>
        </div>
      </section>

      <!-- 탈퇴 확인 AlertDialog -->
      <AlertDialog
        v-model:open="showWithdrawConfirm"
        title="정말 탈퇴하시겠습니까?"
        description="탈퇴 시 예약 내역, 여행 계획, 즐겨찾기 등 모든 데이터가 영구 삭제되며 복구할 수 없습니다."
        confirm-label="탈퇴 확인"
        cancel-label="취소"
        variant="destructive"
        :loading="withdrawing"
        @confirm="withdraw"
      />
    </template>

    <!-- 에러 -->
    <div
      v-else
      role="alert"
      aria-live="polite"
      class="rounded-md bg-destructive/10 px-4 py-3 text-sm text-destructive"
    >
      사용자 정보를 불러올 수 없습니다.
    </div>
  </div>
</template>
