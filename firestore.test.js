const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_nececity";
const BOB_UID = "bob_nececity";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp() {
  return new Date(Date.now() - 2000);
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write any NeceCity collection", async () => {
  const db = testEnv.unauthenticatedContext().firestore();
  const ts = pastTimestamp();
  await assertFails(db.collection(`users/${ALICE_UID}/notes`).get());
  await assertFails(db.collection(`users/${ALICE_UID}/events`).get());
  await assertFails(db.collection(`users/${ALICE_UID}/alarms`).get());
  await assertFails(db.collection(`users/${ALICE_UID}/worldClocks`).get());
  await assertFails(db.collection(`users/${ALICE_UID}/calcHistory`).get());
  await assertFails(
    db.doc(`users/${ALICE_UID}/notes/n1`).set({
      userId: ALICE_UID,
      title: "Anon note",
      content: "Should fail",
      colorTag: "cyan",
      pinned: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("Cross-user access is denied across all user subcollections", async () => {
  const ts = pastTimestamp();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb.doc(`users/${BOB_UID}/notes/bob_note`).set({
      userId: BOB_UID,
      title: "Private",
      content: "Bob only",
      colorTag: "indigo",
      pinned: true,
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.doc(`users/${BOB_UID}/notes/bob_note`).get());
  await assertFails(aliceDb.collection(`users/${BOB_UID}/notes`).get());
  await assertFails(
    aliceDb.doc(`users/${BOB_UID}/alarms/spoof`).set({
      userId: ALICE_UID,
      hour: 7,
      minute: 30,
      label: "Spoof",
      enabled: true,
      repeatDays: [1, 2],
      vibrate: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("Notes: valid create, pin toggle, full edit, ghost field rejection, and delete", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const noteRef = aliceDb.doc(`users/${ALICE_UID}/notes/note_1`);
  const ts1 = pastTimestamp();

  await assertSucceeds(
    noteRef.set({
      userId: ALICE_UID,
      title: "Sprint Checklist",
      content: "Ship NeceCity cleanly",
      colorTag: "cyan",
      pinned: false,
      createdAt: ts1,
      updatedAt: ts1,
    })
  );

  // Shadow update / ghost field fails
  await assertFails(
    noteRef.update({
      title: "Updated",
      isAdmin: true,
      updatedAt: pastTimestamp(),
    })
  );

  // Valid pin toggle succeeds
  await assertSucceeds(
    noteRef.update({
      pinned: true,
      updatedAt: pastTimestamp(),
    })
  );

  // Immutable userId / createdAt change fails
  await assertFails(
    noteRef.update({
      userId: BOB_UID,
      updatedAt: pastTimestamp(),
    })
  );

  // Invalid colorTag fails
  await assertFails(
    noteRef.update({
      colorTag: "neon_pink",
      updatedAt: pastTimestamp(),
    })
  );

  await assertSucceeds(noteRef.delete());
});

test("Calendar events: valid create, update, invalid dateIso rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const eventRef = aliceDb.doc(`users/${ALICE_UID}/events/evt_1`);
  const ts1 = pastTimestamp();

  await assertSucceeds(
    eventRef.set({
      userId: ALICE_UID,
      title: "Design Sync",
      description: "Obsidian UI review",
      dateIso: "2026-11-14",
      startTimeMinutes: 600,
      endTimeMinutes: 660,
      category: "work",
      createdAt: ts1,
      updatedAt: ts1,
    })
  );

  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/events/evt_bad`).set({
      userId: ALICE_UID,
      title: "Bad Date",
      description: "",
      dateIso: "11/14/2026",
      startTimeMinutes: 600,
      endTimeMinutes: 660,
      category: "work",
      createdAt: ts1,
      updatedAt: ts1,
    })
  );

  await assertSucceeds(
    eventRef.update({
      title: "Design Sync Extended",
      endTimeMinutes: 690,
      updatedAt: pastTimestamp(),
    })
  );
});

test("Alarms: valid create, toggle, bounds validation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const alarmRef = aliceDb.doc(`users/${ALICE_UID}/alarms/alm_1`);
  const ts1 = pastTimestamp();

  await assertSucceeds(
    alarmRef.set({
      userId: ALICE_UID,
      hour: 6,
      minute: 45,
      label: "Morning Run",
      enabled: true,
      repeatDays: [1, 3, 5],
      vibrate: true,
      createdAt: ts1,
      updatedAt: ts1,
    })
  );

  await assertSucceeds(
    alarmRef.update({
      enabled: false,
      updatedAt: pastTimestamp(),
    })
  );

  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/alarms/alm_bad`).set({
      userId: ALICE_UID,
      hour: 25,
      minute: 0,
      label: "Invalid hour",
      enabled: true,
      repeatDays: [],
      vibrate: true,
      createdAt: ts1,
      updatedAt: ts1,
    })
  );
});

test("WorldClocks & CalcHistory: valid create and immutable calcHistory", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts1 = pastTimestamp();

  const clockRef = aliceDb.doc(`users/${ALICE_UID}/worldClocks/clk_tokyo`);
  await assertSucceeds(
    clockRef.set({
      userId: ALICE_UID,
      zoneId: "Asia/Tokyo",
      cityLabel: "Tokyo",
      sortOrder: 0,
      createdAt: ts1,
      updatedAt: ts1,
    })
  );

  const calcRef = aliceDb.doc(`users/${ALICE_UID}/calcHistory/calc_1`);
  await assertSucceeds(
    calcRef.set({
      userId: ALICE_UID,
      expression: "12 * (4 + 6)",
      result: "120",
      createdAt: ts1,
      updatedAt: ts1,
    })
  );

  await assertFails(
    calcRef.update({
      result: "999",
      updatedAt: pastTimestamp(),
    })
  );
});
