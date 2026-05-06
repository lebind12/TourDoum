<template>
  <div id="app-root">
    <header>
      <h1>TourDoum</h1>
    </header>
    <main>
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
#app-root {
  font-family: sans-serif;
  max-width: 960px;
  margin: 0 auto;
  padding: 1rem;
}

header {
  border-bottom: 1px solid #e0e0e0;
  margin-bottom: 1.5rem;
}

header h1 {
  font-size: 1.8rem;
  margin: 0 0 0.5rem;
}
</style>
