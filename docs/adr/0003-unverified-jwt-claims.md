# ADR 0003 — JWT claim handling (optional verification via jjwt)

**Status:** Accepted (revised)

## Context
The bot needs the ThingsBoard customerId / zone / display name from the user's JWT to route a
query to the right data. Historically it never held ThingsBoard's JWT signing key, so it could
only base64-decode claims without verifying the signature. ThingsBoard itself remains the
authoritative validator: every privileged action forwards `X-TB-Token` to ThingsBoard, which
checks signature, expiry, and audience.

An audit recommended adopting a battle-tested library (jjwt) for verification. The blocker was
the missing signing key — without it, jjwt could only repeat the same unverified decode while
implying a stronger guarantee than existed.

## Decision
Support **both** modes in `JwtParserUtil`, selected by whether `IOTCHATBOT_JWT_SIGNING_KEY`
(base64) is configured:

- **Key set →** verify the signature and expiry with jjwt (`Jwts.parser().verifyWith(key)
  .build().parseSignedClaims(token)`, jjwt 0.12.x API) before reading any claim. A token that
  fails verification yields no claims.
- **Key blank (dev default) →** the previous unverified decode, with a `[SECURITY]` warning. An
  unusable key is logged and treated as blank so the application still starts.

The public static API (`extractCustomerId`, `extractClaim`, `extractHost`, `isExpired`) is
unchanged, so the ~9 call sites are untouched. The key is pushed into the static util at startup
by `JwtParserInitializer` from `SecurityProperties.jwtSigningKey`.

## Consequences
- **+** Production can enforce real signature verification by setting one env var.
- **+** Default behavior is unchanged and non-breaking; misconfiguration fails open to ThingsBoard
  rather than taking down routing.
- **+** Claims are still never the sole basis of an authorization decision.
- **−** If the configured key does not match ThingsBoard's, all tokens fail verification and
  routing degrades — verification must be enabled with the correct key.
- **−** Adds the jjwt dependency (api compile, impl + jackson runtime).
