import { useEffect } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { ChevronLeft, ChevronRight, X } from "lucide-react";

export type PrototypeVariant = {
  key: string;
  name: string;
};

type Props = {
  variants: PrototypeVariant[];
  current: string;
};

function isTypingTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  return (
    target instanceof HTMLInputElement ||
    target instanceof HTMLTextAreaElement ||
    target.isContentEditable
  );
}

export function PrototypeSwitcher({ variants, current }: Props) {
  const [searchParams, setSearchParams] = useSearchParams();

  const index = Math.max(
    0,
    variants.findIndex((v) => v.key === current),
  );

  function go(offset: number) {
    const next = variants[(index + offset + variants.length) % variants.length];
    const params = new URLSearchParams(searchParams);
    params.set("variant", next.key);
    params.delete("screen");
    params.delete("account");
    setSearchParams(params, { replace: true });
  }

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (isTypingTarget(event.target)) return;
      if (event.key === "ArrowLeft") go(-1);
      if (event.key === "ArrowRight") go(1);
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  });

  if (import.meta.env.PROD) return null;

  return (
    <div className="fixed bottom-4 left-1/2 z-50 flex -translate-x-1/2 items-center gap-1 rounded-full border border-border bg-foreground px-2 py-1.5 text-background shadow-lg">
      <button
        type="button"
        aria-label="Previous variant"
        onClick={() => go(-1)}
        className="rounded-full p-1 outline-none hover:bg-background/20 focus-visible:ring-2 focus-visible:ring-background/60"
      >
        <ChevronLeft aria-hidden="true" className="size-4" />
      </button>
      <span className="min-w-44 px-1 text-center text-xs font-medium select-none">
        {variants[index].key.toUpperCase()} — {variants[index].name}
      </span>
      <button
        type="button"
        aria-label="Next variant"
        onClick={() => go(1)}
        className="rounded-full p-1 outline-none hover:bg-background/20 focus-visible:ring-2 focus-visible:ring-background/60"
      >
        <ChevronRight aria-hidden="true" className="size-4" />
      </button>
      <Link
        to="/"
        aria-label="Exit prototype"
        className="ml-1 rounded-full p-1 outline-none hover:bg-background/20 focus-visible:ring-2 focus-visible:ring-background/60"
      >
        <X aria-hidden="true" className="size-4" />
      </Link>
    </div>
  );
}
