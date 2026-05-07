<script setup lang="ts">
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { useAuthStore } from "@/stores/auth";
import {
	BookOpen,
	ChevronDown,
	Compass,
	Heart,
	MapPin,
	Star,
} from "lucide-vue-next";
import { computed, onMounted, onUnmounted, ref } from "vue";
import { RouterLink } from "vue-router";

const authStore = useAuthStore();

// ────────────────────────────────────────────────────────────
// prefers-reduced-motion 감지
// ────────────────────────────────────────────────────────────
const prefersReduced = ref(false);
let motionMq: MediaQueryList | null = null;

onMounted(() => {
	if (typeof window === "undefined" || !window.matchMedia) return;
	motionMq = window.matchMedia("(prefers-reduced-motion: reduce)");
	prefersReduced.value = motionMq.matches;
	motionMq.addEventListener("change", onMqChange);
});
onUnmounted(() => {
	motionMq?.removeEventListener("change", onMqChange);
});
function onMqChange(e: MediaQueryListEvent) {
	prefersReduced.value = e.matches;
}

// ────────────────────────────────────────────────────────────
// 섹션 dot 인디케이터
// ────────────────────────────────────────────────────────────
const currentSection = ref(0);
const sectionRefs = ref<HTMLElement[]>([]);
let io: IntersectionObserver | null = null;

onMounted(() => {
	if (typeof window === "undefined" || !window.IntersectionObserver) return;
	io = new IntersectionObserver(
		(entries) => {
			for (const e of entries) {
				if (e.isIntersecting) {
					const idx = sectionRefs.value.indexOf(e.target as HTMLElement);
					if (idx >= 0) currentSection.value = idx;
				}
			}
		},
		{ threshold: 0.5 },
	);
	for (const el of sectionRefs.value) io?.observe(el);
});
onUnmounted(() => io?.disconnect());

function setRef(el: HTMLElement | null, idx: number) {
	if (el) sectionRefs.value[idx] = el;
}

function scrollToSection(idx: number) {
	sectionRefs.value[idx]?.scrollIntoView({
		behavior: prefersReduced.value ? "instant" : "smooth",
	});
}

// ────────────────────────────────────────────────────────────
// snap wrapper 클래스
// ────────────────────────────────────────────────────────────
const snapClass = computed(() =>
	prefersReduced.value
		? "flex-1 overflow-y-auto snap-y snap-proximity overscroll-none"
		: "flex-1 overflow-y-scroll snap-y snap-mandatory overscroll-none",
);

// ────────────────────────────────────────────────────────────
// Features 데이터
// ────────────────────────────────────────────────────────────
const features = [
	{
		icon: Compass,
		title: "취향 기반 추천",
		desc: "인기순이 아닌 나의 여행 스타일에 맞춘 장소 추천. 무한 스크롤 대신 바로 가는 코스.",
		color: "text-sky-500",
		bg: "bg-sky-50 dark:bg-sky-950/30",
	},
	{
		icon: MapPin,
		title: "동선 최적화",
		desc: "가까운 장소를 자동으로 묶어 이동 시간을 최소화하는 스마트 경로 제안.",
		color: "text-emerald-500",
		bg: "bg-emerald-50 dark:bg-emerald-950/30",
	},
	{
		icon: BookOpen,
		title: "일정 중심 경험",
		desc: "단순 장소 목록이 아닌 하루 여행 흐름으로 보여주는 타임라인 플래너.",
		color: "text-violet-500",
		bg: "bg-violet-50 dark:bg-violet-950/30",
	},
	{
		icon: Heart,
		title: "즐겨찾기 & 기록",
		desc: "마음에 드는 여행지·숙박을 저장하고 다음 여행 계획에 바로 활용.",
		color: "text-rose-500",
		bg: "bg-rose-50 dark:bg-rose-950/30",
	},
];

// ────────────────────────────────────────────────────────────
// Showcase 정적 mock (API 호출 없음)
// ────────────────────────────────────────────────────────────
const mockAttractions = [
	{
		id: 1,
		name: "경복궁",
		location: "서울 종로구",
		category: "문화유적",
		rating: 4.8,
		emoji: "🏯",
		bgColor:
			"from-amber-100 to-orange-100 dark:from-amber-950/40 dark:to-orange-950/40",
	},
	{
		id: 2,
		name: "해운대 해수욕장",
		location: "부산 해운대구",
		category: "해변",
		rating: 4.7,
		emoji: "🏖️",
		bgColor:
			"from-sky-100 to-cyan-100 dark:from-sky-950/40 dark:to-cyan-950/40",
	},
	{
		id: 3,
		name: "한라산 국립공원",
		location: "제주특별자치도",
		category: "자연",
		rating: 4.9,
		emoji: "🗻",
		bgColor:
			"from-emerald-100 to-green-100 dark:from-emerald-950/40 dark:to-green-950/40",
	},
];

const mockAccommodations = [
	{
		id: 1,
		name: "파라다이스 호텔 부산",
		location: "부산 해운대구",
		type: "호텔",
		price: "250,000",
		emoji: "🏨",
		bgColor:
			"from-blue-100 to-indigo-100 dark:from-blue-950/40 dark:to-indigo-950/40",
	},
	{
		id: 2,
		name: "북촌 한옥스테이",
		location: "서울 종로구",
		type: "한옥",
		price: "120,000",
		emoji: "🏡",
		bgColor:
			"from-orange-100 to-amber-100 dark:from-orange-950/40 dark:to-amber-950/40",
	},
	{
		id: 3,
		name: "제주 스테이",
		location: "제주시 애월읍",
		type: "펜션",
		price: "85,000",
		emoji: "🌴",
		bgColor:
			"from-teal-100 to-emerald-100 dark:from-teal-950/40 dark:to-emerald-950/40",
	},
];
</script>

<template>
  <!-- 전체 스냅 스크롤 컨테이너 (AppShell <main> 가득 채움) -->
  <div :class="snapClass" aria-label="랜딩 페이지 섹션">

    <!-- ══════════════════════════════════════════════
         섹션 1 — Hero
    ══════════════════════════════════════════════ -->
    <section
      :ref="(el) => setRef(el as HTMLElement | null, 0)"
      class="snap-start snap-always relative flex h-screen flex-col items-center justify-center overflow-hidden px-6 text-center"
      aria-label="Hero — 서비스 소개"
    >
      <!-- 배경 그래디언트 -->
      <div
        class="pointer-events-none absolute inset-0 bg-gradient-to-br from-sky-50 via-background to-primary/5 dark:from-sky-950/30 dark:via-background dark:to-primary/10"
        aria-hidden="true"
      />
      <!-- 배경 장식 원 -->
      <div
        class="pointer-events-none absolute -top-32 -right-32 h-[480px] w-[480px] rounded-full bg-sky-400/10 blur-3xl dark:bg-sky-500/10"
        aria-hidden="true"
      />
      <div
        class="pointer-events-none absolute -bottom-24 -left-24 h-[320px] w-[320px] rounded-full bg-primary/10 blur-3xl"
        aria-hidden="true"
      />

      <!-- 콘텐츠 -->
      <div class="relative z-10 flex flex-col items-center gap-6 max-w-3xl">
        <!-- 배지 -->
        <span class="inline-flex items-center gap-1.5 rounded-full border border-sky-200 bg-sky-50 px-4 py-1 text-xs font-semibold text-sky-600 dark:border-sky-800 dark:bg-sky-950/50 dark:text-sky-400">
          ✈️ TourDoum — 여행 추천 플랫폼
        </span>

        <!-- 메인 헤딩 -->
        <h1 class="text-4xl font-extrabold leading-tight tracking-tight text-foreground sm:text-5xl md:text-6xl">
          여행지 고르느라 지친 당신에게,<br />
          <span class="text-primary">딱 맞는 하루</span>를 추천합니다
        </h1>

        <!-- 서브 헤딩 -->
        <p class="max-w-xl text-base text-muted-foreground sm:text-lg">
          취향과 일정만 넣으면 TourDoum이 여행 코스를 바로 제안해요.
          인기 여행지부터 숙박까지 한 화면에서 끝까지.
        </p>

        <!-- CTA 버튼 -->
        <div class="flex flex-wrap items-center justify-center gap-3">
          <RouterLink to="/attractions">
            <Button size="lg" class="gap-2 px-8 text-base shadow-md">
              <Compass class="h-5 w-5" />
              여행지 보기
            </Button>
          </RouterLink>
          <RouterLink to="/accommodations">
            <Button size="lg" variant="outline" class="gap-2 px-8 text-base">
              <MapPin class="h-5 w-5" />
              숙박 검색
            </Button>
          </RouterLink>
        </div>

        <!-- 로그인 유도 (비로그인 시) -->
        <p v-if="!authStore.currentUser" class="text-xs text-muted-foreground">
          이미 계정이 있으신가요?
          <RouterLink to="/login" class="font-medium text-primary underline-offset-4 hover:underline">로그인</RouterLink>
        </p>
        <p v-else class="text-sm text-muted-foreground">
          안녕하세요, <strong class="font-semibold text-foreground">{{ authStore.currentUser.nickname }}</strong>님! 오늘의 여행을 시작해볼까요?
        </p>
      </div>

      <!-- 아래 화살표 -->
      <button
        type="button"
        class="absolute bottom-8 left-1/2 -translate-x-1/2 flex flex-col items-center gap-1 text-muted-foreground opacity-70 transition-opacity hover:opacity-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
        aria-label="다음 섹션으로 스크롤"
        @click="scrollToSection(1)"
      >
        <span class="text-xs">스크롤</span>
        <ChevronDown class="h-5 w-5 motion-safe:animate-bounce" />
      </button>
    </section>

    <!-- ══════════════════════════════════════════════
         섹션 2 — Features
    ══════════════════════════════════════════════ -->
    <section
      :ref="(el) => setRef(el as HTMLElement | null, 1)"
      class="snap-start snap-always relative flex min-h-screen flex-col items-center justify-center overflow-hidden px-6 py-16"
      aria-label="주요 기능"
    >
      <!-- 배경 -->
      <div
        class="pointer-events-none absolute inset-0 bg-background"
        aria-hidden="true"
      />

      <div class="relative z-10 flex w-full max-w-5xl flex-col items-center gap-12">
        <!-- 섹션 헤더 -->
        <div class="text-center">
          <p class="mb-2 text-sm font-semibold uppercase tracking-widest text-primary">Why TourDoum</p>
          <h2 class="text-3xl font-bold tracking-tight text-foreground sm:text-4xl">
            여행, 이렇게 달라집니다
          </h2>
          <p class="mt-3 text-muted-foreground">
            막막했던 여행 계획, TourDoum과 함께라면 30분 안에 완성됩니다.
          </p>
        </div>

        <!-- 피처 카드 그리드 -->
        <div class="grid w-full grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Card
            v-for="f in features"
            :key="f.title"
            class="border-border/50 motion-safe:transition-[transform,box-shadow] motion-safe:duration-200 motion-safe:ease-out motion-safe:hover:-translate-y-1 hover:shadow-lg"
          >
            <CardContent class="flex flex-col gap-3 p-6">
              <div :class="['flex h-12 w-12 items-center justify-center rounded-xl', f.bg]">
                <component :is="f.icon" :class="['h-6 w-6', f.color]" />
              </div>
              <h3 class="font-semibold text-foreground">{{ f.title }}</h3>
              <p class="text-sm leading-relaxed text-muted-foreground">{{ f.desc }}</p>
            </CardContent>
          </Card>
        </div>
      </div>
    </section>

    <!-- ══════════════════════════════════════════════
         섹션 3 — Showcase
    ══════════════════════════════════════════════ -->
    <section
      :ref="(el) => setRef(el as HTMLElement | null, 2)"
      class="snap-start snap-always relative flex min-h-screen flex-col items-center justify-center overflow-hidden px-6 py-16"
      aria-label="인기 여행지 · 숙박 미리보기"
    >
      <!-- 배경 -->
      <div
        class="pointer-events-none absolute inset-0 bg-muted/30 dark:bg-muted/10"
        aria-hidden="true"
      />

      <div class="relative z-10 flex w-full max-w-5xl flex-col items-center gap-12">
        <!-- 섹션 헤더 -->
        <div class="text-center">
          <p class="mb-2 text-sm font-semibold uppercase tracking-widest text-primary">Discover</p>
          <h2 class="text-3xl font-bold tracking-tight text-foreground sm:text-4xl">
            지금 인기 있는 여행지 & 숙박
          </h2>
          <p class="mt-3 text-muted-foreground">
            수천 명이 선택한 베스트 코스와 숙소를 미리 확인하세요.
          </p>
        </div>

        <!-- 여행지 -->
        <div class="w-full">
          <div class="mb-4 flex items-center justify-between">
            <h3 class="font-semibold text-foreground">인기 여행지</h3>
            <RouterLink to="/attractions" class="text-sm text-primary hover:underline underline-offset-4">
              전체 보기 →
            </RouterLink>
          </div>
          <div class="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <RouterLink
              v-for="a in mockAttractions"
              :key="a.id"
              :to="{ name: 'attraction-detail', params: { id: a.id } }"
              class="group block"
            >
              <Card class="overflow-hidden motion-safe:transition-[transform,box-shadow] motion-safe:duration-200 motion-safe:ease-out motion-safe:group-hover:-translate-y-0.5 group-hover:shadow-md cursor-pointer">
                <!-- 이모지 배경 플레이스홀더 -->
                <div :class="['flex h-36 items-center justify-center bg-gradient-to-br text-6xl', a.bgColor]">
                  {{ a.emoji }}
                </div>
                <CardContent class="p-4">
                  <div class="flex items-start justify-between gap-2">
                    <div class="min-w-0">
                      <p class="font-semibold text-foreground truncate">{{ a.name }}</p>
                      <p class="text-xs text-muted-foreground truncate">{{ a.location }}</p>
                    </div>
                    <Badge variant="outline" class="shrink-0 text-xs">{{ a.category }}</Badge>
                  </div>
                  <div class="mt-2 flex items-center gap-1">
                    <Star class="h-3.5 w-3.5 fill-amber-400 text-amber-400" />
                    <span class="text-sm font-medium">{{ a.rating }}</span>
                  </div>
                </CardContent>
              </Card>
            </RouterLink>
          </div>
        </div>

        <!-- 숙박 -->
        <div class="w-full">
          <div class="mb-4 flex items-center justify-between">
            <h3 class="font-semibold text-foreground">인기 숙박</h3>
            <RouterLink to="/accommodations" class="text-sm text-primary hover:underline underline-offset-4">
              전체 보기 →
            </RouterLink>
          </div>
          <div class="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <RouterLink
              v-for="ac in mockAccommodations"
              :key="ac.id"
              :to="{ name: 'accommodation-detail', params: { id: ac.id } }"
              class="group block"
            >
              <Card class="overflow-hidden motion-safe:transition-[transform,box-shadow] motion-safe:duration-200 motion-safe:ease-out motion-safe:group-hover:-translate-y-0.5 group-hover:shadow-md cursor-pointer">
                <div :class="['flex h-28 items-center justify-center bg-gradient-to-br text-5xl', ac.bgColor]">
                  {{ ac.emoji }}
                </div>
                <CardContent class="p-4">
                  <div class="flex items-start justify-between gap-2">
                    <div class="min-w-0">
                      <p class="font-semibold text-foreground truncate text-sm">{{ ac.name }}</p>
                      <p class="text-xs text-muted-foreground truncate">{{ ac.location }}</p>
                    </div>
                    <Badge variant="outline" class="shrink-0 text-xs">{{ ac.type }}</Badge>
                  </div>
                  <p class="mt-2 text-sm font-semibold text-primary">
                    {{ ac.price }}원
                    <span class="text-xs font-normal text-muted-foreground">/ 1박</span>
                  </p>
                </CardContent>
              </Card>
            </RouterLink>
          </div>
        </div>
      </div>
    </section>

    <!-- ══════════════════════════════════════════════
         섹션 4 — CTA
    ══════════════════════════════════════════════ -->
    <section
      :ref="(el) => setRef(el as HTMLElement | null, 3)"
      class="snap-start snap-always relative flex h-screen flex-col items-center justify-center overflow-hidden px-6 text-center"
      aria-label="회원가입 유도"
    >
      <!-- 배경 그래디언트 -->
      <div
        class="pointer-events-none absolute inset-0 bg-gradient-to-br from-primary/10 via-background to-sky-50/50 dark:from-primary/20 dark:via-background dark:to-sky-950/20"
        aria-hidden="true"
      />
      <div
        class="pointer-events-none absolute top-0 left-0 right-0 h-px bg-gradient-to-r from-transparent via-border to-transparent"
        aria-hidden="true"
      />

      <!-- 콘텐츠 -->
      <div class="relative z-10 flex flex-col items-center gap-8 max-w-2xl">
        <div class="flex flex-col items-center gap-4">
          <span class="text-5xl" aria-hidden="true">🗺️</span>
          <h2 class="text-3xl font-extrabold tracking-tight text-foreground sm:text-4xl md:text-5xl">
            지금 바로 시작하세요
          </h2>
          <p class="text-base text-muted-foreground sm:text-lg">
            무료로 가입하고 맞춤 여행 추천을 경험하세요.<br />
            즐겨찾기, 여행 계획, 예약까지 한 곳에서.
          </p>
        </div>

        <div class="flex flex-wrap items-center justify-center gap-3">
          <template v-if="!authStore.currentUser">
            <RouterLink to="/signup">
              <Button size="lg" class="gap-2 px-10 text-base shadow-md">
                무료로 시작하기
              </Button>
            </RouterLink>
            <RouterLink to="/login">
              <Button size="lg" variant="outline" class="gap-2 px-8 text-base">
                로그인
              </Button>
            </RouterLink>
          </template>
          <template v-else>
            <RouterLink to="/attractions">
              <Button size="lg" class="gap-2 px-10 text-base shadow-md">
                <Compass class="h-5 w-5" />
                여행지 둘러보기
              </Button>
            </RouterLink>
            <RouterLink to="/me">
              <Button size="lg" variant="outline" class="gap-2 px-8 text-base">
                내 정보 보기
              </Button>
            </RouterLink>
          </template>
        </div>

        <!-- 보조 링크 -->
        <div class="flex flex-wrap items-center justify-center gap-4 text-sm text-muted-foreground">
          <RouterLink to="/attractions" class="hover:text-foreground hover:underline underline-offset-4">여행지 탐색</RouterLink>
          <span aria-hidden="true">·</span>
          <RouterLink to="/accommodations" class="hover:text-foreground hover:underline underline-offset-4">숙박 검색</RouterLink>
          <span aria-hidden="true">·</span>
          <RouterLink to="/chat" class="hover:text-foreground hover:underline underline-offset-4">채팅</RouterLink>
        </div>

        <!-- 저작권 -->
        <p class="text-xs text-muted-foreground/60">© 2025 TourDoum. SSAFY 특화 프로젝트.</p>
      </div>
    </section>
  </div>

  <!-- ─────────────────────────────────────────────
       사이드 dot 네비게이터 (데스크탑 전용)
  ───────────────────────────────────────────── -->
  <nav
    class="fixed right-6 top-1/2 z-40 hidden -translate-y-1/2 flex-col gap-3 md:flex"
    aria-label="섹션 내비게이션"
  >
    <button
      v-for="(label, idx) in ['Hero', '기능', '여행지', 'CTA']"
      :key="idx"
      type="button"
      :aria-label="`${label} 섹션으로 이동`"
      :aria-current="currentSection === idx ? 'true' : undefined"
      :class="[
        'h-2.5 w-2.5 rounded-full border border-foreground/30 transition-all duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2',
        currentSection === idx
          ? 'bg-primary border-primary scale-125'
          : 'bg-transparent hover:bg-foreground/20',
      ]"
      @click="scrollToSection(idx)"
    />
  </nav>
</template>
