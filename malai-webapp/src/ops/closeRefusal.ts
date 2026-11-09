import { HttpError, isHttpStatus } from "@/api/http";

export type CloseRefusalReason =
  | "accepted-alert-stands"
  | "no-sar-ruling-in-force"
  | "already-closed"
  | "unknown";

export type CloseOutcome =
  | { kind: "refused"; reason: CloseRefusalReason }
  | { kind: "failed" };

const REASON_BY_CODE: Record<string, CloseRefusalReason> = {
  "6990": "accepted-alert-stands",
  "6989": "no-sar-ruling-in-force",
  "6988": "already-closed",
};

export function closeOutcome(error: unknown): CloseOutcome {
  if (!isHttpStatus(error, 409)) return { kind: "failed" };
  return {
    kind: "refused",
    reason: REASON_BY_CODE[domainCode(error) ?? ""] ?? "unknown",
  };
}

function domainCode(error: unknown): string | undefined {
  const body = error instanceof HttpError ? error.body : undefined;
  if (typeof body !== "object" || body === null) return undefined;
  const message = (body as { message?: unknown }).message;
  return typeof message === "string" ? message : undefined;
}
