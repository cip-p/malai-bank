import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, renderHook } from "@testing-library/react";

vi.mock("@/lib/mintIdempotencyKey", async () => {
  const actual = await vi.importActual<
    typeof import("@/lib/mintIdempotencyKey")
  >("@/lib/mintIdempotencyKey");
  return { mintIdempotencyKey: vi.fn(actual.mintIdempotencyKey) };
});

const mintModule = await import("@/lib/mintIdempotencyKey");
const mintMock = mintModule.mintIdempotencyKey as unknown as ReturnType<
  typeof vi.fn<() => string>
>;

const { useTwoPressConfirm } = await import("./useTwoPressConfirm");

const fire = vi.fn();

function renderConfirm() {
  return renderHook(() => useTwoPressConfirm<"dismissed" | "reported">(fire));
}

describe("useTwoPressConfirm", () => {
  beforeEach(() => {
    fire.mockReset();
    mintMock.mockClear();
  });

  it("arms on the first press and fires nothing", () => {
    const { result } = renderConfirm();

    expect(result.current.armed).toBeNull();
    act(() => result.current.press("dismissed"));

    expect(result.current.armed).toBe("dismissed");
    expect(fire).not.toHaveBeenCalled();
  });

  it("fires on the second press, carrying the key minted when it armed", () => {
    const { result } = renderConfirm();

    act(() => result.current.press("dismissed"));
    const minted = mintMock.mock.results[0]?.value as string;
    act(() => result.current.press("dismissed"));

    expect(fire).toHaveBeenCalledTimes(1);
    expect(fire).toHaveBeenCalledWith("dismissed", minted);
    expect(mintMock).toHaveBeenCalledTimes(1);
  });

  it("disarms as it fires, so a terminal close never rides a stray click", () => {
    const { result } = renderConfirm();

    act(() => result.current.press("dismissed"));
    act(() => result.current.press("dismissed"));

    expect(result.current.armed).toBeNull();
  });

  it("re-sends the same key on a retry, so a landed close never forks", () => {
    const { result } = renderConfirm();

    act(() => result.current.press("dismissed"));
    act(() => result.current.press("dismissed"));
    act(() => result.current.press("dismissed"));
    act(() => result.current.press("dismissed"));

    expect(fire).toHaveBeenCalledTimes(2);
    expect(fire.mock.calls[1][1]).toBe(fire.mock.calls[0][1]);
    expect(mintMock).toHaveBeenCalledTimes(1);
  });

  it("arms one action at a time — reaching for the other drops the first", () => {
    const { result } = renderConfirm();

    act(() => result.current.press("dismissed"));
    act(() => result.current.press("reported"));

    expect(result.current.armed).toBe("reported");
    expect(fire).not.toHaveBeenCalled();
  });

  it("gives each action its own key, since each is its own attempt", () => {
    const { result } = renderConfirm();

    act(() => result.current.press("dismissed"));
    act(() => result.current.press("dismissed"));
    act(() => result.current.press("reported"));
    act(() => result.current.press("reported"));

    expect(fire.mock.calls[1][1]).not.toBe(fire.mock.calls[0][1]);
  });

  it("disarms without firing when the officer looks elsewhere", () => {
    const { result } = renderConfirm();

    act(() => result.current.press("dismissed"));
    act(() => result.current.disarm());

    expect(result.current.armed).toBeNull();
    expect(fire).not.toHaveBeenCalled();
  });

  it("keeps the armed key across a disarm, so re-arming resumes one attempt", () => {
    const { result } = renderConfirm();

    act(() => result.current.press("dismissed"));
    act(() => result.current.disarm());
    act(() => result.current.press("dismissed"));
    act(() => result.current.press("dismissed"));

    expect(mintMock).toHaveBeenCalledTimes(1);
    expect(fire).toHaveBeenCalledWith(
      "dismissed",
      mintMock.mock.results[0]?.value,
    );
  });

  it("mints a fresh attempt on a new mount", () => {
    const first = renderConfirm();
    act(() => first.result.current.press("dismissed"));
    act(() => first.result.current.press("dismissed"));
    first.unmount();

    const second = renderConfirm();
    act(() => second.result.current.press("dismissed"));
    act(() => second.result.current.press("dismissed"));

    expect(fire.mock.calls[1][1]).not.toBe(fire.mock.calls[0][1]);
  });
});
