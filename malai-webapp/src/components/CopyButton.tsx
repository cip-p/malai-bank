import { useEffect, useRef, useState } from "react";
import { Check, Copy } from "lucide-react";
import { cn } from "@/lib/utils";

const COPIED_FOR_MS = 1500;

type Props = {
  value: string;
  label: string;
  testId?: string;
  className?: string;
};

export function CopyButton({
  value,
  label,
  testId = "copy-button",
  className,
}: Props) {
  const [copied, setCopied] = useState(false);
  const resetTimer = useRef<number | null>(null);

  useEffect(() => {
    return () => {
      if (resetTimer.current !== null) window.clearTimeout(resetTimer.current);
    };
  }, []);

  const copy = () => {
    void navigator.clipboard?.writeText(value);
    setCopied(true);
    if (resetTimer.current !== null) window.clearTimeout(resetTimer.current);
    resetTimer.current = window.setTimeout(
      () => setCopied(false),
      COPIED_FOR_MS,
    );
  };

  return (
    <>
      <button
        type="button"
        data-testid={testId}
        aria-label={label}
        onClick={copy}
        className={cn(
          "-m-1.5 inline-flex shrink-0 items-center justify-center rounded-md p-2 text-muted-foreground outline-none hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring/50 motion-safe:transition-colors pointer-coarse:-m-3.75 pointer-coarse:size-11",
          className,
        )}
      >
        {copied ? (
          <Check aria-hidden="true" className="size-3.5" />
        ) : (
          <Copy aria-hidden="true" className="size-3.5" />
        )}
      </button>
      <span role="status" className="sr-only">
        {copied ? "Copied" : ""}
      </span>
    </>
  );
}
