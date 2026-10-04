import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// En desarrollo, /api se reenvía al backend de Spring Boot (puerto 8080).
// "npm run dev:demo" usa datos de prueba en memoria, sin backend.
export default defineConfig({
  // Rutas relativas: la app funciona servida en /pos/ por Spring Boot.
  base: './',
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8080' },
  },
  // Nombres fijos: al copiar a Spring Boot no se acumulan archivos viejos con otro nombre.
  build: {
    rollupOptions: {
      output: { entryFileNames: 'assets/app.js', chunkFileNames: 'assets/[name].js', assetFileNames: 'assets/[name][extname]' },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/setupTests.js',
  },
})
