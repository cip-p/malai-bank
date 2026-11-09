import { useId } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useOutletContext } from "react-router-dom";
import { format, parseISO } from "date-fns";
import { zUpdateUserRequest } from "@/api/generated/zod.gen";
import type {
  UpdateUserRequest,
  UserResponse,
} from "@/api/generated/types.gen";
import { useUpdateUser } from "@/auth/useUpdateUser";
import { useCurrentAddress } from "@/account-holder/useCurrentAddress";
import { formatAddressLines } from "@/account-holder/formatAddressLines";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useFocusOnMount } from "@/lib/useFocusOnMount";
import { usePageTitle } from "@/lib/usePageTitle";
import { AppearanceSection } from "./ProfilePage";

type AuthenticatedOutletContext = { user: UserResponse };

export function SettingsPage() {
  usePageTitle("Settings");
  const { user } = useOutletContext<AuthenticatedOutletContext>();
  const headingRef = useFocusOnMount<HTMLHeadingElement>();

  return (
    <div className="mx-auto w-full max-w-2xl space-y-6 p-6">
      <h1
        ref={headingRef}
        tabIndex={-1}
        className="text-2xl font-semibold tracking-tight outline-none"
      >
        Account settings
      </h1>
      <ProfileCard user={user} />
      <SecurityCard />
      <AppearanceCard />
    </div>
  );
}

function SecurityCard() {
  return (
    <section
      aria-labelledby="security-card-heading"
      data-testid="settings-security-section"
      className="rounded-xl border border-border bg-card p-6 shadow-xs"
    >
      <h2 id="security-card-heading" className="text-lg font-semibold">
        Security
      </h2>
      <p
        data-testid="settings-security-coming-soon"
        className="mt-2 text-sm text-muted-foreground"
      >
        Coming soon.
      </p>
    </section>
  );
}

function AppearanceCard() {
  return (
    <section
      data-testid="settings-appearance-section"
      className="rounded-xl border border-border bg-card p-6 shadow-xs"
    >
      <AppearanceSection />
    </section>
  );
}

function ProfileCard({ user }: { user: UserResponse }) {
  const form = useForm<UpdateUserRequest>({
    resolver: zodResolver(zUpdateUserRequest),
    shouldFocusError: true,
    defaultValues: {
      firstName: user.firstName ?? "",
      lastName: user.lastName,
    },
  });
  const firstNameId = useId();
  const lastNameId = useId();
  const firstNameErrorId = `${firstNameId}-error`;
  const lastNameErrorId = `${lastNameId}-error`;
  const emailHelperId = useId();
  const dobHelperId = useId();
  const addressHelperId = useId();

  const { updateUser, isPending } = useUpdateUser();
  const { address } = useCurrentAddress();

  const onSubmit = form.handleSubmit((values) => {
    updateUser(values);
  });

  const firstNameError = form.formState.errors.firstName?.message;
  const lastNameError = form.formState.errors.lastName?.message;
  const isDirty = form.formState.isDirty;

  return (
    <section
      aria-labelledby="profile-card-heading"
      className="rounded-xl border border-border bg-card p-6 shadow-xs"
    >
      <h2 id="profile-card-heading" className="text-lg font-semibold">
        Profile
      </h2>
      <form noValidate onSubmit={onSubmit} className="mt-4 space-y-4">
        <div className="space-y-1.5">
          <Label htmlFor={firstNameId}>First name</Label>
          <Input
            id={firstNameId}
            type="text"
            autoComplete="given-name"
            aria-invalid={firstNameError ? true : undefined}
            aria-describedby={firstNameError ? firstNameErrorId : undefined}
            {...form.register("firstName")}
          />
          {firstNameError ? (
            <p id={firstNameErrorId} className="text-sm text-destructive">
              {firstNameError}
            </p>
          ) : null}
        </div>

        <div className="space-y-1.5">
          <Label htmlFor={lastNameId}>Last name</Label>
          <Input
            id={lastNameId}
            type="text"
            autoComplete="family-name"
            aria-invalid={lastNameError ? true : undefined}
            aria-describedby={lastNameError ? lastNameErrorId : undefined}
            {...form.register("lastName")}
          />
          {lastNameError ? (
            <p id={lastNameErrorId} className="text-sm text-destructive">
              {lastNameError}
            </p>
          ) : null}
        </div>

        <dl className="space-y-3 border-t border-border pt-4">
          <div className="space-y-1">
            <dt className="text-sm font-medium">Email</dt>
            <dd
              aria-describedby={emailHelperId}
              className="text-sm text-foreground"
            >
              {user.email}
            </dd>
            <p id={emailHelperId} className="text-xs text-muted-foreground">
              Contact support to change
            </p>
          </div>
          {user.dateOfBirth ? (
            <div className="space-y-1">
              <dt className="text-sm font-medium">Date of birth</dt>
              <dd
                aria-describedby={dobHelperId}
                className="text-sm text-foreground"
              >
                {format(parseISO(user.dateOfBirth), "d MMM yyyy")}
              </dd>
              <p id={dobHelperId} className="text-xs text-muted-foreground">
                Contact support to change
              </p>
            </div>
          ) : null}
          {address ? (
            <div className="space-y-1">
              <dt className="text-sm font-medium">Address</dt>
              <dd
                aria-describedby={addressHelperId}
                className="text-sm text-foreground"
              >
                {formatAddressLines(address).map((line) => (
                  <span key={line} className="block">
                    {line}
                  </span>
                ))}
              </dd>
              <p id={addressHelperId} className="text-xs text-muted-foreground">
                Contact support to change
              </p>
            </div>
          ) : null}
        </dl>

        <Button type="submit" disabled={!isDirty || isPending}>
          {isPending ? "Saving…" : "Save"}
        </Button>
      </form>
    </section>
  );
}
