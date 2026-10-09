# 🔒 Security

Claude Usage Meter is a **client-only Android app**: there is no server, no database and no user passwords. It stores one secret — your claude.ai session — on your phone and talks only to `https://claude.ai`.

This page reviews the app against the usual 20-point pre-launch checklist. ✅ = applied · ➖ = not applicable (and why).

| # | Check | Status | How |
| --- | --- | --- | --- |
| 1 | Hide API keys | ✅ | The app has no API keys. Your claude.ai session is encrypted at rest (see #5) and never logged. |
| 2 | Remove secrets from Git | ✅ | Full history scanned: no keys, passwords, keystores or APKs. `.gitignore` blocks `*.jks`, `*.keystore` and `*.apk`. |
| 3 | Public key for the DB | ➖ | No database or backend. |
| 4 | Row-Level Security | ➖ | No database. Each account's history is a separate local file. |
| 5 | Encrypt sensitive data | ✅ | The session is encrypted with **AES-256-GCM** using a key in the **Android Keystore** (hardware-backed, non-exportable). |
| 6 | Enforce authentication | ✅ | Optional **app lock with fingerprint, face or PIN**, asked again after 30 s in the background. |
| 7 | Restrict record access | ✅ | App data is private to the app. **Cloud backup and device-to-device transfer are disabled**, so the session never leaves the phone. |
| 8 | Block field tampering | ✅ | Restoring a backup only accepts known setting names with the right types; sensitive fields (accounts, sessions, system IDs) are never restored. |
| 9 | Protect session cookies | ✅ | The session is encrypted, excluded from backups and exports, and only sent to `claude.ai` over HTTPS. |
| 10 | Hash passwords | ➖ | The app never sees or stores passwords; sign-in happens on claude.ai. |
| 11 | Rate limiting | ✅ | Smart polling (30 s active, 2–10 min idle) and **exponential backoff on errors** (1 → 2 → 4 → 8 → 15 min), so claude.ai is never flooded. |
| 12 | Bot protection | ➖ | No public endpoint. The app reads usage through a real browser engine, like a person using claude.ai. |
| 13 | Parameterized queries | ➖ | No SQL or database. |
| 14 | Validate inputs | ✅ | A pasted sessionKey must match a strict format. Backup files have a size limit, and IDs and history rows are validated line by line. |
| 15 | Sanitize content | ✅ | Text from claude.ai (organization name) is stripped of control characters and length-limited. All text is shown as plain text, never as HTML. |
| 16 | Restrict files | ✅ | The built-in browsers can't read phone files. The usage reader **only navigates within `https://claude.ai`**, so its bridge is never exposed to another site. Backups use the system file picker. |
| 17 | Return only necessary data | ✅ | Only usage percentages and reset times are read. The automation event (Tasker/MacroDroid) is **off by default** and only shares the event name and percentage. |
| 18 | Security headers | ➖ | No web server. The built-in browsers use **Safe Browsing**. |
| 19 | Force HTTPS | ✅ | **Cleartext (HTTP) traffic is blocked** for the whole app. |
| 20 | Scan dependencies | ✅ | **Zero third-party libraries**: only the Android and Java standard libraries. Fonts are static files under the SIL Open Font License. |

Additional hardening: signing out from a notification asks for confirmation first, and a broken or unreadable session simply asks you to sign in again.

## Reporting a problem

Open an issue in this repository. Please don't include your sessionKey or screenshots with personal data.
