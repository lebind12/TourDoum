<script setup lang="ts">
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { useAccommodationsStore } from "@/stores/accommodations";
import { onMounted, ref } from "vue";
import { RouterLink } from "vue-router";

const store = useAccommodationsStore();
const searchInput = ref(store.searchQuery);
const selectedType = ref(store.selectedType);
const selectedSido = ref(store.selectedSido);

onMounted(() => {
	store.fetchAccommodations();
});
</script>

<template>
  <div class="space-y-6">
    <!-- 헤더 -->
    <div>
      <h1 class="text-3xl font-bold tracking-tight">숙박 검색</h1>
      <p class="text-muted-foreground mt-1">{{ store.items.length }}개 숙박 시설이 준비되어 있습니다</p>
    </div>

    <!-- 필터 -->
    <Card>
      <CardContent class="pt-4 pb-3">
        <div class="flex flex-col sm:flex-row gap-3">
          <Input
            v-model="searchInput"
            type="text"
            placeholder="숙소 이름 또는 지역 검색..."
            class="flex-1"
            @input="store.setSearch(searchInput)"
          />
          <Select
            v-model="selectedType"
            @change="store.setType(selectedType)"
          >
            <option value="">전체 숙소 유형</option>
            <option v-for="t in store.types" :key="t" :value="t">{{ t }}</option>
          </Select>
          <Select
            v-model="selectedSido"
            @change="store.setSido(selectedSido)"
          >
            <option value="">전체 지역</option>
            <option v-for="sido in store.sidos" :key="sido" :value="sido">{{ sido }}</option>
          </Select>
        </div>
        <p class="text-muted-foreground text-xs mt-2">검색 결과: {{ store.filtered.length }}건</p>
      </CardContent>
    </Card>

    <!-- 로딩 -->
    <template v-if="store.loading">
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        <Card v-for="n in 6" :key="n" class="overflow-hidden">
          <Skeleton class="h-48 w-full rounded-t-xl rounded-b-none" />
          <CardContent class="p-4 space-y-2">
            <Skeleton class="h-4 w-3/4" />
            <Skeleton class="h-3 w-1/2" />
          </CardContent>
        </Card>
      </div>
    </template>

    <!-- 에러 -->
    <template v-else-if="store.error">
      <Card>
        <CardContent class="p-12 text-center space-y-3">
          <p class="text-destructive font-medium">숙박 목록을 불러오지 못했습니다.</p>
          <p class="text-muted-foreground text-sm">{{ store.error }}</p>
          <button
            type="button"
            class="text-sm text-primary underline underline-offset-2"
            @click="store.fetchAccommodations()"
          >
            다시 시도
          </button>
        </CardContent>
      </Card>
    </template>

    <!-- 빈 상태 -->
    <template v-else-if="store.filtered.length === 0">
      <Card>
        <CardContent class="p-12 text-center">
          <p class="text-muted-foreground">검색 결과가 없습니다.</p>
        </CardContent>
      </Card>
    </template>

    <!-- 숙박 목록 -->
    <template v-else>
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        <RouterLink
          v-for="acc in store.filtered"
          :key="acc.id"
          :to="{ name: 'accommodation-detail', params: { id: acc.id } }"
          class="block"
        >
          <Card class="overflow-hidden hover:shadow-md transition-shadow cursor-pointer h-full">
            <img
              :src="acc.imageUrl"
              :alt="acc.name"
              class="w-full h-48 object-cover"
              loading="lazy"
            />
            <CardContent class="p-4">
              <div class="flex items-start justify-between mb-1 gap-2">
                <h2 class="font-semibold text-sm truncate">{{ acc.name }}</h2>
                <Badge variant="emerald" class="shrink-0">{{ acc.type }}</Badge>
              </div>
              <p class="text-xs text-muted-foreground">{{ acc.sido }} {{ acc.gugun }}</p>
              <div class="flex items-center gap-2 mt-2">
                <span class="text-amber-500 text-sm">★ {{ acc.rating.toFixed(1) }}</span>
                <span class="text-muted-foreground text-xs">({{ acc.reviewCount }})</span>
              </div>
              <p class="text-primary font-semibold text-sm mt-2">
                {{ acc.pricePerNight.toLocaleString() }}원
                <span class="text-muted-foreground font-normal text-xs">/ 1박</span>
              </p>
            </CardContent>
          </Card>
        </RouterLink>
      </div>
    </template>
  </div>
</template>
