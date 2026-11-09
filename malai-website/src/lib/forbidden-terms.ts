import terms from "../../docs/forbidden-terms.json" with { type: "json" };

export const FORBIDDEN_TERMS: readonly string[] = terms;

export function findForbidden(text: string): string[] {
  const hits = new Set<string>();
  for (const term of FORBIDDEN_TERMS) {
    if (text.includes(term)) hits.add(term);
  }
  return [...hits];
}
