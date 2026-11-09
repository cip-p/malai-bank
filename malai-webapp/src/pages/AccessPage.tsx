import { Link } from "react-router-dom";
import { useFocusOnMount } from "@/lib/useFocusOnMount";
import { offeredSurfaces, type Role } from "@/surfaces/surfaceOffering";

type Props = {
  roles: readonly Role[];
};

export function AccessPage({ roles }: Props) {
  const headingRef = useFocusOnMount<HTMLHeadingElement>();
  const surfaces = offeredSurfaces(roles);

  return (
    <main className="flex min-h-dvh flex-col items-center justify-center px-6 text-center">
      <h1
        ref={headingRef}
        tabIndex={-1}
        data-testid="access-heading"
        className="text-2xl font-semibold tracking-tight outline-none"
      >
        This area isn&rsquo;t part of your access
      </h1>
      <p className="mt-2 max-w-sm text-sm text-muted-foreground">
        Your sign-in works here, but this area isn&rsquo;t included in it.
      </p>
      {surfaces.length > 0 ? (
        <nav aria-label="Your areas" className="mt-6">
          <ul className="flex flex-col items-center gap-2">
            {surfaces.map((surface) => (
              <li key={surface.id}>
                <Link
                  to={surface.path}
                  data-testid={`access-surface-link-${surface.id}`}
                  className="text-sm text-muted-foreground hover:underline focus-visible:underline"
                >
                  {surface.label} →
                </Link>
              </li>
            ))}
          </ul>
        </nav>
      ) : (
        <p
          data-testid="access-no-surfaces"
          className="mt-6 max-w-sm text-sm text-muted-foreground"
        >
          Your sign-in doesn&rsquo;t include any areas yet. Ask whoever manages
          your access to add one.
        </p>
      )}
    </main>
  );
}
