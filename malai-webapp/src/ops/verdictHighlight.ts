import type { AmlTypology } from "@/api/generated/types.gen";
import { TYPOLOGY_LABEL } from "@/ops/caseTags";

export type VerdictHighlight = {
  conclusion: string | null;
  detail: string | null;
};

export function verdictHighlight(
  verdict: Record<string, unknown> | undefined,
): VerdictHighlight {
  const screening = screeningHighlight(verdict);
  if (screening.conclusion !== null || screening.detail !== null) {
    return screening;
  }
  return typologyHighlight(verdict);
}

function screeningHighlight(
  verdict: Record<string, unknown> | undefined,
): VerdictHighlight {
  return {
    conclusion: stringField(verdict, "decision"),
    detail: stringField(verdict, "rule"),
  };
}

function typologyHighlight(
  verdict: Record<string, unknown> | undefined,
): VerdictHighlight {
  const typology = typologyField(verdict);
  if (typology === null) return { conclusion: null, detail: null };
  return {
    conclusion: TYPOLOGY_LABEL[typology],
    detail: riskScoreField(verdict),
  };
}

function typologyField(
  verdict: Record<string, unknown> | undefined,
): AmlTypology | null {
  const value = stringField(verdict, "typology");
  if (value === null) return null;
  return value in TYPOLOGY_LABEL ? (value as AmlTypology) : null;
}

function riskScoreField(
  verdict: Record<string, unknown> | undefined,
): string | null {
  const value = verdict?.["riskScore"];
  return typeof value === "number" ? `risk ${value}` : null;
}

function stringField(
  bag: Record<string, unknown> | undefined,
  key: string,
): string | null {
  const value = bag?.[key];
  return typeof value === "string" ? value : null;
}
