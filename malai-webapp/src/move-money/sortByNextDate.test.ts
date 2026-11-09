import { describe, expect, it } from "vitest";
import { sortByNextDate } from "./sortByNextDate";

type Row = { nextDate: string | null; name: string };

describe("sortByNextDate", () => {
  it("returns an empty array unchanged", () => {
    expect(sortByNextDate<Row>([])).toEqual([]);
  });

  it("returns a single-element array unchanged", () => {
    const items: Row[] = [{ nextDate: "2026-05-23", name: "Acme" }];
    expect(sortByNextDate(items)).toEqual(items);
  });

  it("sorts items with dates ascending by nextDate", () => {
    const a: Row = { nextDate: "2026-05-20", name: "A" };
    const b: Row = { nextDate: "2026-05-23", name: "B" };
    const c: Row = { nextDate: "2026-06-01", name: "C" };
    expect(sortByNextDate([c, a, b])).toEqual([a, b, c]);
  });

  it("buckets all-null entries to the end alphabetised by name", () => {
    const z: Row = { nextDate: null, name: "Zeta" };
    const a: Row = { nextDate: null, name: "Alpha" };
    const m: Row = { nextDate: null, name: "Mango" };
    expect(sortByNextDate([z, a, m])).toEqual([a, m, z]);
  });

  it("places null entries after dated entries with the null bucket alphabetised", () => {
    const dated: Row = { nextDate: "2026-05-20", name: "DatedRow" };
    const nullA: Row = { nextDate: null, name: "Zeta" };
    const nullB: Row = { nextDate: null, name: "Alpha" };
    const result = sortByNextDate([nullA, dated, nullB]);
    expect(result).toEqual([dated, nullB, nullA]);
  });

  it("breaks date ties alphabetically by name", () => {
    const a: Row = { nextDate: "2026-05-20", name: "Bravo" };
    const b: Row = { nextDate: "2026-05-20", name: "Alpha" };
    const c: Row = { nextDate: "2026-05-20", name: "Charlie" };
    expect(sortByNextDate([a, b, c])).toEqual([b, a, c]);
  });

  it("does not mutate the input array", () => {
    const input: Row[] = [
      { nextDate: "2026-05-23", name: "B" },
      { nextDate: "2026-05-20", name: "A" },
    ];
    const snapshot = [...input];
    sortByNextDate(input);
    expect(input).toEqual(snapshot);
  });
});
