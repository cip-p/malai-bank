import { client } from "./generated/client.gen";
import { createRefreshingFetch } from "./refreshMiddleware";

export class HttpError extends Error {
  readonly status: number;
  readonly body: unknown;

  constructor(status: number, body: unknown) {
    super(`HTTP ${status}`);
    this.name = "HttpError";
    this.status = status;
    this.body = body;
  }
}

export class NetworkError extends Error {
  readonly reason: unknown;

  constructor(reason: unknown) {
    super("Network request failed");
    this.name = "NetworkError";
    this.reason = reason;
  }
}

export function isHttpStatus(error: unknown, status: number): boolean {
  return error instanceof HttpError && error.status === status;
}

function isAbort(error: unknown): boolean {
  return (
    typeof error === "object" &&
    error !== null &&
    (error as { name?: unknown }).name === "AbortError"
  );
}

export function toApiError(
  error: unknown,
  response: Response | undefined,
): unknown {
  if (response) return new HttpError(response.status, error);
  if (isAbort(error)) return error;
  return new NetworkError(error);
}

let configured = false;

export function configureApiClient(): void {
  if (configured) return;
  configured = true;

  client.setConfig({
    baseUrl: import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080",
    credentials: "include",
    fetch: createRefreshingFetch((request) => fetch(request)),
  });

  client.interceptors.error.use((error, response) =>
    toApiError(error, response),
  );
}
