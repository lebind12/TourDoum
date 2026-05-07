<script setup lang="ts">
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Command, Search, X } from "lucide-vue-next";
import { nextTick, onUnmounted, ref } from "vue";
import { useRouter } from "vue-router";

const router = useRouter();
const open = ref(false);
const query = ref("");

async function openSearch() {
	open.value = true;
	await nextTick();
	document.getElementById("header-search-input")?.focus();
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

// ── Cmd+K / Ctrl+K 전역 단축키 ──────────────────────────────────────────
function onGlobalKeydown(e: KeyboardEvent) {
	if ((e.metaKey || e.ctrlKey) && e.key === "k") {
		e.preventDefault();
		if (open.value) {
			close();
		} else {
			openSearch();
		}
	}
}

if (typeof window !== "undefined") {
	window.addEventListener("keydown", onGlobalKeydown);
	onUnmounted(() => window.removeEventListener("keydown", onGlobalKeydown));
}
</script>

<template>
  <!-- 검색 아이콘 버튼 (닫힘 상태) -->
  <template v-if="!open">
    <Button
      variant="ghost"
      size="icon"
      aria-label="검색 (Cmd+K)"
      data-testid="header-search-open"
      class="relative group"
      @click="openSearch"
    >
      <Search class="h-4 w-4" />
      <!-- Cmd+K 힌트 툴팁 (데스크톱만) -->
      <span
        class="pointer-events-none absolute -bottom-8 left-1/2 -translate-x-1/2 hidden group-hover:flex
               items-center gap-0.5 whitespace-nowrap rounded bg-popover px-1.5 py-0.5
               text-[10px] text-muted-foreground border shadow-sm sm:flex"
        aria-hidden="true"
      >
        <Command :size="9" />K
      </span>
    </Button>
  </template>

  <!-- 검색 바 (확장 상태) — width transition -->
  <Transition
    enter-active-class="transition-all duration-200 ease-out"
    leave-active-class="transition-all duration-150 ease-in"
    enter-from-class="opacity-0 scale-x-95 origin-right"
    leave-to-class="opacity-0 scale-x-95 origin-right"
  >
    <form v-if="open" class="flex items-center gap-1" @submit.prevent="submit">
      <div class="relative">
        <Search
          class="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground pointer-events-none"
          aria-hidden="true"
        />
        <Input
          id="header-search-input"
          v-model="query"
          type="search"
          placeholder="검색..."
          class="h-8 pl-8 w-48 sm:w-64 md:w-72 text-sm transition-[width] duration-200 focus:w-56 sm:focus:w-80"
          autocomplete="off"
          aria-label="검색어 입력"
          @keydown.escape="close"
        />
      </div>
      <Button type="submit" size="sm" class="h-8 px-2.5 text-xs shrink-0">검색</Button>
      <Button
        type="button"
        variant="ghost"
        size="icon"
        class="h-8 w-8 shrink-0"
        aria-label="검색 닫기"
        @click="close"
      >
        <X class="h-4 w-4" />
      </Button>
    </form>
  </Transition>
</template>
