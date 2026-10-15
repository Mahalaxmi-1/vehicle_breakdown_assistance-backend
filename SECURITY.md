# RoadAssist session security and local secrets

The application uses an HttpOnly server session cookie. The frontend must call
`GET /api/auth/csrf` before each state-changing request and send the returned
`XSRF-TOKEN` cookie value in the `X-XSRF-TOKEN` header. The Axios client does
this automatically. The CSRF cookie is readable by the frontend; the session
identifier is not.

## Database password

The database password is read from the `DB_PASSWORD` environment variable. Do
not put it in `application.properties`, source control, shell history, or a
project `.env` file.

For local development, set it only in the current PowerShell session. Enter the
rotated password at the prompt so it is not embedded in a command:

```powershell
$secureDbPassword = Read-Host "Database password" -AsSecureString
$credential = [System.Net.NetworkCredential]::new("", $secureDbPassword)
$env:DB_PASSWORD = $credential.Password
Remove-Variable credential, secureDbPassword
```

Start the backend from that same PowerShell session. When finished, clear the
process environment variable:

```powershell
Remove-Item Env:DB_PASSWORD
```

A child process launched from the session receives the environment variable.
Avoid printing it or including it in diagnostic output. For deployment, store
the value in the hosting platform's secret manager and inject it as the
`DB_PASSWORD` environment variable at runtime. Restrict access to the secret
and rotate it in the database and secret manager together. If a credential has
been committed or otherwise exposed, rotate it promptly; changing this
configuration does not rotate the database credential itself.

When creating a ZIP manually, exclude `.env` files, local configuration files,
backups, truststores, credential files, build outputs, and dependency folders.

## Address geocoding

Owner breakdown addresses and mechanic service addresses are sent from the
backend to the configured geocoding service so the server can calculate nearby
matches. Configure a private, HTTPS Nominatim-compatible service in
`ROADASSIST_GEOCODER_BASE_URL`; it must be the base URL of a private,
Nominatim-compatible service serving `/search?format=jsonv2&limit=1&q=...`.
The default is intentionally empty, so address resolution returns a safe
unavailable response until configured. The backend emits a startup warning
without logging endpoint values or addresses. Set the variable in the same
PowerShell process that starts the backend and restart the JVM after changing
it. Set `ROADASSIST_GEOCODER_USER_AGENT` to an identifying application contact
string when operating the service.

For local-only demonstrations, the explicit `mock-geocoder` Spring profile
resolves only the synthetic input `ROADASSIST SYNTHETIC LOCATION`; it rejects
all other input. Its fixed coordinates are mock data, not address results. Never
activate this profile in production or use it for real owner/mechanic locations.

RoadAssist rejects the public `nominatim.openstreetmap.org` endpoint because
breakdown/service addresses may be personal location data. Do not send private
addresses to a provider unless your use and retention practices are appropriate.
OSM-based results must retain OpenStreetMap attribution. The application caches
normalized address results in memory for 24 hours and unsuccessful lookups for
five minutes; cache contents are not persisted.
The root `.gitignore` helps with Git workflows but does not filter files from a
manually assembled archive. Review the archive contents before sharing it.

## One-time local administrator setup

Public registration intentionally accepts only OWNER and MECHANIC. To create
the first administrator, the backend has a startup-only bootstrap runner that
is active only with the `local` Spring profile and the explicit
`roadassist.admin-bootstrap.enabled=true` setting. It is not an HTTP endpoint.
It creates an ADMIN with the existing `PasswordEncoder` bean (BCrypt). If any
ADMIN already exists, it makes no changes; if the requested email belongs to
any existing account, startup fails without changing that account.

First ensure the existing MySQL database credential has been rotated if it was
exposed, then set `DB_PASSWORD` in this same PowerShell session using the
instructions above. Set the non-password account fields and enable bootstrap:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'local'
$env:ROADASSIST_ADMIN_BOOTSTRAP_ENABLED = 'true'
$env:ROADASSIST_ADMIN_BOOTSTRAP_FULL_NAME = Read-Host 'Administrator full name'
$env:ROADASSIST_ADMIN_BOOTSTRAP_EMAIL = Read-Host 'Administrator email'
$env:ROADASSIST_ADMIN_BOOTSTRAP_PHONE = Read-Host 'Administrator phone (+91 optional)'
$secureAdminPassword = Read-Host 'Administrator password (8 to 72 characters)' -AsSecureString
$adminCredential = [System.Net.NetworkCredential]::new('', $secureAdminPassword)
$env:ROADASSIST_ADMIN_BOOTSTRAP_PASSWORD = $adminCredential.Password
Remove-Variable adminCredential, secureAdminPassword
```

From `backend/`, start the app in that same session:

```powershell
.\mvnw.cmd spring-boot:run
```

Wait for the log message confirming that the initial local administrator was
provisioned. Stop the process with Ctrl+C, then clear the bootstrap settings
before restarting normally:

```powershell
Remove-Item Env:ROADASSIST_ADMIN_BOOTSTRAP_ENABLED
Remove-Item Env:ROADASSIST_ADMIN_BOOTSTRAP_FULL_NAME
Remove-Item Env:ROADASSIST_ADMIN_BOOTSTRAP_EMAIL
Remove-Item Env:ROADASSIST_ADMIN_BOOTSTRAP_PHONE
Remove-Item Env:ROADASSIST_ADMIN_BOOTSTRAP_PASSWORD
Remove-Item Env:SPRING_PROFILES_ACTIVE
```

Restart the backend with `DB_PASSWORD` still set, then sign in through the
existing frontend login page using the provisioned email and password and
select the ADMIN role. Clear `DB_PASSWORD` from the session when finished as
described above. Do not enable this local profile or bootstrap flag in a
deployment environment.

## Session cookies

For local development, the session cookie uses `SameSite=Lax` and does not
require HTTPS. For production, serve the frontend and API over HTTPS and set
`ROADASSIST_COOKIE_SECURE=true`. Keep `ROADASSIST_COOKIE_SAME_SITE=lax` when both
services are same-site. If they must be cross-site, use
`ROADASSIST_COOKIE_SAME_SITE=none`, keep `ROADASSIST_COOKIE_SECURE=true`, and
set `ROADASSIST_FRONTEND_ORIGIN` to the exact deployed frontend origin; never
use wildcard origins with credentials. These settings apply to both the session
and CSRF cookies.
