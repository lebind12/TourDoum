<script setup lang="ts">
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { useSearchStore } from "@/stores/search";
import { Search, X } from "lucide-vue-next";
import { onMounted, ref, watch } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";

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
    <div v-if="!searchStore.query && searchStore.recentQueries.length > 0" class="space-y-2">
      <div class="flex items-center justify-between">
        <p class="text-sm font-medium text-muted-foreground">최근 검색어</p>
        <button
          type="button"
          class="text-xs text-muted-foreground hover:text-foreground underline-offset-2 hover:underline"
          @click="searchStore.clearRecent()"
        >
          전체 삭제
        </button>
      </div>
      <div class="flex flex-wrap gap-2">
        <span
          v-for="q in searchStore.recentQueries"
          :key="q"
          class="flex items-center gap-1 rounded-full border bg-muted/50 px-3 py-1 text-sm cursor-pointer hover:bg-muted transition-colors"
          @click="handleRecent(q)"
        >
          {{ q }}
          <button
            type="button"
            class="ml-1 text-muted-foreground/60 hover:text-muted-foreground"
            :aria-label="`${q} 삭제`"
            @click.stop="searchStore.removeRecent(q)"
          >
            <X :size="12" />
          </button>
        </span>
      </div>
    </div>

    <!-- 검색 결과 -->
    <template v-if="searchStore.query">
      <!-- 결과 없음 -->
      <div
        v-if="searchStore.totalCount() === 0"
        class="flex flex-col items-center py-16 text-center gap-3"
        role="status"
      >
        <span class="text-4xl" aria-hidden="true">🔍</span>
        <p class="font-medium">
          "<span class="text-primary">{{ searchStore.query }}</span>"에 대한 결과가 없습니다.
        </p>
        <p class="text-sm text-muted-foreground">다른 키워드로 검색해보세요.</p>
      </div>

      <!-- 여행지 섹션 -->
      <section v-if="searchStore.results.attractions.length > 0" aria-labelledby="search-attractions">
        <div class="flex items-center justify-between mb-3">
          <h2 id="search-attractions" class="font-semibold">여행지</h2>
          <RouterLink
            :to="{ name: 'attractions' }"
            class="text-sm text-primary hover:underline underline-offset-2"
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
            <Card class="hover:shadow-md transition-shadow">
              <CardContent class="p-3 flex gap-3">
                <img
                  :src="item.imageUrl"
                  :alt="item.name"
                  class="w-16 h-16 object-cover rounded-lg shrink-0"
                  loading="lazy"
                />
                <div class="min-w-0">
                  <p class="font-medium text-sm truncate">{{ item.name }}</p>
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
          <h2 id="search-accommodations" class="font-semibold">숙박</h2>
          <RouterLink
            :to="{ name: 'accommodations' }"
            class="text-sm text-primary hover:underline underline-offset-2"
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
            <Card class="hover:shadow-md transition-shadow">
              <CardContent class="p-3 flex gap-3">
                <img
                  :src="item.imageUrl"
                  :alt="item.name"
                  class="w-16 h-16 object-cover rounded-lg shrink-0"
                  loading="lazy"
                />
                <div class="min-w-0">
                  <p class="font-medium text-sm truncate">{{ item.name }}</p>
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
          <h2 id="search-plans" class="font-semibold">내 여행 계획</h2>
          <RouterLink
            :to="{ name: 'plans' }"
            class="text-sm text-primary hover:underline underline-offset-2"
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
                <span class="text-xl" aria-hidden="true">🗓️</span>
                <div>
                  <p class="font-medium text-sm">{{ item.title }}</p>
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
          <h2 id="search-reviews" class="font-semibold">후기</h2>
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
                  <span class="text-sm font-medium">{{ item.authorNickname }}</span>
                  <span class="text-xs text-amber-500">★ {{ item.rating }}</span>
                  <Badge variant="outline" class="text-xs">{{ item.targetType === 'attraction' ? '여행지' : '숙박' }}</Badge>
                </div>
                <p class="text-sm text-muted-foreground line-clamp-2">{{ item.comment }}</p>
              </CardContent>
            </Card>
          </RouterLink>
        </div>
      </section>
    </template>
  </div>
</template>
