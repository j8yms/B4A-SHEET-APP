# Firebase Functions (Sheets sync)

## Required configuration

### Google Sheet

- `BARBERSHOP_SHEET_ID`: your spreadsheet id (from the URL)

Create `functions/.env`:
```bash
copy .env.example .env
```
Then edit `BARBERSHOP_SHEET_ID` if needed.

### Service account

Create a Google service account JSON key.

Store it as a Firebase Functions secret (recommended):

```bash
firebase functions:secrets:set GOOGLE_SERVICE_ACCOUNT_JSON
```

Paste the full JSON content when prompted.

Deploy:

```bash
firebase deploy --only functions
```

## What gets written to the Sheet

Each day a new tab is created using format: `Wed 03 Jun 2026`.

Inside the tab:

1) Attendance block
- Name
- IN (true/false)
- Time (HH:mm)
- Arrival (On time / LATE (Xm) / ABSENT)

2) Transactions table (columns match your MONEY tab)
- s/N
- NAME
- AMOUNT
- SERVED BY
- PAY METHOD
- STATUS
- MPESA TO
- last editor (name + time)
