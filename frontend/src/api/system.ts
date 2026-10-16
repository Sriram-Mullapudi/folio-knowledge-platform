import { apiGet } from "./client";

export interface PingResponse {
  status: string;
  app: string;
  version: string;
  time: string;
}

/** Spring Boot Actuator health document (subset we render). */
export interface HealthResponse {
  status: "UP" | "DOWN" | "OUT_OF_SERVICE" | "UNKNOWN";
  components?: Record<string, { status: string }>;
}

export function getPing(): Promise<PingResponse> {
  return apiGet<PingResponse>("/api/v1/system/ping");
}

export function getHealth(): Promise<HealthResponse> {
  return apiGet<HealthResponse>("/actuator/health");
}
