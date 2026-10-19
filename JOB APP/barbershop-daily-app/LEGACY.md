# Legacy Firebase Implementation - RETIRED

## Status: RETIRED / NOT DEPLOYED

This Firebase-based implementation (`barbershop-daily-app/`) is **legacy code** and has been **replaced** by the Google Sheets-only implementation in `barbershop-daily-app-sheets-only/`.

### Evidence of Retirement:

1. **Placeholder Firebase Project ID**: The `.firebaserc` file contains `"default": "YOUR_FIREBASE_PROJECT_ID"` - a clear placeholder indicating no real Firebase project was configured.

2. **No Active Deployment**: The Cloud Functions (`functions/src/index.ts`) reference secrets (`GOOGLE_SERVICE_ACCOUNT_JSON`, `BARBERSHOP_SHEET_ID`) that were never configured in a Firebase project.

3. **Superseded Architecture**: The Sheets-only app (`barbershop-daily-app-sheets-only/`) provides the same functionality using Google Apps Script + Google Sheets, which is simpler to deploy and maintain.

4. **Firestore Rules Analysis**: The `firestore.rules` file shows a design where:
   - All signed-in users can read all historical day data (`days/{dayKey}` and subcollections)
   - Only admins can write user profiles and app settings
   - Transactions and attendance are written by Cloud Functions only
   - **Risk**: If this were deployed, any authenticated staff member could read all historical transaction data for all staff across all days.

### Security Note:

If this Firebase implementation were ever deployed, the Firestore rules would need tightening:
- Staff should only read their own day's data (not all historical days)
- Transaction read access should be limited to the creator or admins
- Consider adding `request.auth.token.email` checks for staff data isolation

### Migration Path:

The Sheets-only app (`barbershop-daily-app-sheets-only/`) is the **current production implementation** and includes:
- Google Apps Script backend (`apps_script/Code.gs`)
- Android app with encrypted session storage
- WorkManager-based scheduled refreshes
- Audit logging
- Session revocation via durable registry
- PIN policy enforcement (4-6 digits)
- Race condition protection via LockService

### Recommendation:

**Do not deploy** this Firebase implementation. Archive or remove this folder to avoid confusion. The Sheets-only app is the active codebase.