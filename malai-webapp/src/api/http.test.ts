import { describe, expect, it } from "vitest";
import { HttpError, NetworkError, isHttpStatus, toApiError } from "./http";

const abortError = () =>
  Object.assign(new Error("The user aborted a request."), {
    name: "AbortError",
  });

describe("toApiError", () => {
  it("wraps a failure that carries a response as an HttpError", () => {
    const body = { message: "nope" };

    const error = toApiError(body, { status: 409 } as Response);

    expect(error).toBeInstanceOf(HttpError);
    expect((error as HttpError).status).toBe(409);
    expect((error as HttpError).body).toBe(body);
  });

  it("wraps a failure with no response as a NetworkError, so an unreachable backend is distinguishable from a rejecting one", () => {
    const reason = new TypeError("Failed to fetch");

    const error = toApiError(reason, undefined);

    expect(error).toBeInstanceOf(NetworkError);
    expect((error as NetworkError).reason).toBe(reason);
  });

  it("passes an abort through untouched — a cancelled request is not evidence the backend is gone", () => {
    const aborted = abortError();

    expect(toApiError(aborted, undefined)).toBe(aborted);
  });
});

describe("isHttpStatus", () => {
  it("matches an HttpError of that status", () => {
    expect(isHttpStatus(new HttpError(401, null), 401)).toBe(true);
  });

  it("does not match a NetworkError", () => {
    expect(isHttpStatus(new NetworkError(new TypeError("x")), 401)).toBe(false);
  });
});
