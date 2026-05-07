<script setup lang="ts">
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { useSearchStore } from "@/stores/search";
import { Clock, Search, X } from "lucide-vue-next";
import { onMounted, ref, watch } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";

// ── 검색어 highlight 헬퍼 ─────────────────────────────────────────────────
function escapeHtml(text: string): string {
	return text
		.replace(/&/g, "&amp;")
		.replace(/</g, "&lt;")
		.replace(/>/g, "&gt;")
		.replace(/"/g, "&quot;");
}

function highlight(text: string, query: string): string {
	if (!query.trim()) return escapeHtml(text);
	const safeQ = query.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
	const re = new RegExp(`(${safeQ})`, "gi");
	return escapeHtml(text).replace(
		re,
		'<mark class="bg-amber-200 dark:bg-amber-800/60 text-foreground rounded-[2px] px-0.5">$1</mark>',
	);
}

const route = useRoute();
const router = useRouter();
const searchStore = useSearchStore();

const inputValue = ref("");

onMounted(() => {
	const q = String(route.query.q ?? "");
	if (q) {
		inputValue.value = q;
		searchStore.search(q);
	}
});

// URL 쿼리 변경 시 재검색
watch(
	() => route.query.q,
	(q) => {
		const str = String(q ?? "");
		inputValue.value = str;
		if (str) searchStore.search(str);
	},
);

function handleSearch() {
	const q = inputValue.value.trim();
	if (!q) return;
	router.replace({ name: "search", query: { q } });
	searchStore.search(q);
}

function handleRecent(q: string) {
	inputValue.value = q;
	router.replace({ name: "search", query: { q } });
	searchStore.search(q);
}

function formatDate(iso: string) {
	return new Date(iso).toLocaleDateString("ko-KR", {
		month: "short",
		day: "numeric",
	});
}
</script>

<template>
  <div class="space-y-6 max-w-3xl mx-auto">
    <!-- 검색 입력 -->
    <form class="flex gap-2" @submit.prevent="handleSearch">
      <div class="relative flex-1">
        <Search class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
        <Input
          v-model="inputValue"
          type="search"
          placeholder="여행지, 숙박, 계획, 후기 검색..."
          class="pl-9"
          autofocus
        />
      </div>
      <Button type="submit">검색</Button>
    </form>

    <!-- 최근 검색어 (결과 없을 때) -->
    <div v-if="!searchStore.query && searchStore.recentQueries.length > 0" class="space-y-3">
      <div class="flex items-center justify-between">
        <p class="text-sm font-semibold flex items-center gap-1.5">
          <Clock :size="14" class="text-muted-foreground" aria-hidden="true" />
          최근 검색어
        </p>
        <button
          type="button"
          class="text-xs text-muted-foreground hover:text-foreground underline-offset-2 hover:underline transition-colors
                 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring rounded"
          @click="searchStore.clearRecent()"
        >
          전체 삭제
        </button>
      </div>
      <div class="flex flex-wrap gap-2" role="list" aria-label="최근 검색어 목록">
        <div
          v-for="q in searchStore.recentQueries"
          :key="q"
          role="listitem"
          class="group flex items-center gap-1 rounded-full border bg-muted/50 px-3 py-1.5 text-sm
                 cursor-pointer hover:bg-muted hover:border-border/80 transition-colors
                 focus-within:ring-2 focus-within:ring-ring"
        >
          <button
            type="button"
            class="focus-visible:outline-none"
            :aria-label="`${q} 재검색`"
            @click="handleRecent(q)"
          >
            {{ q }}
          </button>
          <button
            type="button"
            class="ml-0.5 text-muted-foreground/50 hover:text-muted-foreground transition-colors rounded-full
                   opacity-0 group-hover:opacity-100 focus-visible:opacity-100
                   focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
            :aria-label="`${q} 삭제`"
            @click.stop="searchStore.removeRecent(q)"
          >
            <X :size="12" />
          </button>
        </div>
      </div>
    </div>

    <!-- 검색 결과 -->
    <template v-if="searchStore.query">
      <!-- 결과 없음 -->
      <div
        v-if="searchStore.totalCount() === 0"
        class="flex flex-col items-center py-20 text-center gap-4"
        role="status"
        aria-label="검색 결과 없음"
      >
        <div class="flex h-16 w-16 items-center justify-center rounded-full bg-muted text-3xl" aria-hidden="true">🔍</div>
        <div class="space-y-1">
          <p class="font-semibold">
            "<span class="text-primary">{{ searchStore.query }}</span>"에 대한 결과가 없습니다.
          </p>
          <p class="text-sm text-muted-foreground">오타를 확인하거나 다른 키워드로 검색해보세요.</p>
        </div>
        <button
          type="button"
          class="text-sm text-primary hover:underline underline-offset-2"
          @click="searchStore.clearRecent(); inputValue = ''"
        >
          검색 초기화
        </button>
      </div>

      <!-- 결과 총계 헤더 -->
      <p v-else class="text-sm text-muted-foreground">
        "<span class="font-medium text-foreground">{{ searchStore.query }}</span>" 검색 결과
        <span class="font-semibold text-foreground">{{ searchStore.totalCount() }}</span>건
      </p>

      <!-- 여행지 섹션 -->
      <section v-if="searchStore.results.attractions.length > 0" aria-labelledby="search-attractions">
        <div class="flex items-center justify-between mb-3">
          <h2 id="search-attractions" class="font-semibold flex items-center gap-1.5">
            <span aria-hidden="true">📍</span>
            여행지
            <Badge variant="secondary" class="text-xs font-normal">{{ searchStore.results.attractions.length }}</Badge>
          </h2>
          <RouterLink
            :to="{ name: 'attractions' }"
            class="text-sm text-primary hover:underline underline-offset-2 transition-colors"
          >
            더보기 →
          </RouterLink>
        </div>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <RouterLink
            v-for="item in searchStore.results.attractions"
            :key="item.id"
            :to="{ name: 'attraction-detail', params: { id: item.id } }"
          >
            <Card class="hover:shadow-md transition-shadow group">
              <CardContent class="p-3 flex gap-3">
                <img
                  :src="item.imageUrl"
                  :alt="item.name"
                  class="w-16 h-16 object-cover rounded-lg shrink-0 group-hover:opacity-90 transition-opacity"
                  loading="lazy"
                />
                <div class="min-w-0">
                  <!-- eslint-disable-next-line vue/no-v-html -->
                  <p class="font-medium text-sm truncate" v-html="highlight(item.name, searchStore.query)" />
                  <p class="text-xs text-muted-foreground">{{ item.sido }}</p>
                  <Badge variant="secondary" class="mt-1 text-xs">{{ item.category }}</Badge>
                </div>
              </CardContent>
            </Card>
          </RouterLink>
        </div>
      </section>

      <!-- 숙박 섹션 -->
      <section v-if="searchStore.results.accommodations.length > 0" aria-labelledby="search-accommodations">
        <div class="flex items-center justify-between mb-3">
          <h2 id="search-accommodations" class="font-semibold flex items-center gap-1.5">
            <span aria-hidden="true">🏨</span>
            숙박
            <Badge variant="secondary" class="text-xs font-normal">{{ searchStore.results.accommodations.length }}</Badge>
          </h2>
          <RouterLink
            :to="{ name: 'accommodations' }"
            class="text-sm text-primary hover:underline underline-offset-2 transition-colors"
          >
            더보기 →
          </RouterLink>
        </div>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <RouterLink
            v-for="item in searchStore.results.accommodations"
            :key="item.id"
            :to="{ name: 'accommodation-detail', params: { id: item.id } }"
          >
            <Card class="hover:shadow-md transition-shadow group">
              <CardContent class="p-3 flex gap-3">
                <img
                  :src="item.imageUrl"
                  :alt="item.name"
                  class="w-16 h-16 object-cover rounded-lg shrink-0 group-hover:opacity-90 transition-opacity"
                  loading="lazy"
                />
                <div class="min-w-0">
                  <!-- eslint-disable-next-line vue/no-v-html -->
                  <p class="font-medium text-sm truncate" v-html="highlight(item.name, searchStore.query)" />
                  <p class="text-xs text-muted-foreground">{{ item.accommodationType }}</p>
                  <p class="text-xs font-medium text-primary mt-1">
                    {{ item.pricePerNight.toLocaleString() }}원 / 1박
                  </p>
                </div>
              </CardContent>
            </Card>
          </RouterLink>
        </div>
      </section>

      <!-- 내 계획 섹션 -->
      <section v-if="searchStore.results.plans.length > 0" aria-labelledby="search-plans">
        <div class="flex items-center justify-between mb-3">
          <h2 id="search-plans" class="font-semibold flex items-center gap-1.5">
            <span aria-hidden="true">🗓️</span>
            내 여행 계획
            <Badge variant="secondary" class="text-xs font-normal">{{ searchStore.results.plans.length }}</Badge>
          </h2>
          <RouterLink
            :to="{ name: 'plans' }"
            class="text-sm text-primary hover:underline underline-offset-2 transition-colors"
          >
            더보기 →
          </RouterLink>
        </div>
        <div class="space-y-2">
          <RouterLink
            v-for="item in searchStore.results.plans"
            :key="item.id"
            :to="{ name: 'plan-detail', params: { id: item.id } }"
          >
            <Card class="hover:shadow-md transition-shadow">
              <CardContent class="p-3 flex items-center gap-3">
                <span class="text-xl shrink-0" aria-hidden="true">🗓️</span>
                <div class="min-w-0">
                  <!-- eslint-disable-next-line vue/no-v-html -->
                  <p class="font-medium text-sm truncate" v-html="highlight(item.title, searchStore.query)" />
                  <p class="text-xs text-muted-foreground">
                    {{ formatDate(item.startDate) }} ~ {{ formatDate(item.endDate) }}
                  </p>
                </div>
              </CardContent>
            </Card>
          </RouterLink>
        </div>
      </section>

      <!-- 후기 섹션 -->
      <section v-if="searchStore.results.reviews.length > 0" aria-labelledby="search-reviews">
        <div class="flex items-center justify-between mb-3">
          <h2 id="search-reviews" class="font-semibold flex items-center gap-1.5">
            <span aria-hidden="true">⭐</span>
            후기
            <Badge variant="secondary" class="text-xs font-normal">{{ searchStore.results.reviews.length }}</Badge>
          </h2>
        </div>
        <div class="space-y-2">
          <RouterLink
            v-for="item in searchStore.results.reviews"
            :key="item.id"
            :to="item.targetType === 'attraction'
              ? { name: 'attraction-detail', params: { id: item.targetId } }
              : { name: 'accommodation-detail', params: { id: item.targetId } }"
          >
            <Card class="hover:shadow-md transition-shadow">
              <CardContent class="p-3">
                <div class="flex items-center gap-2 mb-1">
                  <!-- 이니셜 아바타 -->
                  <span
                    class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-primary/10
                           text-xs font-semibold text-primary select-none"
                    aria-hidden="true"
                  >
                    {{ item.authorNickname.charAt(0).toUpperCase() }}
                  </span>
                  <span class="text-sm font-medium">{{ item.authorNickname }}</span>
                  <span class="text-xs text-amber-500 font-medium">★ {{ item.rating }}</span>
                  <Badge variant="outline" class="text-xs ml-auto shrink-0">
                    {{ item.targetType === "attraction" ? "여행지" : "숙박" }}
                  </Badge>
                </div>
                <!-- eslint-disable-next-line vue/no-v-html -->
                <p class="text-sm text-muted-foreground line-clamp-2" v-html="highlight(item.comment, searchStore.query)" />
              </CardContent>
            </Card>
          </RouterLink>
        </div>
      </section>
    </template>

    <!-- 검색 중 스켈레톤 (store에 loading state가 없으므로 쿼리 있고 결과 없는 짧은 순간용) -->
    <template v-if="!searchStore.query && searchStore.recentQueries.length === 0">
      <div class="space-y-3 pt-2" aria-hidden="true">
        <Skeleton class="h-4 w-24 rounded" />
        <div class="flex gap-2">
          <Skeleton class="h-8 w-20 rounded-full" />
          <Skeleton class="h-8 w-16 rounded-full" />
          <Skeleton class="h-8 w-24 rounded-full" />
        </div>
      </div>
    </template>
  </div>
</template>
