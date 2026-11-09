export type ParseAmountResult =
  | { ok: true; minorUnits: number }
  | {
      ok: false;
      reason:
        | "empty"
        | "invalid"
        | "too-many-decimals"
        | "below-minimum"
        | "above-max";
    };

const MAX_MINOR_UNITS = 99_999_999;
const STRICT_AMOUNT = /^\d+(\.\d{1,2})?$/;
const HAS_OVERLONG_FRACTION = /\.\d{3,}$/;

export function parseAmount(input: string): ParseAmountResult {
  const trimmed = input.trim();
  if (trimmed.length === 0) return { ok: false, reason: "empty" };

  if (HAS_OVERLONG_FRACTION.test(trimmed)) {
    return { ok: false, reason: "too-many-decimals" };
  }

  if (!STRICT_AMOUNT.test(trimmed)) {
    return { ok: false, reason: "invalid" };
  }

  const [whole, fraction = ""] = trimmed.split(".");
  const paddedFraction = (fraction + "00").slice(0, 2);
  const minorUnits = Number(whole) * 100 + Number(paddedFraction);

  if (minorUnits === 0) return { ok: false, reason: "below-minimum" };
  if (minorUnits > MAX_MINOR_UNITS) return { ok: false, reason: "above-max" };

  return { ok: true, minorUnits };
}
