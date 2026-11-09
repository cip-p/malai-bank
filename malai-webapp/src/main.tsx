import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { QueryClientProvider } from "@tanstack/react-query";
import { ThemeProvider as NextThemesProvider } from "next-themes";
import { configureApiClient } from "@/api/http";
import { BackendAvailabilityGate } from "@/availability/BackendAvailabilityGate";
import { createQueryClient } from "@/lib/queryClient";
import { ThemeProvider } from "@/theming/useTheme";
import { ThemedToaster } from "@/theming/ThemedToaster";
import { warmEntryChunk } from "@/routeChunks";
import { App } from "./App";
import "./index.css";

configureApiClient();
void warmEntryChunk(window.location.pathname);

const queryClient = createQueryClient();

const rootEl = document.getElementById("root");
if (!rootEl) throw new Error("Root element #root not found");

createRoot(rootEl).render(
  <StrictMode>
    <NextThemesProvider
      attribute="class"
      defaultTheme="system"
      enableSystem
      disableTransitionOnChange
      storageKey="malai-mode"
    >
      <ThemeProvider>
        <QueryClientProvider client={queryClient}>
          <BackendAvailabilityGate>
            <App />
            <ThemedToaster />
          </BackendAvailabilityGate>
        </QueryClientProvider>
      </ThemeProvider>
    </NextThemesProvider>
  </StrictMode>,
);
