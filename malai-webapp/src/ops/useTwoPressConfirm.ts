import { useCallback, useRef, useState } from "react";
import { mintIdempotencyKey } from "@/lib/mintIdempotencyKey";

export type TwoPressConfirm<A extends string> = {
  armed: A | null;
  press: (action: A) => void;
  disarm: () => void;
};

export function useTwoPressConfirm<A extends string>(
  fire: (action: A, idempotencyKey: string) => void,
): TwoPressConfirm<A> {
  const [armed, setArmed] = useState<A | null>(null);
  const keys = useRef(new Map<A, string>());

  const press = (action: A) => {
    const key = keys.current.get(action) ?? mintIdempotencyKey();
    keys.current.set(action, key);
    if (armed !== action) {
      setArmed(action);
      return;
    }
    setArmed(null);
    fire(action, key);
  };

  const disarm = useCallback(() => setArmed(null), []);

  return { armed, press, disarm };
}
