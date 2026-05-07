<script setup lang="ts">
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Search, X } from "lucide-vue-next";
import { ref } from "vue";
import { useRouter } from "vue-router";

const router = useRouter();
const open = ref(false);
const query = ref("");

function openSearch() {
	open.value = true;
	// 다음 tick에 input 포커스
	setTimeout(() => {
		const el = document.getElementById("header-search-input");
		el?.focus();
	}, 50);
}

function close() {
	open.value = false;
	query.value = "";
}

function submit() {
	const q = query.value.trim();
	if (!q) return;
	close();
	router.push({ name: "search", query: { q } });
}
</script>

<template>
  <!-- 검색 아이콘 버튼 (항상 표시) -->
  <template v-if="!open">
    <Button
      variant="ghost"
      size="icon"
      aria-label="검색"
      data-testid="header-search-open"
      @click="openSearch"
    >
      <Search class="h-4 w-4" />
    </Button>
  </template>

  <!-- 검색 바 (확장 시) -->
  <form v-else class="flex items-center gap-1" @submit.prevent="submit">
    <div class="relative">
      <Search class="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
      <Input
        id="header-search-input"
        v-model="query"
        type="search"
        placeholder="검색..."
        class="h-8 pl-8 w-40 sm:w-56 text-sm"
        @keydown.escape="close"
      />
    </div>
    <Button type="submit" size="sm" class="h-8 px-2.5 text-xs">검색</Button>
    <Button
      type="button"
      variant="ghost"
      size="icon"
      class="h-8 w-8"
      aria-label="검색 닫기"
      @click="close"
    >
      <X class="h-4 w-4" />
    </Button>
  </form>
</template>
