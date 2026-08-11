/**
 * Barbershop Daily — Sheets-only backend (Google Apps Script Web App)
 *
 * Enforces:
 * - Admin vs Staff
 * - Device lock: staff 1 device, admin up to 2 devices
 * - Staff can see ONLY today's data (midnight boundary, Africa/Nairobi)
 * - Staff can edit/delete ONLY their own transactions and ONLY today
 * - Late/on-time rules:
 *   - Weekdays start 08:00
 *   - Weekends + Kenya public holidays start 09:00
 *   - Grace 15 minutes
 * - Close time 21:00 (for Absent reporting; staff access hours can be tightened later)
 *
 * Daily tab name format: "Wed 03 Jun 2026"
 *
 * Admin/staff roster is stored in the __ADMIN__ tab and can be renamed
 * by admins at runtime (see renameUser action).
 *
 * Queue/booking link: a QUEUE + BARBERS tab pair lives in the main
 * spreadsheet, or in an external shared sheet when the `queueSheetId`
 * setting is set. A booking website can write to / read from the same
 * sheet to stay in sync with the app.
 */

const TZ = "Africa/Nairobi";
const ADMIN_TAB = "__ADMIN__";
const QUEUE_TAB = "QUEUE";
const BARBERS_TAB = "BARBERS";
const COUNTRY_CODE = "KE";

const STAFF = [
  { name: "Ng'ash", role: "admin" },
  { name: "Jay", role: "admin" },
  { name: "Esther", role: "staff" },
  { name: "Cate", role: "staff" },
];

function doPost(e) {
  try {
    const body = e && e.postData && e.postData.contents ? JSON.parse(e.postData.contents) : {};
    const action = String(body.action || "");
    const payload = body.payload || {};

    switch (action) {
      case "bootstrap":
        return ok_(bootstrap_(payload));
      case "login":
        return ok_(login_(payload));
      case "clockIn":
        return ok_(clockIn_(payload));
      case "listToday":
        return ok_(listToday_(payload));
      case "createTx":
        return ok_(createTx_(payload));
      case "updateTx":
        return ok_(updateTx_(payload));
      case "deleteTx":
        return ok_(deleteTx_(payload));
      case "adminListDay":
        return ok_(adminListDay_(payload));
      case "manageStaff":
        return ok_(manageStaff_(payload));
      case "listUsers":
        return ok_(listUsers_(payload));
      case "renameUser":
        return ok_(renameUser_(payload));
      case "listQueue":
        return ok_(listQueue_(payload));
      case "addToQueue":
        return ok_(addToQueue_(payload));
      case "startQueueService":
        return ok_(startQueueService_(payload));
      case "completeQueue":
        return ok_(completeQueue_(payload));
      case "removeFromQueue":
        return ok_(removeFromQueue_(payload));
      case "setBarberFree":
        return ok_(setBarberFree_(payload));
      default:
        return err_("Unknown action");
    }
  } catch (err) {
    return err_(String(err && err.message ? err.message : err));
  }
}

function ok_(data) {
  return ContentService
    .createTextOutput(JSON.stringify({ ok: true, data: data }))
    .setMimeType(ContentService.MimeType.JSON);
}

function err_(message) {
  return ContentService
    .createTextOutput(JSON.stringify({ ok: false, error: message }))
    .setMimeType(ContentService.MimeType.JSON);
}

function spreadsheet_() {
  return SpreadsheetApp.getActiveSpreadsheet();
}

function adminSecret_() {
  const s = PropertiesService.getScriptProperties().getProperty("ADMIN_SECRET");
  if (!s) throw new Error("Missing script property ADMIN_SECRET");
  return s;
}

function sha256Hex_(text) {
  const bytes = Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, text, Utilities.Charset.UTF_8);
  return bytes.map(b => (b + 256).toString(16).slice(-2)).join("");
}

function now_() {
  return new Date();
}

function todayKey_() {
  return Utilities.formatDate(now_(), TZ, "yyyy-MM-dd");
}

function todayTabName_() {
  return Utilities.formatDate(now_(), TZ, "EEE dd MMM yyyy");
}

function isWeekend_(d) {
  const day = Number(Utilities.formatDate(d, TZ, "u")); // 1=Mon..7=Sun
  return day === 6 || day === 7;
}

function holidayCacheKey_(year) {
  return `holidays_${COUNTRY_CODE}_${year}`;
}

function getKenyaHolidays_(year) {
  const cache = CacheService.getScriptCache();
  const key = holidayCacheKey_(year);
  const cached = cache.get(key);
  if (cached) return JSON.parse(cached);

  const url = `https://date.nager.at/api/v3/PublicHolidays/${year}/${COUNTRY_CODE}`;
  const resp = UrlFetchApp.fetch(url, { muteHttpExceptions: true });
  if (resp.getResponseCode() !== 200) {
    throw new Error(`Holiday API failed: ${resp.getResponseCode()}`);
  }
  const json = JSON.parse(resp.getContentText());
  const dates = json.map(x => x.date); // YYYY-MM-DD
  cache.put(key, JSON.stringify(dates), 6 * 60 * 60); // 6 hours
  return dates;
}

function isKenyaHolidayToday_() {
  const d = now_();
  const year = Number(Utilities.formatDate(d, TZ, "yyyy"));
  const iso = Utilities.formatDate(d, TZ, "yyyy-MM-dd");
  const dates = getKenyaHolidays_(year);
  return dates.indexOf(iso) >= 0;
}

function getStartTimeForToday_() {
  const weekend = isWeekend_(now_());
  const holiday = isKenyaHolidayToday_();
  return (weekend || holiday) ? { hh: 9, mm: 0, kind: "weekend_or_holiday" } : { hh: 8, mm: 0, kind: "weekday" };
}

function minutesSinceMidnight_(d) {
  const hh = Number(Utilities.formatDate(d, TZ, "H"));
  const mm = Number(Utilities.formatDate(d, TZ, "m"));
  return hh * 60 + mm;
}

function computeArrival_(d, startH, startM, graceMins) {
  const start = startH * 60 + startM;
  const cur = minutesSinceMidnight_(d);
  const diff = cur - start;
  if (diff <= 0) return { arrival: "EARLY", late: false, minutesLate: 0 };
  if (diff <= graceMins) return { arrival: "ON TIME", late: false, minutesLate: 0 };
  return { arrival: `LATE (${diff}m)`, late: true, minutesLate: diff };
}

function styleArrivalCell_(cell, arrivalText) {
  // Match your old sheet colors:
  // - EARLY / ON TIME: green
  // - LATE: red
  const green = "#00b050";
  const red = "#c00000";
  if (String(arrivalText).indexOf("LATE") === 0) {
    cell.setBackground(red).setFontColor("#ffffff").setFontWeight("bold");
  } else if (arrivalText === "EARLY" || arrivalText === "ON TIME") {
    cell.setBackground(green).setFontColor("#ffffff").setFontWeight("bold");
  } else {
    cell.setBackground("#ffffff").setFontColor("#000000").setFontWeight("normal");
  }
}

function ensureAdminTab_() {
  const ss = spreadsheet_();
  let sh = ss.getSheetByName(ADMIN_TAB);
  if (!sh) sh = ss.insertSheet(ADMIN_TAB);

  // Layout:
  // A1:D1 = USERS header
  // A2:F? = users table
  // Columns: name | role | pinHash | maxDevices | deviceIds (comma) | active
  if (sh.getRange("A1").getValue() !== "USERS") {
    sh.clear();
    sh.getRange("A1").setValue("USERS");
    sh.getRange("A2:F2").setValues([["name", "role", "pinHash", "maxDevices", "deviceIds", "active"]]);

    const rows = STAFF.map(u => [u.name, u.role, "", u.role === "admin" ? 2 : 1, "", true]);
    sh.getRange(3, 1, rows.length, 6).setValues(rows);

    sh.getRange("H1").setValue("SETTINGS");
    sh.getRange("H2:I2").setValues([["key", "value"]]);
    sh.getRange("H3:I9").setValues([
      ["graceMinutes", 15],
      ["closeTime", "21:00"],
      ["weekdayStart", "08:00"],
      ["weekendHolidayStart", "09:00"],
      ["payMethods", "Cash,M-pesa,Withdraw"],
      ["statuses", "Collected,Pending"],
      ["queueSheetId", ""],
    ]);
  }

  return sh;
}

function getUsers_() {
  const sh = ensureAdminTab_();
  const last = sh.getLastRow();
  if (last < 3) return [];
  const data = sh.getRange(3, 1, last - 2, 6).getValues();
  const users = [];
  data.forEach(r => {
    const name = String(r[0]).trim();
    if (!name) return;
    users.push({
      name: name,
      role: String(r[1] || "staff"),
      pinHash: String(r[2] || ""),
      maxDevices: Number(r[3] || (String(r[1]) === "admin" ? 2 : 1)),
      deviceIds: String(r[4] || ""),
      active: Boolean(r[5]),
    });
  });
  return users;
}

function staffCount_() {
  return getUsers_().length;
}

function readSettings_() {
  const sh = ensureAdminTab_();
  const values = sh.getRange("H3:I50").getValues().filter(r => String(r[0]).trim() !== "");
  const settings = {};
  values.forEach(r => settings[String(r[0])] = r[1]);
  return settings;
}

function findUserRow_(name) {
  const sh = ensureAdminTab_();
  const last = sh.getLastRow();
  const data = sh.getRange(3, 1, Math.max(0, last - 2), 6).getValues(); // start row 3
  for (let i = 0; i < data.length; i++) {
    if (String(data[i][0]).trim() === name) return { sh: sh, row: 3 + i, data: data[i] };
  }
  return null;
}

function bootstrap_(payload) {
  // Creates admin tab and returns dropdown options/settings for the app.
  ensureAdminTab_();
  const settings = readSettings_();
  return {
    todayTab: todayTabName_(),
    staff: getUsers_().map(u => ({ name: u.name, role: u.role })),
    settings: settings,
  };
}

function login_(payload) {
  const name = String(payload.name || "").trim();
  const pin = String(payload.pin || "");
  const deviceId = String(payload.deviceId || "").trim();
  if (!name || !pin || !deviceId) throw new Error("Missing name/pin/deviceId");

  const u = findUserRow_(name);
  if (!u) throw new Error("Unknown user");
  const role = String(u.data[1]);
  const pinHash = String(u.data[2] || "");
  const maxDevices = Number(u.data[3] || (role === "admin" ? 2 : 1));
  const deviceIds = String(u.data[4] || "").split(",").map(s => s.trim()).filter(Boolean);
  const active = Boolean(u.data[5]);
  if (!active) throw new Error("User is inactive");

  // Staff cannot login outside working hours; admins can login anytime.
  requireWithinHours_(role);

  // First login: if pinHash empty, set it now (staff creates their own PIN).
  const newHash = sha256Hex_(pin);
  if (!pinHash) {
    u.sh.getRange(u.row, 3).setValue(newHash);
  } else {
    if (newHash !== pinHash) throw new Error("Wrong PIN");
  }

  // Device lock:
  const unique = deviceIds.indexOf(deviceId) >= 0 ? deviceIds : deviceIds.concat([deviceId]);
  if (unique.length > maxDevices) throw new Error("Device limit reached for this user");
  u.sh.getRange(u.row, 4).setValue(maxDevices);
  u.sh.getRange(u.row, 5).setValue(unique.join(","));

  // Create session token (cache, 12 hours)
  const token = Utilities.getUuid();
  CacheService.getScriptCache().put(
    `sess_${token}`,
    JSON.stringify({ name: name, role: role, deviceId: deviceId, issuedAt: Date.now() }),
    12 * 60 * 60
  );

  return { token: token, role: role, name: name };
}

function session_(token) {
  if (!token) throw new Error("Missing token");
  const raw = CacheService.getScriptCache().get(`sess_${token}`);
  if (!raw) throw new Error("Session expired, please login again");
  return JSON.parse(raw);
}

function ensureDailyTab_() {
  const ss = spreadsheet_();
  const tab = todayTabName_();
  let sh = ss.getSheetByName(tab);
  if (!sh) sh = ss.insertSheet(tab);

  // Create layout if empty
  if (sh.getLastRow() === 0 || String(sh.getRange("A1").getValue()).trim() === "") {
    sh.clear();
    sh.getRange("A1").setValue("ATTENDANCE");
    sh.getRange("A2:D2").setValues([["NAME", "IN", "Time", "Arrival"]]);
    const rows = getUsers_().map(u => [u.name, false, "", ""]);
    sh.getRange(3, 1, rows.length, 4).setValues(rows);

    const startRow = 3 + rows.length + 2;
    sh.getRange(startRow, 1).setValue(todayTabName_());
    sh.getRange(startRow + 1, 1, 1, 10).setValues([["s/N", "NAME", "AMOUNT", "", "SERVED BY", "", "PAY METHOD", "STATUS", "MPESA TO", "last editor"]]);
    // Hidden metadata header (K..O)
    sh.getRange(startRow + 1, 11, 1, 5).setValues([["txId", "createdBy", "deviceId", "createdAt", "txType"]]);
    sh.hideColumns(11, 5);
  } else {
    // Existing tabs created before txType existed: make sure column O stays hidden
    try { sh.hideColumns(15, 1); } catch (e) {}
  }

  return sh;
}

function lastEditorText_(displayName) {
  const timestamp = Utilities.formatDate(now_(), TZ, "HH:mm");
  return `${timestamp} - ${displayName}`;
}

function requireWithinHours_(role) {
  if (role === "admin") return;
  const settings = readSettings_();
  const closeTime = String(settings.closeTime || "21:00");
  const closeParts = closeTime.split(":");
  const closeMins = Number(closeParts[0]) * 60 + Number(closeParts[1] || 0);

  const start = getStartTimeForToday_();
  // Staff allowed from 1 hour before start until close time.
  const startMins = start.hh * 60 + start.mm - 60;
  const cur = minutesSinceMidnight_(now_());
  if (cur < startMins || cur > closeMins) throw new Error("Staff access is only allowed during working hours");
}

function clockIn_(payload) {
  const token = String(payload.token || "");
  const sess = session_(token);
  requireWithinHours_(sess.role);

  const sh = ensureDailyTab_();
  const settings = readSettings_();
  const grace = Number(settings.graceMinutes || 15);
  const start = getStartTimeForToday_();

  const arrival = computeArrival_(now_(), start.hh, start.mm, grace);
  const time = Utilities.formatDate(now_(), TZ, "HH:mm");

  // Attendance list is A3:D? with names
  const last = sh.getLastRow();
  const data = sh.getRange(3, 1, Math.max(0, last - 2), 1).getValues();
  for (let i = 0; i < data.length; i++) {
    if (String(data[i][0]).trim() === sess.name) {
      sh.getRange(3 + i, 2).setValue(true);
      sh.getRange(3 + i, 3).setValue(time);
      sh.getRange(3 + i, 4).setValue(arrival.arrival);
      styleArrivalCell_(sh.getRange(3 + i, 4), arrival.arrival);
      return { ok: true, time: time, arrival: arrival.arrival, tab: todayTabName_() };
    }
  }
  throw new Error("Attendance row not found");
}

function txTableStartRow_(sh) {
  const vals = sh.getRange(1, 1, sh.getLastRow(), 1).getValues();
  for (let r = 1; r <= vals.length; r++) {
    const v = String(vals[r - 1][0]);
    if (v === todayTabName_()) return r;
  }
  // fallback: find first date-like line after attendance
  return 3 + staffCount_() + 2;
}

function readAttendance_(sh) {
  const max = Math.min(100, Math.max(0, sh.getLastRow() - 2));
  if (max <= 0) return [];
  return sh.getRange(3, 1, max, 4).getValues()
    .filter(r => String(r[0]).trim() !== "")
    .map(r => ({
      name: r[0],
      in: !!r[1],
      time: String(r[2] || ""),
      arrival: String(r[3] || ""),
    }));
}

function listToday_(payload) {
  const token = String(payload.token || "");
  const sess = session_(token);

  const sh = ensureDailyTab_();
  const tabName = todayTabName_();

  // Attendance
  const attendance = readAttendance_(sh);

  // Transactions
  const startRow = txTableStartRow_(sh);
  const headerRow = startRow + 1;
  const dataStart = headerRow + 1;
  const lastRow = sh.getLastRow();
  const rowCount = Math.max(0, lastRow - dataStart + 1);
  const rows = rowCount ? sh.getRange(dataStart, 1, rowCount, 15).getValues() : [];

  const tx = [];
  rows.forEach(r => {
    const sn = r[0];
    const name = String(r[1] || "");
    const amount = r[2];
    const serviceType = String(r[3] || "");
    const servedBy = String(r[4] || "");
    const groomedBy = String(r[5] || "");
    const payMethod = String(r[6] || "");
    const status = String(r[7] || "");
    const mpesaTo = String(r[8] || "");
    const lastEditor = String(r[9] || "");
    const txId = String(r[10] || "");
    const createdBy = String(r[11] || "");
    const deviceId = String(r[12] || "");
    const createdAt = String(r[13] || "");
    const txType = String(r[14] || "service");

    // Stop at first fully empty row (common in sheets)
    if (!name && !amount && !servedBy && !payMethod && !status && !mpesaTo) return;

    tx.push({
      txId: txId,
      sn: sn,
      clientName: name,
      amount: Number(amount || 0),
      serviceType: serviceType,
      servedBy: servedBy,
      groomedBy: groomedBy,
      payMethod: payMethod,
      status: status,
      mpesaTo: mpesaTo,
      lastEditor: lastEditor,
      createdBy: createdBy,
      deviceId: deviceId,
      createdAt: createdAt,
      txType: txType
    });
  });

  // Staff only sees today (this endpoint only returns today anyway)
  return { tabName: tabName, role: sess.role, me: sess.name, attendance: attendance, transactions: tx };
}

function createTx_(payload) {
  const token = String(payload.token || "");
  const sess = session_(token);
  requireWithinHours_(sess.role);

  const txType = String(payload.txType || "service").trim() === "sale" ? "sale" : "service";
  const clientName = String(payload.clientName || "").trim();
  const amount = Number(payload.amount || 0);
  const payMethod = String(payload.payMethod || "").trim();
  const status = String(payload.status || "").trim();
  const mpesaTo = String(payload.mpesaTo || "").trim();
  if (!clientName) throw new Error("Client name required");
  if (!amount || amount <= 0) throw new Error("Amount must be > 0");
  if (!payMethod) throw new Error("Pay method required");

  // For sales only item name + amount + client + pay method are used.
  const servedBy = txType === "sale" ? (String(payload.servedBy || "").trim() || sess.name) : String(payload.servedBy || "").trim();
  const groomedBy = txType === "sale" ? "" : String(payload.groomedBy || "").trim();
  const serviceType = txType === "sale" ? String(payload.itemName || "").trim() : String(payload.serviceType || "").trim();
  const finalStatus = txType === "sale" ? (status || "Collected") : status;
  if (txType === "sale" && !serviceType) throw new Error("Item name required");
  if (txType !== "sale" && !servedBy) throw new Error("Served by required");

  const sh = ensureDailyTab_();
  const startRow = txTableStartRow_(sh);
  const dataStart = startRow + 3; // date row + header row + start

  // Find next empty row (scan down)
  let r = dataStart;
  while (String(sh.getRange(r, 2).getValue()).trim() !== "") r++;

  // Compute s/N by counting existing
  const sn = r - dataStart + 1;
  const txId = Utilities.getUuid();
  const createdAt = Utilities.formatDate(now_(), TZ, "yyyy-MM-dd HH:mm:ss");

  // Visible columns A..J
  sh.getRange(r, 1).setValue(sn);
  sh.getRange(r, 2).setValue(clientName);
  sh.getRange(r, 3).setValue(amount);
  sh.getRange(r, 4).setValue(serviceType); // D: service type / item name
  sh.getRange(r, 5).setValue(servedBy);
  sh.getRange(r, 6).setValue(groomedBy);
  sh.getRange(r, 7).setValue(payMethod);
  sh.getRange(r, 8).setValue(finalStatus);
  sh.getRange(r, 9).setValue(mpesaTo);
  sh.getRange(r, 10).setValue(lastEditorText_(sess.name));

  // Metadata K..O (hidden)
  sh.getRange(r, 11).setValue(txId);
  sh.getRange(r, 12).setValue(sess.name); // createdBy = displayName
  sh.getRange(r, 13).setValue(sess.deviceId);
  sh.getRange(r, 14).setValue(createdAt);
  sh.getRange(r, 15).setValue(txType);

  return { txId: txId };
}

function findTxRow_(sh, txId) {
  const startRow = txTableStartRow_(sh);
  const dataStart = startRow + 3;
  const lastRow = sh.getLastRow();
  if (lastRow < dataStart) return null;
  const rowCount = lastRow - dataStart + 1;
  const ids = sh.getRange(dataStart, 11, rowCount, 1).getValues(); // column K
  for (let i = 0; i < ids.length; i++) {
    if (String(ids[i][0]) === txId) return dataStart + i;
  }
  return null;
}

function findLegacyTxRow_(sh, payload) {
  // Legacy daily tabs (or rows created before the hidden metadata columns
  // existed) have no txId. Match by clientName + amount + createdBy.
  const clientName = String(payload.clientName || "").trim();
  const amount = Number(payload.amount || 0);
  const createdBy = String(payload.createdBy || "").trim();
  if (!clientName) return null;

  const startRow = txTableStartRow_(sh);
  const dataStart = startRow + 2;
  const lastRow = sh.getLastRow();
  if (lastRow < dataStart) return null;
  const rowCount = lastRow - dataStart + 1;
  const rows = sh.getRange(dataStart, 1, rowCount, 12).getValues();
  for (let i = 0; i < rows.length; i++) {
    const r = rows[i];
    if (String(r[10] || "") !== "") continue; // has a real txId, handled elsewhere
    const name = String(r[1] || "").trim();
    const amt = Number(r[2] || 0);
    const cb = String(r[11] || "").trim();
    if (name === clientName && amt === amount && (!createdBy || cb === createdBy)) {
      return dataStart + i;
    }
  }
  return null;
}

function requireTxOwnershipToday_(sess, sh, row) {
  // Only today tab exists here; staff can update/delete only own tx and only if device matches.
  const createdBy = String(sh.getRange(row, 12).getValue()); // displayName
  const deviceId = String(sh.getRange(row, 13).getValue());
  if (sess.role !== "admin") {
    if (createdBy !== sess.name) throw new Error("Not allowed (not your transaction)");
    if (deviceId && deviceId !== sess.deviceId) throw new Error("Not allowed (wrong device)");
  }
}

function updateTx_(payload) {
  const token = String(payload.token || "");
  const sess = session_(token);
  requireWithinHours_(sess.role);

  const txId = String(payload.txId || "");
  if (!txId) throw new Error("Missing txId");
  const sh = ensureDailyTab_();
  const row = findTxRow_(sh, txId);
  if (!row) throw new Error("Transaction not found");
  requireTxOwnershipToday_(sess, sh, row);

  const clientName = String(payload.clientName || "").trim();
  const amount = Number(payload.amount || 0);
  const servedBy = String(payload.servedBy || "").trim();
  const payMethod = String(payload.payMethod || "").trim();
  const status = String(payload.status || "").trim();
  const mpesaTo = String(payload.mpesaTo || "").trim();
  const txType = String(payload.txType || "").trim();
  const serviceType = String(payload.serviceType || "").trim();
  const itemName = String(payload.itemName || "").trim();
  const groomedBy = String(payload.groomedBy || "").trim();

  if (clientName) sh.getRange(row, 2).setValue(clientName);
  if (amount && amount > 0) sh.getRange(row, 3).setValue(amount);
  if (txType) sh.getRange(row, 4).setValue(txType === "sale" ? itemName : serviceType);
  if (servedBy) sh.getRange(row, 5).setValue(servedBy);
  if (groomedBy) sh.getRange(row, 6).setValue(groomedBy);
  if (payMethod) sh.getRange(row, 7).setValue(payMethod);
  sh.getRange(row, 8).setValue(status);
  sh.getRange(row, 9).setValue(mpesaTo);
  sh.getRange(row, 10).setValue(lastEditorText_(sess.name));
  if (txType) sh.getRange(row, 15).setValue(txType);

  return { ok: true };
}

function deleteTx_(payload) {
  const token = String(payload.token || "");
  const sess = session_(token);
  requireWithinHours_(sess.role);

  const txId = String(payload.txId || "");
  if (!txId) throw new Error("Missing txId");
  const sh = ensureDailyTab_();
  let row = findTxRow_(sh, txId);
  if (!row) row = findLegacyTxRow_(sh, payload);
  if (!row) throw new Error("Transaction not found");
  requireTxOwnershipToday_(sess, sh, row);

  // Clear visible cells + metadata. We'll clear all A..N.
  sh.getRange(row, 1, 1, 14).clearContent();
  return { ok: true };
}

function adminListDay_(payload) {
  const token = String(payload.token || "");
  const sess = session_(token);
  if (sess.role !== "admin") throw new Error("Admins only");

  const dayTab = String(payload.tabName || "").trim();
  if (!dayTab) throw new Error("Missing tabName");
  const ss = spreadsheet_();
  const sh = ss.getSheetByName(dayTab);
  if (!sh) throw new Error("Day tab not found");

  // Reuse list logic but for a given tab
  const attendance = readAttendance_(sh);

  const startRow = (() => {
    const vals = sh.getRange(1, 1, sh.getLastRow(), 1).getValues();
    for (let r = 1; r <= vals.length; r++) {
      const v = String(vals[r - 1][0]);
      if (v === dayTab) return r;
    }
    return 3 + staffCount_() + 2;
  })();

  const dataStart = startRow + 3;
  const lastRow = sh.getLastRow();
  const rowCount = Math.max(0, lastRow - dataStart + 1);
  const rows = rowCount ? sh.getRange(dataStart, 1, rowCount, 15).getValues() : [];
  const tx = [];
  rows.forEach(r => {
    const name = String(r[1] || "");
    const amount = r[2];
    const serviceType = String(r[3] || "");
    const servedBy = String(r[4] || "");
    const groomedBy = String(r[5] || "");
    const payMethod = String(r[6] || "");
    const status = String(r[7] || "");
    const mpesaTo = String(r[8] || "");
    const lastEditor = String(r[9] || "");
    const txId = String(r[10] || "");
    const createdBy = String(r[11] || "");
    const deviceId = String(r[12] || "");
    const createdAt = String(r[13] || "");
    const txType = String(r[14] || "service");
    if (!name && !amount && !servedBy && !payMethod && !status && !mpesaTo) return;
    tx.push({
      txId: txId,
      clientName: name,
      amount: Number(amount || 0),
      serviceType: serviceType,
      servedBy: servedBy,
      groomedBy: groomedBy,
      payMethod: payMethod,
      status: status,
      mpesaTo: mpesaTo,
      lastEditor: lastEditor,
      createdBy: createdBy,
      deviceId: deviceId,
      createdAt: createdAt,
      txType: txType
    });
  });

  return { tabName: dayTab, role: sess.role, me: sess.name, attendance: attendance, transactions: tx };
}

// ---------------------------------------------------------------------------
// Admin: staff management
// ---------------------------------------------------------------------------

function manageStaff_(payload) {
  const token = String(payload.token || "");
  const sess = session_(token);
  if (sess.role !== "admin") throw new Error("Admins only");

  const name = String(payload.staffName || "").trim();
  const action = String(payload.staffAction || "").trim();
  if (!name || !action) throw new Error("Missing name/action");

  const u = findUserRow_(name);
  if (!u) throw new Error("User not found");

  if (action === "reset") {
    u.sh.getRange(u.row, 3).setValue(""); // clear PIN so next login sets a new one
  } else if (action === "suspend") {
    u.sh.getRange(u.row, 6).setValue(false);
  } else if (action === "recall") {
    u.sh.getRange(u.row, 6).setValue(true);
  } else {
    throw new Error("Unknown staff action");
  }
  return { ok: true };
}

function listUsers_(payload) {
  const token = String(payload.token || "");
  const sess = session_(token);
  if (sess.role !== "admin") throw new Error("Admins only");
  return { users: getUsers_() };
}

function renameUser_(payload) {
  const token = String(payload.token || "");
  const sess = session_(token);
  if (sess.role !== "admin") throw new Error("Admins only");

  const oldName = String(payload.oldName || "").trim();
  const newName = String(payload.newName || "").trim();
  if (!oldName || !newName) throw new Error("Missing names");
  if (newName === oldName) return { users: getUsers_() };

  const u = findUserRow_(oldName);
  if (!u) throw new Error("User not found");
  if (String(u.data[1]) !== "staff") throw new Error("Only non-admin users can be renamed");

  // Update the roster in __ADMIN__
  u.sh.getRange(u.row, 1).setValue(newName);

  // Update attendance names + transaction fields in every day tab
  const ss = spreadsheet_();
  const sheets = ss.getSheets();
  sheets.forEach(sh => {
    const tabName = sh.getName();
    if (tabName === ADMIN_TAB || tabName === QUEUE_TAB || tabName === BARBERS_TAB) return;
    const last = sh.getLastRow();
    if (last < 3) return;

    // Attendance column A (rows 3..)
    const attMax = Math.min(100, last - 2);
    const att = sh.getRange(3, 1, attMax, 1).getValues();
    for (let i = 0; i < att.length; i++) {
      if (String(att[i][0]).trim() === oldName) sh.getRange(3 + i, 1).setValue(newName);
    }

    // Transaction columns E(servedBy) F(groomedBy) I(mpesaTo) L(createdBy)
    const rows = sh.getRange(1, 5, last, 8).getValues(); // E..L
    for (let i = 0; i < rows.length; i++) {
      const r = rows[i];
      const row = i + 1;
      if (String(r[0]) === oldName) sh.getRange(row, 5).setValue(newName);
      if (String(r[1]) === oldName) sh.getRange(row, 6).setValue(newName);
      if (String(r[4]) === oldName) sh.getRange(row, 9).setValue(newName);
      if (String(r[7]) === oldName) sh.getRange(row, 12).setValue(newName);
    }
  });

  // Update queue barber names too (best-effort)
  try {
    const q = getQueueSheet_();
    const bsh = q.getSheetByName(BARBERS_TAB);
    if (bsh) {
      const bLast = bsh.getLastRow();
      if (bLast >= 3) {
        const names = bsh.getRange(3, 1, bLast - 2, 1).getValues();
        for (let i = 0; i < names.length; i++) {
          if (String(names[i][0]).trim() === oldName) bsh.getRange(3 + i, 1).setValue(newName);
        }
      }
    }
  } catch (e) {
    // ignore external sheet failures during rename
  }

  return { users: getUsers_() };
}

// ---------------------------------------------------------------------------
// Queue / booking (linked via a shared Google Sheet)
// ---------------------------------------------------------------------------

function getQueueSheet_() {
  const settings = readSettings_();
  const id = String(settings.queueSheetId || "").trim();
  if (id) {
    try {
      return SpreadsheetApp.openById(id);
    } catch (e) {
      // fall through to the main spreadsheet
    }
  }
  return spreadsheet_();
}

function ensureQueueTab_() {
  const ss = getQueueSheet_();
  let sh = ss.getSheetByName(QUEUE_TAB);
  if (!sh) sh = ss.insertSheet(QUEUE_TAB);
  if (sh.getLastRow() === 0 || String(sh.getRange("A1").getValue()) !== "QUEUE") {
    sh.clear();
    sh.getRange("A1").setValue("QUEUE");
    sh.getRange("A2:H2").setValues([["qId", "clientName", "service", "barber", "status", "phone", "addedAt", "note"]]);
  }
  return sh;
}

function ensureBarberTab_() {
  const ss = getQueueSheet_();
  let sh = ss.getSheetByName(BARBERS_TAB);
  if (!sh) sh = ss.insertSheet(BARBERS_TAB);
  if (sh.getLastRow() === 0 || String(sh.getRange("A1").getValue()) !== "BARBERS") {
    sh.clear();
    sh.getRange("A1").setValue("BARBERS");
    sh.getRange("A2:B2").setValues([["barber", "free"]]);
    const rows = getUsers_().filter(u => u.role === "admin").map(u => [u.name, true]);
    if (rows.length) sh.getRange(3, 1, rows.length, 2).setValues(rows);
  }
  return sh;
}

function findQueueRow_(sh, qId) {
  const last = sh.getLastRow();
  if (last < 3) return null;
  const ids = sh.getRange(3, 1, last - 2, 1).getValues();
  for (let i = 0; i < ids.length; i++) {
    if (String(ids[i][0]) === qId) return 3 + i;
  }
  return null;
}

function markBarberFree_(sh, barber, free) {
  const last = sh.getLastRow();
  if (last >= 3) {
    const names = sh.getRange(3, 1, last - 2, 1).getValues();
    for (let i = 0; i < names.length; i++) {
      if (String(names[i][0]).trim() === barber) {
        sh.getRange(3 + i, 2).setValue(free);
        return;
      }
    }
  }
  // add the barber if missing
  const r = sh.getLastRow() + 1;
  sh.getRange(r, 1).setValue(barber);
  sh.getRange(r, 2).setValue(free);
}

function listQueue_(payload) {
  const token = String(payload.token || "");
  session_(token);

  const q = ensureQueueTab_();
  const last = q.getLastRow();
  const rows = last >= 3 ? q.getRange(3, 1, last - 2, 8).getValues() : [];
  const queue = [];
  rows.forEach(r => {
    const qId = String(r[0] || "");
    const clientName = String(r[1] || "");
    if (!qId && !clientName) return;
    queue.push({
      qId: qId,
      clientName: clientName,
      service: String(r[2] || ""),
      barber: String(r[3] || ""),
      status: String(r[4] || "waiting"),
      phone: String(r[5] || ""),
      addedAt: String(r[6] || ""),
      note: String(r[7] || "")
    });
  });

  const b = ensureBarberTab_();
  const bLast = b.getLastRow();
  const bRows = bLast >= 3 ? b.getRange(3, 1, bLast - 2, 2).getValues() : [];
  const barbers = [];
  bRows.forEach(r => {
    const name = String(r[0] || "").trim();
    if (!name) return;
    barbers.push({ name: name, free: Boolean(r[1]) });
  });

  return {
    queue: queue,
    barbers: barbers,
    connected: Boolean(String(readSettings_().queueSheetId || "").trim()),
  };
}

function addToQueue_(payload) {
  const token = String(payload.token || "");
  session_(token);

  const clientName = String(payload.clientName || "").trim();
  const service = String(payload.service || "").trim();
  const phone = String(payload.phone || "").trim();
  const note = String(payload.note || "").trim();
  if (!clientName) throw new Error("Client name required");

  const q = ensureQueueTab_();
  let r = 3;
  while (String(q.getRange(r, 2).getValue()).trim() !== "") r++;

  const qId = Utilities.getUuid();
  const addedAt = Utilities.formatDate(now_(), TZ, "yyyy-MM-dd HH:mm:ss");

  q.getRange(r, 1).setValue(qId);
  q.getRange(r, 2).setValue(clientName);
  if (service) q.getRange(r, 3).setValue(service);
  q.getRange(r, 5).setValue("waiting");
  if (phone) q.getRange(r, 6).setValue(phone);
  if (note) q.getRange(r, 8).setValue(note);
  q.getRange(r, 7).setValue(addedAt);

  return { qId: qId };
}

function startQueueService_(payload) {
  const token = String(payload.token || "");
  session_(token);

  const qId = String(payload.qId || "");
  const barber = String(payload.barber || "").trim();
  if (!qId) throw new Error("Missing qId");

  const q = ensureQueueTab_();
  const row = findQueueRow_(q, qId);
  if (!row) throw new Error("Queue entry not found");

  q.getRange(row, 4).setValue(barber);
  q.getRange(row, 5).setValue("in-progress");
  if (barber) markBarberFree_(ensureBarberTab_(), barber, false);
  return { ok: true };
}

function completeQueue_(payload) {
  const token = String(payload.token || "");
  session_(token);

  const qId = String(payload.qId || "");
  if (!qId) throw new Error("Missing qId");

  const q = ensureQueueTab_();
  const row = findQueueRow_(q, qId);
  if (!row) throw new Error("Queue entry not found");

  const barber = String(q.getRange(row, 4).getValue() || "");
  q.getRange(row, 5).setValue("done");
  if (barber) markBarberFree_(ensureBarberTab_(), barber, true);
  return { ok: true };
}

function removeFromQueue_(payload) {
  const token = String(payload.token || "");
  session_(token);

  const qId = String(payload.qId || "");
  if (!qId) throw new Error("Missing qId");

  const q = ensureQueueTab_();
  const row = findQueueRow_(q, qId);
  if (!row) throw new Error("Queue entry not found");

  const barber = String(q.getRange(row, 4).getValue() || "");
  q.getRange(row, 1, 1, 8).clearContent();
  if (barber) markBarberFree_(ensureBarberTab_(), barber, true);
  return { ok: true };
}

function setBarberFree_(payload) {
  const token = String(payload.token || "");
  session_(token);

  const barber = String(payload.barber || "").trim();
  if (!barber) throw new Error("Missing barber");

  markBarberFree_(ensureBarberTab_(), barber, Boolean(payload.free));
  return { ok: true };
}
