import { useId } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { zVerifyCodeRequest } from "@/api/generated/zod.gen";
import type { VerifyCodeRequest } from "@/api/generated/types.gen";
import { useVerifyCode } from "@/auth/useVerifyCode";
import { useResendCode } from "@/auth/useResendCode";
import {
  originEmail,
  type VerificationOrigin,
} from "@/auth/verificationOrigin";
import { AuthShell } from "@/auth/AuthShell";
import { ConfirmCodeField } from "@/components/ConfirmCodeField";
import { Button } from "@/components/ui/button";

const GENERIC_VERIFY_ERROR =
  "That code didn't work. Check it and try again, or send a new one.";

const LOGIN_CODE_LENGTH = 8;
const REGISTRATION_CODE_LENGTH = 6;

export function VerifyPage() {
  const location = useLocation();
  const origin = (location.state as { origin?: VerificationOrigin } | null)
    ?.origin;

  if (!origin) {
    return <Navigate to="/login" replace />;
  }

  return <VerifyForm origin={origin} />;
}

function VerifyForm({ origin }: { origin: VerificationOrigin }) {
  const email = originEmail(origin);
  const form = useForm<VerifyCodeRequest>({
    resolver: zodResolver(zVerifyCodeRequest),
    shouldFocusError: true,
    defaultValues: { email, confirmationCode: "" },
  });
  const helperId = useId();

  const { verify, isPending, isError, reset } = useVerifyCode();
  const { resend, isPending: resendPending } = useResendCode(origin);

  const codeLength =
    origin.kind === "login" ? LOGIN_CODE_LENGTH : REGISTRATION_CODE_LENGTH;
  const code =
    useWatch({ control: form.control, name: "confirmationCode" }) ?? "";
  const canSubmit = code.length === codeLength && !isPending && !resendPending;

  const onSubmit = form.handleSubmit((values) => {
    verify({ email, confirmationCode: values.confirmationCode });
  });

  const onResend = () => {
    reset();
    form.resetField("confirmationCode");
    resend();
  };

  return (
    <AuthShell
      title="Enter your confirmation code"
      description={
        <>
          We sent a confirmation code to <strong>{email}</strong>. Enter it
          below to continue.
        </>
      }
    >
      <form noValidate onSubmit={onSubmit} className="space-y-5">
        {isError ? (
          <div
            role="alert"
            className="rounded-lg border border-destructive bg-destructive/10 p-3 text-sm text-destructive"
          >
            {GENERIC_VERIFY_ERROR}
          </div>
        ) : null}

        <div className="space-y-1.5">
          <ConfirmCodeField
            name="confirmationCode"
            label="Confirmation code"
            control={form.control}
            length={codeLength}
          />
          <p id={helperId} className="text-sm text-muted-foreground">
            Didn&apos;t get it?{" "}
            <button
              type="button"
              onClick={onResend}
              disabled={resendPending}
              className="font-medium text-primary hover:underline focus-visible:underline disabled:opacity-60 pointer-coarse:py-3.75"
            >
              {resendPending ? "Sending…" : "Send a new code"}
            </button>
          </p>
        </div>

        <Button
          type="submit"
          disabled={!canSubmit}
          className="h-11 w-full text-base"
        >
          {isPending ? "Verifying…" : "Verify"}
        </Button>
      </form>
    </AuthShell>
  );
}
