import { Snowflake } from "lucide-react";
import type { CardStatus, CardVariant } from "@/api/generated/types.gen";
import { cn } from "@/lib/utils";
import { formatPanGroups } from "./formatPan";

export const SCHEME_MARK = "VELA";

type Props = {
  variant: CardVariant;
  status: CardStatus;
  pan: string;
  expiry: string;
  cvv?: string;
  cardholderName?: string;
  className?: string;
};

const VARIANT_LABELS: Record<CardVariant, string> = {
  DEBIT: "Debit",
  CREDIT: "Credit",
};

export function CardArt({
  variant,
  status,
  pan,
  expiry,
  cvv,
  cardholderName,
  className,
}: Props) {
  return (
    <div
      data-testid="card-art"
      data-variant={variant}
      data-status={status}
      className={cn(
        "relative aspect-[1.586/1] w-full max-w-sm overflow-hidden rounded-2xl bg-gradient-to-br from-primary via-primary to-[color-mix(in_oklab,var(--primary)_82%,var(--foreground))] p-5 text-primary-foreground shadow-md",
        status === "CANCELLED" && "opacity-60 saturate-50",
        className,
      )}
    >
      <div className="flex h-full flex-col justify-between">
        <div className="flex items-start justify-between">
          <span className="text-sm font-semibold tracking-tight">malai</span>
          <span
            data-testid="card-art-variant"
            className="rounded-md bg-primary-foreground/15 px-2 py-0.5 text-xs font-medium"
          >
            {VARIANT_LABELS[variant]}
          </span>
        </div>

        <p
          data-testid="card-art-pan"
          aria-label="Card number"
          className="flex justify-between text-lg font-medium tracking-widest tabular-nums"
        >
          {formatPanGroups(pan).map((group, index) => (
            <span key={index}>{group}</span>
          ))}
        </p>

        <div className="flex items-end justify-between gap-3 text-xs">
          <span
            data-testid="card-art-name"
            className="min-w-0 truncate font-medium uppercase tracking-wider"
          >
            {cardholderName ?? " "}
          </span>
          <span className="flex shrink-0 items-center gap-3">
            <span data-testid="card-art-expiry" className="tabular-nums">
              <span className="mr-1 text-2xs uppercase">Exp</span>
              {expiry}
            </span>
            {cvv ? (
              <span data-testid="card-art-cvv" className="tabular-nums">
                <span className="mr-1 text-2xs uppercase">CVV</span>
                {cvv}
              </span>
            ) : null}
            <span className="text-sm font-bold italic tracking-tight">
              {SCHEME_MARK}
            </span>
          </span>
        </div>
      </div>

      {status === "FROZEN" ? (
        <div
          data-testid="card-art-frozen-overlay"
          className="absolute inset-0 flex items-center justify-center gap-2 bg-primary/60 backdrop-blur-[2px] motion-safe:animate-in motion-safe:fade-in-0 motion-safe:duration-300 motion-safe:ease-arrive"
        >
          <Snowflake aria-hidden="true" className="size-5" />
          <span className="text-sm font-semibold">Frozen</span>
        </div>
      ) : null}
    </div>
  );
}
