<template>
  <main class="min-h-screen bg-slate-50">
    <template v-if="!acc">
      <div class="max-w-3xl mx-auto px-4 py-20 text-center">
        <p class="text-slate-400 text-lg">숙소를 찾을 수 없습니다.</p>
        <RouterLink to="/accommodations" class="mt-4 inline-block text-sky-600 hover:underline text-sm">
          목록으로 돌아가기
        </RouterLink>
      </div>
    </template>

    <template v-else>
      <!-- 히어로 이미지 -->
      <div class="relative h-64 sm:h-80 overflow-hidden">
        <img :src="acc.imageUrl" :alt="acc.name" class="w-full h-full object-cover" />
        <div class="absolute inset-0 bg-gradient-to-t from-black/60 to-transparent" />
        <div class="absolute bottom-0 left-0 right-0 px-6 pb-6">
          <span class="text-xs bg-emerald-500 text-white rounded-full px-3 py-1 font-medium">{{ acc.type }}</span>
          <h1 class="text-3xl font-bold text-white mt-2">{{ acc.name }}</h1>
          <p class="text-white/80 text-sm mt-1">{{ acc.address }}</p>
        </div>
        <button
          type="button"
          class="absolute top-4 left-4 bg-white/20 backdrop-blur-sm text-white rounded-full p-2 hover:bg-white/30 transition-colors"
          @click="$router.back()"
        >
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
          </svg>
        </button>
      </div>

      <div class="max-w-4xl mx-auto px-4 py-8 grid grid-cols-1 lg:grid-cols-3 gap-6">
        <!-- 메인 정보 -->
        <div class="lg:col-span-2 flex flex-col gap-4">
          <!-- 평점 + 기본 정보 -->
          <div class="bg-white rounded-xl border border-slate-200 p-6">
            <div class="flex items-center gap-4 mb-4">
              <div class="flex items-center gap-1">
                <span class="text-amber-400 text-xl">★</span>
                <span class="text-xl font-bold text-slate-900">{{ acc.rating.toFixed(1) }}</span>
              </div>
              <span class="text-slate-500 text-sm">리뷰 {{ acc.reviewCount }}개</span>
              <span class="text-slate-300">|</span>
              <span class="text-slate-500 text-sm">최대 {{ acc.maxGuests }}명</span>
            </div>
            <p class="text-slate-700 leading-relaxed">{{ acc.description }}</p>
          </div>

          <!-- 편의시설 -->
          <div class="bg-white rounded-xl border border-slate-200 p-6">
            <h2 class="font-semibold text-slate-900 mb-4">편의시설</h2>
            <div class="flex flex-wrap gap-2">
              <span
                v-for="amenity in acc.amenities"
                :key="amenity"
                class="text-sm bg-slate-100 text-slate-700 rounded-full px-3 py-1"
              >
                {{ amenity }}
              </span>
            </div>
          </div>

          <!-- 체크인/아웃 -->
          <div class="bg-white rounded-xl border border-slate-200 p-6">
            <h2 class="font-semibold text-slate-900 mb-4">입실/퇴실 안내</h2>
            <div class="grid grid-cols-2 gap-4">
              <div>
                <p class="text-xs text-slate-400 mb-1">체크인</p>
                <p class="font-semibold text-slate-900">{{ acc.checkInTime }}부터</p>
              </div>
              <div>
                <p class="text-xs text-slate-400 mb-1">체크아웃</p>
                <p class="font-semibold text-slate-900">{{ acc.checkOutTime }}까지</p>
              </div>
            </div>
          </div>

          <!-- 위치 -->
          <div class="bg-white rounded-xl border border-slate-200 p-6">
            <h2 class="font-semibold text-slate-900 mb-3">위치</h2>
            <div class="h-48 bg-slate-100 rounded-lg flex items-center justify-center mb-3">
              <p class="text-slate-400 text-sm">지도 연결 예정</p>
            </div>
            <p class="text-slate-600 text-sm">{{ acc.address }}</p>
          </div>
        </div>

        <!-- 예약 패널 + 호스트 -->
        <div class="flex flex-col gap-4">
          <!-- 예약 카드 (mockup) -->
          <div class="bg-white rounded-xl border border-slate-200 p-6 shadow-sm sticky top-4">
            <p class="text-2xl font-bold text-sky-600">
              {{ acc.pricePerNight.toLocaleString() }}원
              <span class="text-slate-400 font-normal text-sm">/ 1박</span>
            </p>
            <div class="mt-4 flex flex-col gap-2">
              <label class="text-xs text-slate-500 font-medium">체크인</label>
              <input type="date" class="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-sky-500" />
              <label class="text-xs text-slate-500 font-medium">체크아웃</label>
              <input type="date" class="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-sky-500" />
            </div>
            <button
              type="button"
              class="w-full mt-4 bg-sky-500 text-white py-3 rounded-lg font-semibold hover:bg-sky-600 transition-colors"
            >
              예약 문의 (mockup)
            </button>
            <p class="text-center text-slate-400 text-xs mt-2">BE 예약 API 연결 예정</p>
          </div>

          <!-- 호스트 정보 + DM 버튼 -->
          <div class="bg-white rounded-xl border border-slate-200 p-6">
            <h2 class="font-semibold text-slate-900 mb-3">호스트</h2>
            <div class="flex items-center gap-3 mb-4">
              <div class="w-10 h-10 rounded-full bg-sky-100 flex items-center justify-center">
                <span class="text-sky-600 font-bold text-sm">{{ acc.hostName[0] }}</span>
              </div>
              <div>
                <p class="font-medium text-slate-900 text-sm">{{ acc.hostName }}</p>
                <p class="text-slate-400 text-xs">호스트</p>
              </div>
            </div>
            <RouterLink
              :to="{ name: 'dm', params: { userId: acc.hostId } }"
              class="block w-full text-center bg-slate-100 text-slate-700 py-2 rounded-lg text-sm font-medium hover:bg-slate-200 transition-colors"
            >
              💬 1:1 메시지 보내기
            </RouterLink>
          </div>
        </div>
      </div>
    </template>
  </main>
</template>

<script setup lang="ts">
import { useAccommodationsStore } from "@/stores/accommodations";
import { computed } from "vue";
import { RouterLink, useRoute } from "vue-router";

const route = useRoute();
const store = useAccommodationsStore();
const id = Number(route.params.id);
const acc = computed(() => store.getById(id));
</script>
