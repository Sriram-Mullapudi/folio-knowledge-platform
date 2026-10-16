import { apiGet, apiPost } from "./client";

export interface AuthUser {
  id: string;
  email: string;
  role: "ADMIN" | "USER";
  enabled: boolean;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
  user: AuthUser;
}

export function login(email: string, password: string): Promise<AuthResponse> {
  return apiPost<AuthResponse>("/api/v1/auth/login", { email, password });
}

export function register(email: string, password: string): Promise<AuthResponse> {
  return apiPost<AuthResponse>("/api/v1/auth/register", { email, password });
}

export function refresh(refreshToken: string): Promise<AuthResponse> {
  return apiPost<AuthResponse>("/api/v1/auth/refresh", { refreshToken });
}

export function logout(refreshToken: string): Promise<void> {
  return apiPost<void>("/api/v1/auth/logout", { refreshToken });
}

export function me(): Promise<AuthUser> {
  return apiGet<AuthUser>("/api/v1/auth/me");
}
