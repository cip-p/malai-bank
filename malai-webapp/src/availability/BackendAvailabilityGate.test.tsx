import { afterEach, describe, expect, it } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { client } from "@/api/generated/client.gen";
import { ThemeProvider } from "@/theming/useTheme";
import { BackendAvailabilityGate } from "./BackendAvailabilityGate";

const BASE = "http://localhost:8080";

type Fetch = (request: Request) => Promise<Response>;

function withFetch(fetchImpl: Fetch) {
  client.setConfig({ baseUrl: BASE, fetch: fetchImpl as typeof fetch });
}

const reachable: Fetch = async () => new Response(null, { status: 200 });
const unreachable: Fetch = async () => {
  throw new TypeError("Failed to fetch");
};
const hanging: Fetch = () => new Promise<Response>(() => {});

function renderGate() {
  const queryClient = new QueryClient();
  render(
    <ThemeProvider>
      <QueryClientProvider client={queryClient}>
        <BackendAvailabilityGate>
          <div data-testid="app">the bank</div>
        </BackendAvailabilityGate>
      </QueryClientProvider>
    </ThemeProvider>,
  );
  return queryClient;
}

afterEach(() => {
  client.setConfig({ baseUrl: BASE, fetch: undefined });
});

describe("BackendAvailabilityGate", () => {
  it("shows the app while the first probe is still in flight, so a healthy load never flashes the paused screen", () => {
    withFetch(hanging);
    renderGate();

    expect(screen.getByTestId("app")).toBeInTheDocument();
    expect(
      screen.queryByTestId("service-paused-heading"),
    ).not.toBeInTheDocument();
  });

  it("keeps showing the app when the backend answers", async () => {
    withFetch(reachable);
    renderGate();

    expect(await screen.findByTestId("app")).toBeInTheDocument();
    expect(
      screen.queryByTestId("service-paused-heading"),
    ).not.toBeInTheDocument();
  });

  it("replaces the whole app with the paused screen once the backend cannot be reached", async () => {
    withFetch(unreachable);
    renderGate();

    expect(
      await screen.findByTestId("service-paused-heading", undefined, {
        timeout: 4000,
      }),
    ).toBeInTheDocument();
    expect(screen.queryByTestId("app")).not.toBeInTheDocument();
  });

  it("hands the app back when a recheck finds the backend again", async () => {
    withFetch(unreachable);
    renderGate();
    await screen.findByTestId("service-paused-heading", undefined, {
      timeout: 4000,
    });

    withFetch(reachable);
    await userEvent.click(screen.getByTestId("service-paused-retry"));

    expect(await screen.findByTestId("app")).toBeInTheDocument();
    expect(
      screen.queryByTestId("service-paused-heading"),
    ).not.toBeInTheDocument();
  });
});
