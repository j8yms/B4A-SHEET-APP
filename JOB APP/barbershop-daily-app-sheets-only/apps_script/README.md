# Google Apps Script backend (Sheets-only)

This folder contains the Google Apps Script Web App backend that the Android app calls.

## Create the Apps Script project

1. Open your Google Sheet
2. Go to **Extensions → Apps Script**
3. Create two files:
   - `Code.gs` (copy from `apps_script/Code.gs`)
   - `appsscript.json` (copy from `apps_script/appsscript.json`) — switch to “Project Settings → Show appsscript.json”

## Configure script properties

In Apps Script:
- **Project Settings → Script Properties**

Set:
- `ADMIN_SECRET` = a long random string (only admins know)

## Deploy as Web App

1. Click **Deploy → New deployment**
2. Select type: **Web app**
3. Execute as: **Me**
4. Who has access: **Anyone** (or Anyone with the link)
5. Deploy and copy the **Web App URL**

Paste the URL into the Android project:
- `android-app/app/src/main/java/com/barbershop/daily/data/SheetsApi.kt`

## Sheet structure created/used

### Private tab (admins only)

Create a tab named `__ADMIN__` and share the sheet normally, but restrict the `__ADMIN__` tab to admins only.

The script will create/populate tables inside `__ADMIN__`:
- Users (name, role, pin hash, device registrations)
- Settings (grace minutes, close time, etc.)

### Daily tabs

The script creates daily tabs using:

`Wed 03 Jun 2026`

Each daily tab contains:
- Attendance block
- Transactions table

Attendance arrival cells are automatically colored:
- EARLY / ON TIME = green
- LATE = red

Transactions include a **last editor** column in the format:
- `HH:mm - Name`

---

## Important note about permissions

To keep rules enforceable:
- **Admins** can access the Google Sheet normally
- **Staff should NOT be granted edit access to the sheet**
  - Staff use only the Android app
