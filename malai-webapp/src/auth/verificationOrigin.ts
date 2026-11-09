import type { LoginRequest, RegisterRequest } from "@/api/generated/types.gen";

export type VerificationOrigin =
  | { kind: "register"; request: RegisterRequest }
  | { kind: "login"; request: LoginRequest };

export function originEmail(origin: VerificationOrigin): string {
  return origin.request.email;
}
