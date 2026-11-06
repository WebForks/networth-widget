<img src="assets/icon.svg" width="64" height="64" alt="">

# networth-widget

A personal Android app and home-screen widget that adds up your bank, brokerage, IRA and 401(k)
balances and shows today's net worth, the day's change, and a red/green trend line.

- **Free, automatic.** Balances come from [Plaid](https://plaid.com)'s free Trial plan (up to 10
  institution logins, including Chase), refreshed in the background. No manual entry.
- **No server.** The app talks to Plaid directly. Your Plaid keys and bank access tokens are
  encrypted with the phone's Android Keystore and excluded from backups.
- **Widget sizes.** Small: total + today's change. Medium: adds the trend line. Large: adds
  1M / YTD / 1Y and a refresh button. Tap it to open the app.
- **App.** Net worth chart (1D, 1W, 1M, 3M, YTD, 1Y, All, with press-and-drag to scrub),
  breakdown by category, per-institution and per-account charts, fund holdings with period
  return and gain vs. cost basis, include/exclude and rename accounts.

> History starts the day you link an account. Plaid doesn't give past daily balances, so
> 1M/YTD/1Y fill in as the app records a snapshot each day.

## Linking institutions

Works with any Plaid-supported bank, brokerage, IRA or 401(k) provider.

- **Bank or credit card:** checking, savings, credit cards. Some banks (e.g. Chase) ask you to
  choose how long to share access; pick **"always"** or you'll have to reconnect when it expires.
- **Investment or retirement:** brokerage accounts, IRAs, 401(k)s.
- **Employer 401(k) plans run by Alight:** search Plaid for **"Alight - &lt;your employer&gt;"**
  and sign in with the credentials for your employer's benefits site.

## 1. Get free Plaid keys (one time, ~10 min)

1. Sign up at <https://dashboard.plaid.com/signup>. Choose personal / individual use.
2. Apply for the free **Trial** plan at <https://dashboard.plaid.com/trial-plan> (not Pay As You
   Go / Growth / Custom, which are paid). For products, pick only **Transactions** and
   **Investments**. It's a short questionnaire; say it's
   a personal app for viewing your own balances.
3. **Developers → Keys**: copy your `client_id`, **Sandbox** secret and **Production** secret.
4. **Developers → API → Allowed Android package names**: add `com.webforks.networth`.
   (Without this, Plaid Link refuses to open in the app.)

Trial-plan caveats: 10 connections max, and removing a connection does **not** free its slot, so
avoid repeatedly removing and re-adding institutions.

## 2. Build and install (Linux)

The toolchain lives in your home directory (no root needed):

- JDK 21: `~/.local/share/jdk-21`
- Android SDK: `~/Android/Sdk`

On the phone: **Settings → About phone → tap "Build number" 7 times**, then
**Settings → System → Developer options → USB debugging: on**. Plug in over USB and accept the
"Allow USB debugging?" prompt.

```bash
./install.sh            # builds and installs the app on the connected phone
```

Or manually:

```bash
export JAVA_HOME=~/.local/share/jdk-21 ANDROID_HOME=~/Android/Sdk
./gradlew installRelease   # or installDebug
./gradlew test             # unit tests for the history/period math
```

## 3. First run

1. Open **Net Worth → Settings**, paste your `client_id` and secrets, **Save keys**.
2. Try it with **Sandbox** first: back on the home screen, tap *Bank or credit card*, pick any
   bank, and sign in with `user_good` / `pass_good`.
3. Switch **Settings → New connections use → Production**, then link each real institution.
   Sandbox connections are labeled "(sandbox)"; remove them from the institution screen.
4. Long-press the home screen → **Widgets → Net Worth**, and resize it to taste.

When a bank needs you to sign in again (password change, MFA, expired consent), the widget shows
⚠ and the app shows a **Reconnect** card.

## Project layout

```
app/src/main/java/com/webforks/networth/
  data/plaid/     Plaid REST client + models
  data/db/        Room database: connections, accounts, holdings, daily snapshots
  data/secure/    Keystore-encrypted secret storage
  data/           Repository (link/refresh/remove), settings
  domain/         History math (daily series, period changes), formatting, categories
  sync/           WorkManager periodic refresh
  widget/         Glance home-screen widget + trend-line renderer
  ui/             Compose screens: home, institution, account, settings
```
