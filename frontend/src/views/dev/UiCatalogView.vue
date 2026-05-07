<script setup lang="ts">
import { AlertDialog } from "@/components/ui/alert-dialog";
import { Avatar } from "@/components/ui/avatar";
/**
 * UiCatalogView — 디자인 시스템 컴포넌트 카탈로그 (dev-only)
 *
 * 목적
 * - Round 1~3 누적 산출물(reduced-motion / Select / 시맨틱 토큰 / Skeleton / Toast / hover elevation / a11y)을
 *   한 화면에 variant·state별로 박제 — 시각 회귀 감지용.
 *
 * 격리 (UI/UX 역할 제약)
 * - 도메인 store / API / composable(use<Domain>) import 0개.
 * - 모든 상태는 local ref + 정적 샘플.
 * - 라우터에서 import.meta.env.DEV 가드로만 등록 — production bundle에서 제외.
 */
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
	Card,
	CardContent,
	CardDescription,
	CardFooter,
	CardHeader,
	CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { Separator } from "@/components/ui/separator";
import { Sheet } from "@/components/ui/sheet";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs } from "@/components/ui/tabs";
import { ThemeToggle } from "@/components/ui/theme-toggle";
import { useToast } from "@/composables/useToast";
import { ref } from "vue";

// ── 로컬 상태 (모두 mock) ──────────────────────────────────────────────
const inputValue = ref("");
const inputDisabled = ref("");
const selectValue = ref("");
const tabKey = ref<"a" | "b" | "c">("a");
const sheetOpen = ref(false);
const dialogOpen = ref(false);
const toast = useToast();

const buttonVariants = [
	"default",
	"destructive",
	"outline",
	"secondary",
	"ghost",
	"link",
] as const;
const buttonSizes = ["sm", "default", "lg", "icon"] as const;
const badgeVariants = [
	"default",
	"secondary",
	"outline",
	"sky",
	"emerald",
	"destructive",
] as const;
const avatarVariants = ["primary", "sky", "emerald", "slate"] as const;
const avatarSizes = ["sm", "md", "lg"] as const;

const tabs = [
	{ key: "a", label: "Pills A" },
	{ key: "b", label: "Pills B", badge: 3 },
	{ key: "c", label: "Pills C" },
];
</script>

<template>
  <div class="container mx-auto max-w-5xl px-4 py-8 space-y-10">
    <!-- 헤더 -->
    <header class="space-y-2">
      <div class="flex items-center justify-between gap-3 flex-wrap">
        <div>
          <Badge variant="outline" class="mb-2">dev-only · /dev/ui-catalog</Badge>
          <h1 class="text-2xl font-bold tracking-tight">UI Catalog</h1>
          <p class="text-sm text-muted-foreground">
            디자인 시스템 컴포넌트의 variant / size / state를 한 화면에서 검증한다.
            production build에는 본 라우트가 포함되지 않는다.
          </p>
        </div>
        <ThemeToggle />
      </div>
    </header>

    <Separator />

    <!-- Buttons -->
    <section aria-labelledby="sec-button" class="space-y-4">
      <h2 id="sec-button" class="text-lg font-semibold">Button</h2>
      <div class="space-y-3">
        <p class="text-xs text-muted-foreground">Variants</p>
        <div class="flex flex-wrap gap-2">
          <Button v-for="v in buttonVariants" :key="v" :variant="v">
            {{ v }}
          </Button>
        </div>
        <p class="text-xs text-muted-foreground">Sizes</p>
        <div class="flex flex-wrap items-center gap-2">
          <Button v-for="s in buttonSizes" :key="s" :size="s">
            {{ s === "icon" ? "★" : s }}
          </Button>
        </div>
        <p class="text-xs text-muted-foreground">States</p>
        <div class="flex flex-wrap gap-2">
          <Button>default</Button>
          <Button disabled>disabled</Button>
          <Button variant="destructive" disabled>destructive · disabled</Button>
        </div>
      </div>
    </section>

    <Separator />

    <!-- Badges -->
    <section aria-labelledby="sec-badge" class="space-y-3">
      <h2 id="sec-badge" class="text-lg font-semibold">Badge</h2>
      <div class="flex flex-wrap gap-2">
        <Badge v-for="v in badgeVariants" :key="v" :variant="v">{{ v }}</Badge>
      </div>
      <p class="text-xs text-muted-foreground">시맨틱 토큰 (Round 2)</p>
      <div class="flex flex-wrap gap-2">
        <span class="inline-flex items-center rounded-full bg-success/10 text-success px-2.5 py-0.5 text-xs font-medium">success</span>
        <span class="inline-flex items-center rounded-full bg-warning/10 text-warning px-2.5 py-0.5 text-xs font-medium">warning</span>
        <span class="inline-flex items-center rounded-full bg-destructive/10 text-destructive px-2.5 py-0.5 text-xs font-medium">destructive</span>
      </div>
    </section>

    <Separator />

    <!-- Form -->
    <section aria-labelledby="sec-form" class="space-y-4">
      <h2 id="sec-form" class="text-lg font-semibold">Input · Select</h2>
      <div class="grid gap-4 md:grid-cols-2">
        <div class="space-y-2">
          <label for="catalog-input" class="text-sm font-medium">Input (focusable)</label>
          <Input
            id="catalog-input"
            v-model="inputValue"
            placeholder="여기에 입력"
            autocomplete="off"
          />
        </div>
        <div class="space-y-2">
          <label for="catalog-input-d" class="text-sm font-medium">Input (disabled)</label>
          <Input
            id="catalog-input-d"
            v-model="inputDisabled"
            placeholder="비활성화"
            disabled
          />
        </div>
        <div class="space-y-2 md:col-span-2">
          <label for="catalog-select" class="text-sm font-medium">Select</label>
          <Select id="catalog-select" v-model="selectValue">
            <option value="">전체</option>
            <option value="seoul">서울</option>
            <option value="busan">부산</option>
            <option value="jeju">제주</option>
          </Select>
          <p class="text-xs text-muted-foreground">선택값: {{ selectValue || "(없음)" }}</p>
        </div>
      </div>
    </section>

    <Separator />

    <!-- Tabs -->
    <section aria-labelledby="sec-tabs" class="space-y-3">
      <h2 id="sec-tabs" class="text-lg font-semibold">Tabs (ARIA tablist)</h2>
      <Tabs
        :tabs="tabs"
        :active="tabKey"
        aria-label="catalog-tabs"
        @update:active="(k) => (tabKey = k as 'a' | 'b' | 'c')"
      >
        <template #a>
          <p class="text-sm text-muted-foreground">Panel A — 화살표 키로 탭 전환 가능.</p>
        </template>
        <template #b>
          <p class="text-sm text-muted-foreground">Panel B — Tabs는 ARIA tabs 패턴, badge 지원.</p>
        </template>
        <template #c>
          <p class="text-sm text-muted-foreground">Panel C — 이전 탭은 unmount.</p>
        </template>
      </Tabs>
    </section>

    <Separator />

    <!-- Card + Skeleton -->
    <section aria-labelledby="sec-card" class="space-y-4">
      <h2 id="sec-card" class="text-lg font-semibold">Card · Skeleton</h2>
      <div class="grid gap-4 md:grid-cols-2">
        <Card class="transition-shadow hover:shadow-md">
          <CardHeader>
            <CardTitle>Card 제목</CardTitle>
            <CardDescription>설명 텍스트 — muted-foreground 톤.</CardDescription>
          </CardHeader>
          <CardContent>
            <p class="text-sm">본문 영역. hover 시 그림자 elevate (Round 3).</p>
          </CardContent>
          <CardFooter class="flex justify-end gap-2">
            <Button size="sm" variant="ghost">Cancel</Button>
            <Button size="sm">Save</Button>
          </CardFooter>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Loading state</CardTitle>
            <CardDescription>Skeleton 컴포넌트 (Round 2)</CardDescription>
          </CardHeader>
          <CardContent class="space-y-2">
            <Skeleton class="h-4 w-3/4" />
            <Skeleton class="h-4 w-1/2" />
            <Skeleton class="h-24 w-full rounded-xl" />
          </CardContent>
        </Card>
      </div>
    </section>

    <Separator />

    <!-- Avatar -->
    <section aria-labelledby="sec-avatar" class="space-y-3">
      <h2 id="sec-avatar" class="text-lg font-semibold">Avatar</h2>
      <div class="space-y-3">
        <p class="text-xs text-muted-foreground">Variants</p>
        <div class="flex items-center gap-2">
          <Avatar v-for="v in avatarVariants" :key="v" :name="v.charAt(0).toUpperCase() + v.slice(1)" :variant="v" />
        </div>
        <p class="text-xs text-muted-foreground">Sizes</p>
        <div class="flex items-center gap-2">
          <Avatar v-for="s in avatarSizes" :key="s" name="K" :size="s" variant="primary" />
        </div>
      </div>
    </section>

    <Separator />

    <!-- Overlays -->
    <section aria-labelledby="sec-overlay" class="space-y-3">
      <h2 id="sec-overlay" class="text-lg font-semibold">Overlays</h2>
      <div class="flex flex-wrap gap-2">
        <Button variant="outline" @click="sheetOpen = true">Sheet 열기</Button>
        <Button variant="outline" @click="dialogOpen = true">AlertDialog 열기</Button>
        <Button variant="outline" @click="toast.success('성공 토스트입니다')">
          Toast (success)
        </Button>
        <Button variant="outline" @click="toast.warning('주의 토스트입니다')">
          Toast (warning)
        </Button>
        <Button variant="outline" @click="toast.error('에러 토스트입니다')">
          Toast (error)
        </Button>
        <Button variant="outline" @click="toast.toast('기본 토스트입니다')">
          Toast (default)
        </Button>
      </div>

      <Sheet v-model:open="sheetOpen" side="right">
        <div class="p-4 space-y-3">
          <h3 class="text-base font-semibold">Sheet 패널</h3>
          <p class="text-sm text-muted-foreground">
            오버레이 클릭 또는 Esc 키로 닫는다 (focus trap 적용 가능 영역).
          </p>
          <div class="flex justify-end">
            <Button size="sm" @click="sheetOpen = false">닫기</Button>
          </div>
        </div>
      </Sheet>

      <AlertDialog
        v-model:open="dialogOpen"
        title="확인 대화상자"
        description="이 액션은 되돌릴 수 없습니다 (샘플)."
        variant="destructive"
        confirm-label="삭제"
        cancel-label="취소"
        @confirm="
          () => {
            dialogOpen = false;
            toast.success('확인됨 (샘플)');
          }
        "
      />
    </section>

    <Separator />

    <!-- 토큰 색 sampler -->
    <section aria-labelledby="sec-tokens" class="space-y-3">
      <h2 id="sec-tokens" class="text-lg font-semibold">Tokens (CSS vars)</h2>
      <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3 text-xs">
        <div class="rounded-md p-3 bg-primary text-primary-foreground">primary</div>
        <div class="rounded-md p-3 bg-secondary text-secondary-foreground">secondary</div>
        <div class="rounded-md p-3 bg-muted text-muted-foreground">muted</div>
        <div class="rounded-md p-3 bg-accent text-accent-foreground">accent</div>
        <div class="rounded-md p-3 bg-destructive text-destructive-foreground">destructive</div>
        <div class="rounded-md p-3 bg-success text-success-foreground">success</div>
        <div class="rounded-md p-3 bg-warning text-warning-foreground">warning</div>
        <div class="rounded-md p-3 bg-card text-card-foreground border border-border">card</div>
      </div>
    </section>

    <footer class="pt-6 text-xs text-muted-foreground">
      Round 4 (G) — 카탈로그는 dev-only. AppShell nav에 노출되지 않는다.
    </footer>
  </div>
</template>
