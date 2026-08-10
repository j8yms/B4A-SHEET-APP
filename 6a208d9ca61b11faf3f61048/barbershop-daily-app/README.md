# Barbershop Daily App (Android) — MVP

Android app for a single barbershop to record daily activity (attendance + money) with **real-time sync** for all users and **automatic write-back to Google Sheets** (new daily tab: `Wed 03 Jun 2026` format).

## Users (as agreed)

- **Admins:** Ng’ash, Jay
- **Staff:** Esther, Cate

## What this MVP includes

- Login by **selecting name + PIN** (PIN stored via Firebase Auth password)
- Attendance: **Clock in** → On-time/Late (15 min grace)
  - Weekdays start: **08:00**
  - Weekends + Kenya public holidays start: **09:00**
  - End-of-day for “Absent”: **21:00**
- Transactions (Money): add/list entries matching your sheet fields
  - `NAME, AMOUNT, SERVED BY, PAY METHOD, STATUS, MPESA TO`
- Realtime sync via Firestore
- Google Sheets sync via Cloud Functions:
  - creates a new daily tab (e.g., `Wed 03 Jun 2026`)
  - writes an Attendance block + Transactions table

## Repo structure

- `android-app/` — Android (Kotlin + Jetpack Compose)
- `functions/` — Firebase Cloud Functions (TypeScript) for:
  - Kenya public holidays lookup
  - time rules (late/on-time, staff access hours)
  - Google Sheets write-back
- `firestore/` — Firestore Security Rules

## Setup (you will do these steps)

### 1) Firebase

1. Create a Firebase project
2. Add an Android app (package name in this repo: `com.barbershop.daily`)
3. Enable **Authentication → Email/Password**
4. Create Firestore database (production or test mode)
5. Download `google-services.json` and place it here:
   - `android-app/app/google-services.json`

### 2) Create the 4 user accounts (Auth)

We use “hidden emails” so staff can still “select name + PIN”.

Create these users in Firebase Auth (Email/Password), with temporary passwords (setup codes):

- `ngash@barbershop.local`
- `jay@barbershop.local`
- `esther@barbershop.local`
- `cate@barbershop.local`

First login: staff selects their name and enters the temporary password, then the app forces them to set a new PIN (password).

### 3) Set roles in Firestore

Create documents:

`users/ngash`:
```json
{ "displayName": "Ng'ash", "email": "ngash@barbershop.local", "role": "admin" }
```
`users/jay`: role `admin`
`users/esther`: role `staff`
`users/cate`: role `staff`

### 4) Google Sheets write-back

This MVP writes to your existing spreadsheet using a **Google service account**.

1. In Google Cloud Console (same project as Firebase, or any project), enable:
   - Google Sheets API
2. Create a **Service Account** + generate a **JSON key**
3. Share your Google Sheet with the service account email (Editor)
4. In Firebase Functions, set secrets/config (see `functions/README.md`)

### 5) Deploy backend

From `functions/`:
- `npm install`
- `firebase deploy --only functions,firestore:rules`

### 6) Run the Android app

Open `android-app/` in Android Studio and run.

---

## Notes / Defaults

- Pay method/status/mpesa-to use **dropdowns** (editable by admin in Settings).
- Staff access outside working hours is blocked **server-side** (Cloud Function) for clock-in and transaction creation.
