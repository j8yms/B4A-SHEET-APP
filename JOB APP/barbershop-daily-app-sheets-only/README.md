# Barbershop Daily App (Android) — Sheets-only MVP

Android app for a single barbershop to record daily activity (attendance + money) with syncing through **Google Sheets only**.

This version uses:
- **Google Apps Script Web App** as the backend (rules/permissions enforced there)
- Your existing **Google Sheet** as the database

## Users (as agreed)

- **Admins:** Ng’ash, Jay (each can register up to **2 phones**)
- **Staff:** Esther, Cate (each can register **1 phone**)

## What this MVP includes

- Login by **selecting name + PIN**
  - First login sets your PIN in the private `__ADMIN__` tab
  - **Staff cannot login outside working hours** (admins can anytime)
- Attendance: **Clock in** → On-time/Late (15 min grace)
  - Weekdays start: **08:00**
  - Weekends + Kenya public holidays start: **09:00**
  - “Today” switches at **midnight** (Kenya time)
  - Close time: **21:00**
- Transactions (Money): staff can:
  - **view all today’s transactions**
  - **edit/delete only the transactions they created**, and only for today
- Google Sheet write-back:
  - creates a new daily tab named like: `Wed 03 Jun 2026`
  - writes an Attendance block + Transactions table (matching your MONEY columns)

## Repo structure

- `android-app/` — Android (Kotlin + Jetpack Compose)
- `apps_script/` — Google Apps Script backend (Web App)

## Setup

### 1) Apps Script backend

Follow:
- `apps_script/README.md`

When done, paste your Web App URL into:
- `android-app/app/src/main/java/com/barbershop/daily/data/SheetsApi.kt`

### 2) Google Sheet permissions

To keep permissions enforceable:
- Only admins should have normal access to the spreadsheet
- Staff should use the Android app only
- Create a private tab named `__ADMIN__` and ensure only admins can view it

### 3) Run the Android app

Open `android-app/` in Android Studio and run.
