import { useEffect, useState } from "react";
import { cn } from "@/lib/utils";

function prefersReducedMotion(): boolean {
  if (
    typeof window === "undefined" ||
    typeof window.matchMedia !== "function"
  ) {
    return true;
  }
  return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

type Props = {
  className?: string;
};

const CIRCLE_LENGTH = 188.5;
const CHECK_LENGTH = 60;

export function SuccessTick({ className }: Props) {
  const reduce = prefersReducedMotion();
  const [drawn, setDrawn] = useState(false);

  useEffect(() => {
    const id = requestAnimationFrame(() => setDrawn(true));
    return () => cancelAnimationFrame(id);
  }, []);

  const showFinal = reduce || drawn;

  return (
    <svg
      data-testid="success-tick"
      data-motion={reduce ? "reduced" : "animated"}
      aria-hidden="true"
      viewBox="0 0 64 64"
      className={cn("size-16 text-positive", className)}
    >
      <circle
        cx="32"
        cy="32"
        r="30"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeDasharray={CIRCLE_LENGTH}
        strokeDashoffset={showFinal ? 0 : CIRCLE_LENGTH}
        style={{
          transition: reduce
            ? undefined
            : "stroke-dashoffset 400ms cubic-bezier(0.4, 0, 0.2, 1)",
        }}
      />
      <path
        d="M18 33 L28 43 L46 22"
        fill="none"
        stroke="currentColor"
        strokeWidth="4"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeDasharray={CHECK_LENGTH}
        strokeDashoffset={showFinal ? 0 : CHECK_LENGTH}
        style={{
          transition: reduce
            ? undefined
            : "stroke-dashoffset 320ms cubic-bezier(0.4, 0, 0.2, 1) 320ms",
        }}
      />
    </svg>
  );
}
