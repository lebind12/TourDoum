<script setup lang="ts">
import { Button } from "@/components/ui/button";
import { useTheme } from "@/composables/useTheme";
import { Moon, Sun } from "lucide-vue-next";
import { computed } from "vue";

const { mode, setTheme } = useTheme();

const isDark = computed(() => {
	if (mode.value === "dark") return true;
	if (mode.value === "light") return false;
	// auto: read current DOM state
	return document.documentElement.classList.contains("dark");
});

function toggle() {
	setTheme(isDark.value ? "light" : "dark");
}
</script>

<template>
  <Button
    variant="ghost"
    size="icon"
    aria-label="테마 전환"
    :title="isDark ? '라이트 모드로 전환' : '다크 모드로 전환'"
    @click="toggle"
  >
    <Sun v-if="isDark" class="h-4 w-4" />
    <Moon v-else class="h-4 w-4" />
  </Button>
</template>
