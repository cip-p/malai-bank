const AUTH_PATH_PREFIX = "/api/v1/auth/";
const REFRESH_TOKEN_PATH = "/api/v1/auth/refresh-token";

type RequestFetch = (request: Request) => Promise<Response>;

function isAuthRequest(request: Request): boolean {
  return new URL(request.url).pathname.startsWith(AUTH_PATH_PREFIX);
}

function refreshUrlFor(request: Request): string {
  return new URL(REFRESH_TOKEN_PATH, request.url).toString();
}

export function createRefreshingFetch(innerFetch: RequestFetch): typeof fetch {
  let inFlightRefresh: Promise<boolean> | null = null;

  async function attemptRefresh(refreshUrl: string): Promise<boolean> {
    try {
      const response = await innerFetch(
        new Request(refreshUrl, {
          method: "POST",
          credentials: "include",
          // Both of these are load-bearing even though the server reads the
          // refresh token from the HttpOnly cookie and needs nothing from the
          // body.
          //
          // Without an explicit Content-Type this POST reached the API as
          // `application/x-www-form-urlencoded;charset=UTF-8`. Spring picks a
          // message converter by content type before it looks at
          // `@RequestBody(required = false)`, no JSON converter will read
          // form-urlencoded, and the resulting HttpMediaTypeNotSupportedException
          // surfaced as a 500. Refresh then failed for every expired session,
          // which is the one moment it has to work.
          //
          // `{}` rather than no body at all: RefreshTokenRequest has all-
          // nullable fields, so an empty object deserialises to (null, null)
          // and the controller falls through to the cookie path exactly as a
          // bodyless request was meant to.
          headers: { "Content-Type": "application/json" },
          body: "{}",
        }),
      );
      return response.ok;
    } catch {
      return false;
    }
  }

  function refreshOnce(refreshUrl: string): Promise<boolean> {
    inFlightRefresh ??= attemptRefresh(refreshUrl).finally(() => {
      inFlightRefresh = null;
    });
    return inFlightRefresh;
  }

  return async function refreshingFetch(
    input: RequestInfo | URL,
    init?: RequestInit,
  ): Promise<Response> {
    const request = input instanceof Request ? input : new Request(input, init);

    if (isAuthRequest(request)) {
      return innerFetch(request);
    }

    const retryable = request.clone();
    const response = await innerFetch(request);

    if (response.status !== 401) {
      return response;
    }

    const refreshed = await refreshOnce(refreshUrlFor(request));
    if (!refreshed) {
      return response;
    }

    return innerFetch(retryable);
  };
}
