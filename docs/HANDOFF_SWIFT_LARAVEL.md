# AirDrop — Swift + Laravel Handoff (from Kotlin)

Parity + API items completed on **Kotlin** (`claude/live-chat-native`) that the
Swift and Laravel teams need to mirror or are already deployed. Dated 2026-07-20.

---

## → SWIFT (parity — pull these from Kotlin)

### 1. Brand color (`BrandPalette`, Kotlin `core/designsystem/theme/Color.kt`)
Use these exact values so Swift matches Kotlin/Figma:

| Token | Light | Dark |
|---|---|---|
| **Primary orange** (`OrangeMain`) | `#F15114` | `#F46427` (`OrangeMainDark`) |
| Orange accent | `#F99A3C` | — |
| Blue main | `#2A2367` | — |
| Blue accent | `#0A96D4` | — |
| Button static (legacy pumpkin) | `#FF8000` | — |

Primary CTA / accent everywhere = **`#F15114`** (light), **`#F46427`** (dark).

### 2. App icon
Kotlin launcher = the AirDrop crest/logo, `res/mipmap-*/ic_launcher.png` (192×192
at xxxhdpi, RGBA). Swift's `AppIcon` should use the **same crest asset** so the
home-screen icon matches across platforms. (Kotlin currently ships a raster
`ic_launcher.png`; no adaptive-icon XML — a single crest PNG per density.)

### 3. Feature parity implemented on Kotlin this cycle
Mirror these on Swift:
- **Active Sessions** screen under Settings — lists signed-in devices, tags the
  current one "This device", "Sign out" per device, and **"Sign out all other
  devices"**. Uses `GET /user/sessions`, `DELETE /user/sessions/{id}`, and the
  new `POST /user/sessions/revoke` (below).
- **Delivery map** = the Laravel Google-Maps picker in a WebView:
  `GET {WEB_BASE}/api/v1/delivery/picker?embed=ios[&lat=&lng=]`. `embed=ios`
  hides the page's own chrome; the native screen keeps its search/controls; the
  page posts `{event:'location-selected', latitude, longitude, address}` to the
  `deliveryPicker` message handler. (Swift already has the WKWebView host — this
  is what Kotlin now matches.)
- **Order total shows BOTH currencies side by side** — Cart footer "Order Total"
  and Order Summary "Total Charges" render `JMD x,xxx.xx · USD xx.xx` (derive the
  other from the exchange rate).
- **Tax only when applicable** — the flat `$5.00` placeholder is gone; a tax row
  shows only when the value is > 0 (Swift's order summary already has no tax
  line — keep it that way unless the backend returns a real tax; see Laravel).
- **Unified header back arrow** = the single left chevron everywhere.
- **Contacts** value text = Normal weight (headings stay Bold).
- **AirCoin** balance = flush cards, cropped coin, values from the API.

---

## → LARAVEL (API)

### 1. ✅ DONE + DEPLOYED — bulk revoke endpoint
`POST /api/v1/user/sessions/revoke` → `UserController::revokeOtherSessions()`.
Deletes every Sanctum token for the authenticated user **except the current one**
(current device stays signed in; clients use Logout to end it). Scoped to the
user's own tokens (no IDOR). Returns:
```json
{ "success": true, "message": "Other devices signed out", "data": { "revoked_count": 381 } }
```
On `origin/pre_staging` (commit `5866b973`), route
`api.v1.user.sessions.revoke-others`. Verified live. **No Swift/Laravel work
needed — just consume it.**

### 2. Session list contract (already deployed, for reference)
`GET /user/sessions` → `data: [{ id, device_name, platform, last_seen_ip, last_seen_at, is_current }]`.
`DELETE /user/sessions/{id}` → revokes one (422 `CANNOT_REVOKE_CURRENT` on the current).

### 3. Tax contract (needs a backend decision — not built)
Both mobile apps now display a **Tax** line **only when a tax value > 0** is
present; there is currently **no tax field in the checkout response**, so no tax
shows (matches Swift). **If AirDrop needs to charge/display sales tax**, return a
`tax` amount (major units, USD) on the checkout-creation response (alongside
`delivery_fee`) or the order/checkout-flow payload. The apps are already wired to
render it conditionally — this is a backend build decision, deliberately not
implemented speculatively.
