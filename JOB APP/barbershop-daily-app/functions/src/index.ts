import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { defineSecret, defineString } from "firebase-functions/params";
import * as admin from "firebase-admin";
import { google } from "googleapis";

admin.initializeApp();
const db = admin.firestore();

const GOOGLE_SERVICE_ACCOUNT_JSON = defineSecret("GOOGLE_SERVICE_ACCOUNT_JSON");
const BARBERSHOP_SHEET_ID = defineString("BARBERSHOP_SHEET_ID");

const TZ = "Africa/Nairobi";
const COUNTRY_CODE = "KE";

type Role = "admin" | "staff";

function formatDayKey(d: Date): string {
  // YYYY-MM-DD in Africa/Nairobi
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: TZ,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(d);

  const year = parts.find((p) => p.type === "year")?.value;
  const month = parts.find((p) => p.type === "month")?.value;
  const day = parts.find((p) => p.type === "day")?.value;
  return `${year}-${month}-${day}`;
}

function formatSheetTabName(d: Date): string {
  // "Wed 03 Jun 2026"
  return new Intl.DateTimeFormat("en-GB", {
    timeZone: TZ,
    weekday: "short",
    day: "2-digit",
    month: "short",
    year: "numeric",
  }).format(d);
}

function formatTimeHM(d: Date): string {
  return new Intl.DateTimeFormat("en-GB", {
    timeZone: TZ,
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(d);
}

function getLocalParts(d: Date): { y: number; m: number; day: number; hh: number; mm: number; dow: number } {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: TZ,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
    weekday: "short",
  }).formatToParts(d);

  const y = Number(parts.find((p) => p.type === "year")?.value);
  const m = Number(parts.find((p) => p.type === "month")?.value);
  const day = Number(parts.find((p) => p.type === "day")?.value);
  const hh = Number(parts.find((p) => p.type === "hour")?.value);
  const mm = Number(parts.find((p) => p.type === "minute")?.value);

  // Compute day-of-week using locale weekday token
  const wd = parts.find((p) => p.type === "weekday")?.value ?? "";
  const dowMap: Record<string, number> = { Mon: 1, Tue: 2, Wed: 3, Thu: 4, Fri: 5, Sat: 6, Sun: 0 };
  const dow = dowMap[wd] ?? 0;

  return { y, m, day, hh, mm, dow };
}

async function getUserRoleFromContext(context: any): Promise<{ email: string; role: Role; displayName: string }> {
  if (!context.auth?.token?.email) {
    throw new HttpsError("unauthenticated", "You must be logged in.");
  }

  const email = String(context.auth.token.email).toLowerCase();
  const snap = await db.doc(`userProfiles/${email}`).get();
  if (!snap.exists) {
    throw new HttpsError("failed-precondition", `No user profile found for ${email}.`);
  }

  const data = snap.data() as any;
  const role = (data.role ?? "staff") as Role;
  const displayName = String(data.displayName ?? email);
  return { email, role, displayName };
}

async function isKenyaHoliday(d: Date): Promise<boolean> {
  const { y, m, day } = getLocalParts(d);
  const year = y;
  const cacheRef = db.doc(`holidayCache/${COUNTRY_CODE}_${year}`);
  const cached = await cacheRef.get();

  let dates: string[] | null = null;
  if (cached.exists) {
    dates = (cached.data()?.dates ?? null) as string[] | null;
  }

  if (!dates) {
    const url = `https://date.nager.at/api/v3/PublicHolidays/${year}/${COUNTRY_CODE}`;
    const resp = await fetch(url);
    if (!resp.ok) {
      throw new Error(`Holiday API failed: ${resp.status} ${resp.statusText}`);
    }
    const json = (await resp.json()) as Array<{ date: string }>;
    dates = json.map((x) => x.date);
    await cacheRef.set(
      {
        country: COUNTRY_CODE,
        year,
        dates,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      },
      { merge: true }
    );
  }

  const mm = String(m).padStart(2, "0");
  const dd = String(day).padStart(2, "0");
  const iso = `${year}-${mm}-${dd}`;
  return dates.includes(iso);
}

async function getStartTimeForDay(d: Date): Promise<{ startHour: number; startMinute: number; isWeekendOrHoliday: boolean }> {
  const { dow } = getLocalParts(d);
  const weekend = dow === 0 || dow === 6;
  const holiday = await isKenyaHoliday(d);
  const isWeekendOrHoliday = weekend || holiday;
  return { startHour: isWeekendOrHoliday ? 9 : 8, startMinute: 0, isWeekendOrHoliday };
}

function minutesSinceMidnight(d: Date): number {
  const { hh, mm } = getLocalParts(d);
  return hh * 60 + mm;
}

function requireWithinStaffHours(now: Date, startHour: number, role: Role) {
  if (role === "admin") return;
  // Allow staff to use the app from 60 mins before start until 21:00.
  const start = startHour * 60 - 60;
  const end = 21 * 60; // 21:00
  const cur = minutesSinceMidnight(now);
  if (cur < start || cur > end) {
    throw new HttpsError("permission-denied", "Staff access is only allowed during working hours.");
  }
}

function computeArrival(now: Date, startHour: number, startMinute: number) {
  const grace = 15;
  const start = startHour * 60 + startMinute;
  const cur = minutesSinceMidnight(now);
  const diff = cur - start;
  if (diff <= grace) return { arrivalText: "ON TIME", isLate: false, minutesLate: 0 };
  return { arrivalText: `LATE (${diff}m)`, isLate: true, minutesLate: diff };
}

function getSheetIdFromConfig(): string {
  const sheetId = BARBERSHOP_SHEET_ID.value();
  if (!sheetId) throw new Error("Missing BARBERSHOP_SHEET_ID param for Cloud Functions.");
  return sheetId;
}

function getServiceAccountFromSecret(raw: string) {
  const json = JSON.parse(raw);
  if (!json.client_email || !json.private_key) throw new Error("Invalid service account JSON.");
  return json;
}

async function getSheetsClient(secretJson: string) {
  const sa = getServiceAccountFromSecret(secretJson);
  const auth = new google.auth.JWT({
    email: sa.client_email,
    key: sa.private_key,
    scopes: ["https://www.googleapis.com/auth/spreadsheets"],
  });
  await auth.authorize();
  return google.sheets({ version: "v4", auth });
}

async function ensureTabExists(sheets: any, spreadsheetId: string, tabName: string) {
  const meta = await sheets.spreadsheets.get({ spreadsheetId });
  const exists = (meta.data.sheets ?? []).some((s: any) => s.properties?.title === tabName);
  if (exists) return;

  await sheets.spreadsheets.batchUpdate({
    spreadsheetId,
    requestBody: {
      requests: [{ addSheet: { properties: { title: tabName } } }],
    },
  });
}

async function writeSheetForDay(dayKey: string, tabName: string, secretJson: string) {
  const spreadsheetId = getSheetIdFromConfig();
  const sheets = await getSheetsClient(secretJson);
  await ensureTabExists(sheets, spreadsheetId, tabName);

  const attendanceSnap = await db.collection(`days/${dayKey}/attendance`).orderBy("displayName").get();
  const txSnap = await db.collection(`days/${dayKey}/transactions`).orderBy("createdAt").get();

  const attendanceRows: any[] = [
    ["ATTENDANCE", "", "", ""],
    ["NAME", "IN", "Time", "Arrival"],
  ];
  attendanceSnap.docs.forEach((doc) => {
    const a: any = doc.data();
    attendanceRows.push([a.displayName ?? "", a.in ?? false, a.time ?? "", a.arrival ?? ""]);
  });
  attendanceRows.push(["", "", "", ""]);

  const txRows: any[] = [
    ["MONEY", "", "", "", "", "", "", ""],
    ["s/N", "NAME", "AMOUNT", "SERVED BY", "PAY METHOD", "STATUS", "MPESA TO", "last editor"],
  ];

  let sn = 1;
  txSnap.docs.forEach((doc) => {
    const t: any = doc.data();
    txRows.push([
      sn++,
      t.clientName ?? "",
      t.amount ?? "",
      t.servedBy ?? "",
      t.payMethod ?? "",
      t.status ?? "",
      t.mpesaTo ?? "",
      t.lastEditor ?? "",
    ]);
  });

  const values = [...attendanceRows, ...txRows];

  await sheets.spreadsheets.values.update({
    spreadsheetId,
    range: `'${tabName}'!A1`,
    valueInputOption: "USER_ENTERED",
    requestBody: { values },
  });
}

// Callable: clock in
export const clockIn = onCall(
  { region: "europe-west1" },
  async (req) => {
    const now = new Date();
    const { role, displayName, email } = await getUserRoleFromContext(req);
    const { startHour, startMinute } = await getStartTimeForDay(now);
    requireWithinStaffHours(now, startHour, role);

    const { arrivalText } = computeArrival(now, startHour, startMinute);
    const dayKey = formatDayKey(now);
    const tabName = formatSheetTabName(now);

    await db.doc(`days/${dayKey}`).set({ sheetTabName: tabName, updatedAt: admin.firestore.FieldValue.serverTimestamp() }, { merge: true });
    await db.doc(`days/${dayKey}/attendance/${email}`).set(
      {
        email,
        displayName,
        in: true,
        time: formatTimeHM(now),
        arrival: arrivalText,
        clockInAt: admin.firestore.FieldValue.serverTimestamp(),
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      },
      { merge: true }
    );

    return { dayKey, tabName, arrival: arrivalText, time: formatTimeHM(now) };
  }
);

// Callable: create transaction
export const createTransaction = onCall(
  { region: "europe-west1" },
  async (req) => {
    const now = new Date();
    const { role, displayName, email } = await getUserRoleFromContext(req);
    const { startHour } = await getStartTimeForDay(now);
    requireWithinStaffHours(now, startHour, role);

    const body = (req.data ?? {}) as any;
    const clientName = String(body.clientName ?? "").trim();
    const amount = Number(body.amount ?? 0);
    const servedBy = String(body.servedBy ?? displayName).trim();
    const payMethod = String(body.payMethod ?? "").trim();
    const status = String(body.status ?? "").trim();
    const mpesaTo = String(body.mpesaTo ?? "").trim();

    if (!clientName) throw new HttpsError("invalid-argument", "Client name is required.");
    if (!Number.isFinite(amount) || amount <= 0) throw new HttpsError("invalid-argument", "Amount must be > 0.");
    if (!servedBy) throw new HttpsError("invalid-argument", "Served by is required.");
    if (!payMethod) throw new HttpsError("invalid-argument", "Pay method is required.");

    const dayKey = formatDayKey(now);
    const tabName = formatSheetTabName(now);

    await db.doc(`days/${dayKey}`).set({ sheetTabName: tabName, updatedAt: admin.firestore.FieldValue.serverTimestamp() }, { merge: true });

    const txRef = db.collection(`days/${dayKey}/transactions`).doc();
    await txRef.set({
      clientName,
      amount,
      servedBy,
      payMethod,
      status,
      mpesaTo,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      createdBy: email,
      lastEditor: `${displayName} ${formatTimeHM(now)}`,
    });

    return { dayKey, tabName, txId: txRef.id };
  }
);

// Firestore trigger: whenever attendance/transactions change, refresh today’s sheet tab.
export const syncDayToSheet = onDocumentWritten(
  {
    region: "europe-west1",
    document: "days/{dayKey}/{subcol}/{docId}",
    secrets: [GOOGLE_SERVICE_ACCOUNT_JSON],
  },
  async (event) => {
    const dayKey = event.params.dayKey as string;
    const dayDoc = await db.doc(`days/${dayKey}`).get();
    if (!dayDoc.exists) return;
    const tabName = String(dayDoc.data()?.sheetTabName ?? "");
    if (!tabName) return;

    const secretJson = GOOGLE_SERVICE_ACCOUNT_JSON.value();
    await writeSheetForDay(dayKey, tabName, secretJson);
  }
);
