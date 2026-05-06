# UI 컴포넌트 라이브러리

TourDoum FE의 shadcn-vue 기반 베이스 컴포넌트 모음.
Tailwind CSS v3 + CSS 변수(Sky 팔레트) 기반.

---

## 새 shadcn 컴포넌트 추가

```bash
cd frontend
npx shadcn-vue@latest add <name>
# 예: npx shadcn-vue@latest add dialog
```

CLI가 인터랙티브하면 수동으로 `src/components/ui/<name>/` 디렉터리를 만들고
shadcn-vue 공식 사이트(https://www.shadcn-vue.com/docs/components)에서 코드를 복사한다.

---

## 컴포넌트 목록

| 컴포넌트 | 경로 | 설명 |
|---|---|---|
| Button | `ui/button` | variant(default/outline/secondary/ghost/link/destructive), size(default/sm/lg/icon) |
| Input | `ui/input` | v-model, type, placeholder, disabled 지원 |
| Label | `ui/label` | `for` prop으로 Input과 연결 |
| Card / CardHeader / CardTitle / CardDescription / CardContent / CardFooter | `ui/card` | 카드 레이아웃 조각 |
| Separator | `ui/separator` | 수평/수직 구분선, orientation prop |

### 사용 예시

```vue
<script setup lang="ts">
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
</script>

<template>
  <Card>
    <CardHeader>
      <CardTitle>예시 폼</CardTitle>
    </CardHeader>
    <CardContent class="space-y-4">
      <div class="space-y-2">
        <Label for="name">이름</Label>
        <Input id="name" v-model="name" placeholder="이름을 입력하세요" />
      </div>
      <Button type="submit">저장</Button>
    </CardContent>
  </Card>
</template>
```

---

## 디자인 토큰 변경

1. `src/index.css` 의 `:root` 및 `.dark` 블록에서 CSS 변수를 수정한다.
2. 두 곳(라이트/다크) 모두 업데이트해야 한다.
3. Primary: `--primary: 199 89% 48%` (sky-500, `#0EA5E9`)
4. Base: slate 팔레트

---

## 다크모드

- **현재 상태**: 시스템 `prefers-color-scheme`에 따라 자동 적용 (`useTheme` composable).
- **토글 UI**: 미구현. `handoff.md` 참조.
- ADR-0005(디자인 시스템 채택) 박제 검토 필요.

---

## 미해결 노트 (handoff.md 참조)

- 다크모드 토글 UI 위치 결정 필요 (헤더 우측 / 설정 페이지 / 미도입)
- 폰트: 현재 시스템 폰트. Pretendard 등 한국어 폰트 도입 시점 별도 결정.
- shadcn-vue ADR-0005로 채택 공식화 권장.
