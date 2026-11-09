import { defineConfig } from "@hey-api/openapi-ts";

export default defineConfig({
  input: "./openapi.yaml",
  output: {
    path: "src/api/generated",
    postProcess: ["prettier"],
  },
  plugins: [
    "@hey-api/client-fetch",
    {
      name: "@tanstack/react-query",
      queryOptions: true,
      mutationOptions: true,
    },
    {
      name: "zod",
      requests: true,
      responses: true,
    },
  ],
});
