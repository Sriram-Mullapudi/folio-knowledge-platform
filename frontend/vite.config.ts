import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// The SPA always calls the API with relative paths (/api, /actuator). In dev,
// Vite proxies those to the Spring Boot backend so there are no CORS concerns
// and the same client code works unchanged behind nginx in Docker.
const BACKEND = process.env.VITE_PROXY_TARGET ?? "http://localhost:8080";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": { target: BACKEND, changeOrigin: true },
      "/actuator": { target: BACKEND, changeOrigin: true },
    },
  },
  build: {
    outDir: "dist",
    sourcemap: true,
  },
});
