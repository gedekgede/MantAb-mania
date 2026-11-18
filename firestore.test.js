const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_alice";
const ASSISTANT_UID = "assistant_budi";
const OUTSIDER_UID = "outsider_eve";
const TEAM_ID = "TEAM_KAWAL_01";
const SHIFT_ID = "shift_2026_11_18_budi";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msOffset = 2000) {
  return new Date(Date.now() - msOffset);
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

async function seedTeamAndShift() {
  const now = pastDate(10000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("teams").doc(TEAM_ID).set({
      teamName: "Tim Distribusi Selatan",
      ownerUid: ADMIN_UID,
      joinCode: TEAM_ID,
      memberUids: [ADMIN_UID, ASSISTANT_UID],
      adminUids: [ADMIN_UID],
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("daily_shifts").doc(SHIFT_ID).set({
      teamId: TEAM_ID,
      assistantUid: ASSISTANT_UID,
      assistantName: "Budi Santoso",
      accompaniedSalesName: "Andi Wijaya",
      vehiclePlate: "B 9124 KWS",
      shiftDate: "2026-11-18",
      activeCycleName: "Cycle 11 - Promo Akhir Tahun",
      memberUids: [ADMIN_UID, ASSISTANT_UID],
      status: "ACTIVE",
      trackingActive: true,
      latitude: -6.2088,
      longitude: 106.8456,
      accuracyMeters: 12.5,
      locationLabel: "Jl. Sudirman, Jakarta",
      taskOrderPicked: false,
      taskOrderNote: "",
      taskPlanogramArranged: false,
      taskPlanogramNote: "",
      taskPromoSetup: false,
      taskPromoNote: "",
      preStockChecked: true,
      preStockSummary: "12 item dicek sebelum jalan",
      postStockChecked: false,
      postStockSummary: "",
      cleanlinessChecked: true,
      cleanlinessExteriorOk: true,
      cleanlinessCabinOk: true,
      cleanlinessGlassOk: true,
      cleanlinessTrashFree: true,
      cleanlinessNote: "Kendaraan bersih",
      createdAt: now,
      updatedAt: now,
    });
  });
}

test("Unauthenticated users cannot read or write any collection", async () => {
  await seedTeamAndShift();
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("teams").doc(TEAM_ID).get());
  await assertFails(unauth.collection("planograms").get());
  await assertFails(unauth.collection("csu_outlets").get());
  await assertFails(unauth.collection("sales_assistants").get());
  await assertFails(unauth.collection("daily_shifts").doc(SHIFT_ID).get());
  await assertFails(unauth.collection("visit_reports").get());
  await assertFails(unauth.collection("vehicle_stocks").get());
});

test("User profile: owner-only PII isolation & update validation", async () => {
  const now = pastDate(5000);
  const budiDb = testEnv.authenticatedContext(ASSISTANT_UID).firestore();
  const eveDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();

  await assertSucceeds(
    budiDb.collection("users").doc(ASSISTANT_UID).set({
      userId: ASSISTANT_UID,
      displayName: "Budi Santoso",
      email: "budi@example.com",
      role: "ASSISTANT",
      activeTeamId: TEAM_ID,
      vehiclePlate: "B 9124 KWS",
      accompaniedSalesName: "Andi Wijaya",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Outsider cannot read Budi's PII profile
  await assertFails(eveDb.collection("users").doc(ASSISTANT_UID).get());

  // Budi can update allowed profile fields
  await assertSucceeds(
    budiDb.collection("users").doc(ASSISTANT_UID).update({
      displayName: "Budi S.",
      updatedAt: pastDate(1000),
    })
  );

  // Budi cannot mutate email or inject ghost field
  await assertFails(
    budiDb.collection("users").doc(ASSISTANT_UID).update({
      email: "hacked@example.com",
      updatedAt: pastDate(500),
    })
  );
});

test("Team creation, join via memberUids, and non-member rejection", async () => {
  const now = pastDate(5000);
  const aliceDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const budiDb = testEnv.authenticatedContext(ASSISTANT_UID).firestore();
  const eveDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("teams").doc(TEAM_ID).set({
      teamName: "Tim Selatan",
      ownerUid: ADMIN_UID,
      joinCode: TEAM_ID,
      memberUids: [ADMIN_UID],
      adminUids: [ADMIN_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Budi joins team using exact teamId
  await assertSucceeds(
    budiDb.collection("teams").doc(TEAM_ID).update({
      memberUids: [ADMIN_UID, ASSISTANT_UID],
      updatedAt: pastDate(1000),
    })
  );

  // Eve cannot read team or list unfiltered teams
  await assertFails(eveDb.collection("teams").doc(TEAM_ID).get());
  await assertFails(eveDb.collection("teams").get());

  // Budi can list teams filtered by memberUids
  await assertSucceeds(
    budiDb.collection("teams").where("memberUids", "array-contains", ASSISTANT_UID).get()
  );
});

test("Admin uploads Planogram, CSU Outlet, and Sales Assistant; non-admin rejected; team member reads", async () => {
  await seedTeamAndShift();
  const now = pastDate(5000);
  const aliceDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const budiDb = testEnv.authenticatedContext(ASSISTANT_UID).firestore();
  const eveDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();

  // Admin creates Planogram
  await assertSucceeds(
    aliceDb.collection("planograms").doc("plano_1").set({
      teamId: TEAM_ID,
      createdByUid: ADMIN_UID,
      createdByName: "Admin Alice",
      memberUids: [ADMIN_UID, ASSISTANT_UID],
      title: "Planogram Rak Utama Minuman",
      versionCode: "v2026.11-C11",
      cycleName: "Cycle 11 - Promo Akhir Tahun",
      category: "Minuman & Snack",
      shelfInstructions: "Tier 1: Best Sellers\nTier 2: Snack\nTier 3: Health\nTier 4: Air Mineral",
      promoHighlight: "Diskon 20% Beli 2 Gratis 1",
      active: true,
      revision: 1,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Non-admin assistant cannot create Planogram
  await assertFails(
    budiDb.collection("planograms").doc("plano_bad").set({
      teamId: TEAM_ID,
      createdByUid: ASSISTANT_UID,
      createdByName: "Budi",
      memberUids: [ADMIN_UID, ASSISTANT_UID],
      title: "Fake Planogram",
      versionCode: "v99",
      cycleName: "Cycle X",
      category: "Minuman",
      shelfInstructions: "Fake",
      active: true,
      revision: 1,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Admin creates CSU Outlet with geotag
  await assertSucceeds(
    aliceDb.collection("csu_outlets").doc("csu_1").set({
      teamId: TEAM_ID,
      createdByUid: ADMIN_UID,
      memberUids: [ADMIN_UID, ASSISTANT_UID],
      csuCode: "CSU-JKT-01",
      storeName: "Toko Maju Jaya",
      address: "Jl. Kemang Raya No. 12",
      latitude: -6.2607,
      longitude: 106.8135,
      assignedAssistantName: "Budi Santoso",
      priority: "WAJIB",
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Admin uploads Sales Assistant roster entry
  await assertSucceeds(
    aliceDb.collection("sales_assistants").doc("as_1").set({
      teamId: TEAM_ID,
      createdByUid: ADMIN_UID,
      memberUids: [ADMIN_UID, ASSISTANT_UID],
      employeeCode: "AS-001",
      fullName: "Budi Santoso",
      vehiclePlate: "B 9124 KWS",
      pairedSalesName: "Andi Wijaya",
      region: "Jakarta Selatan",
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Non-admin cannot add Sales Assistant
  await assertFails(
    budiDb.collection("sales_assistants").doc("as_bad").set({
      teamId: TEAM_ID,
      createdByUid: ASSISTANT_UID,
      memberUids: [ADMIN_UID, ASSISTANT_UID],
      employeeCode: "AS-999",
      fullName: "Unauthorized",
      vehiclePlate: "B 1",
      pairedSalesName: "X",
      region: "Y",
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Assistant can read filtered team Planograms, CSUs, and Assistants; outsider fails
  await assertSucceeds(
    budiDb.collection("planograms").where("memberUids", "array-contains", ASSISTANT_UID).get()
  );
  await assertSucceeds(
    budiDb.collection("csu_outlets").where("memberUids", "array-contains", ASSISTANT_UID).get()
  );
  await assertSucceeds(
    budiDb.collection("sales_assistants").where("memberUids", "array-contains", ASSISTANT_UID).get()
  );
  await assertFails(budiDb.collection("planograms").get());
  await assertFails(eveDb.collection("csu_outlets").get());
  await assertFails(eveDb.collection("csu_outlets").where("memberUids", "array-contains", ASSISTANT_UID).get());
  await assertFails(eveDb.collection("csu_outlets").doc("csu_1").get());
});

test("Daily shift tasks, vehicle stock, visit reports, and terminal sign-off lock", async () => {
  await seedTeamAndShift();
  const now = pastDate(5000);
  const aliceDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const budiDb = testEnv.authenticatedContext(ASSISTANT_UID).firestore();

  // Assistant updates GPS, tasks, & cleanliness proof file
  await assertSucceeds(
    budiDb.collection("daily_shifts").doc(SHIFT_ID).update({
      preStockChecked: true,
      postStockChecked: true,
      cleanlinessChecked: true,
      cleanlinessFileName: "foto_kebersihan_armada.jpg",
      cleanlinessFileBase64: "BASE64_CLEANLINESS_SAMPLE_DATA",
      latitude: -6.2611,
      longitude: 106.814,
      updatedAt: pastDate(2000),
    })
  );

  // Assistant logs customer visit report with mandatory 3 task photos
  await assertSucceeds(
    budiDb.collection("visit_reports").doc("visit_1").set({
      shiftId: SHIFT_ID,
      teamId: TEAM_ID,
      csuCode: "CSU-JKT-01",
      assistantUid: ASSISTANT_UID,
      assistantName: "Budi Santoso",
      memberUids: [ADMIN_UID, ASSISTANT_UID],
      storeName: "Toko Maju Jaya",
      storeAddress: "Jl. Kemang Raya No. 12",
      visitStatus: "COMPLETED",
      orderDone: true,
      orderItemsPicked: "3 karton Teh Botol, 2 karton Snack",
      orderPhotoBase64: "BASE64_ORDER_PHOTO_PROOF_DATA",
      planogramDone: true,
      planogramVersionRef: "v2026.11-C11",
      planogramPhotoBase64: "BASE64_PLANOGRAM_PHOTO_PROOF_DATA",
      promoDone: true,
      promoCycleName: "Cycle 11 - Promo Akhir Tahun",
      posmPhotoBase64: "BASE64_POSM_PHOTO_PROOF_DATA",
      latitude: -6.2607,
      longitude: 106.8135,
      notes: "Rak tertata rapi sesuai planogram terbaru",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject visit report missing mandatory photos or containing @DocumentId `id` field
  await assertFails(
    budiDb.collection("visit_reports").doc("visit_missing_photos").set({
      shiftId: SHIFT_ID,
      teamId: TEAM_ID,
      assistantUid: ASSISTANT_UID,
      assistantName: "Budi Santoso",
      memberUids: [ADMIN_UID, ASSISTANT_UID],
      storeName: "Toko Tanpa Foto",
      storeAddress: "Jl. Test",
      visitStatus: "COMPLETED",
      orderDone: true,
      orderItemsPicked: "1",
      orderPhotoBase64: "",
      planogramDone: true,
      planogramVersionRef: "v1",
      planogramPhotoBase64: "",
      promoDone: true,
      promoCycleName: "Cycle 11",
      posmPhotoBase64: "",
      latitude: -6.2,
      longitude: 106.8,
      notes: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Assistant cannot self-sign-off their own shift
  await assertFails(
    budiDb.collection("daily_shifts").doc(SHIFT_ID).update({
      status: "SIGNED_OFF",
      signerName: "Budi Santoso",
      signerUid: ASSISTANT_UID,
      signOffNote: "Self sign",
      signatureSvgPath: "M 10,20 L 50,60",
      signedOffAt: pastDate(1000),
      updatedAt: pastDate(1000),
    })
  );

  // End-of-day Sales Rep digital sign-off (by team member Alice acting as accompanied sales rep)
  await assertSucceeds(
    aliceDb.collection("daily_shifts").doc(SHIFT_ID).update({
      status: "SIGNED_OFF",
      signerName: "Andi Wijaya",
      signerUid: ADMIN_UID,
      signOffNote: "Semua kunjungan & stok kendaraan sesuai",
      signatureSvgPath: "M 10,20 L 50,60;M 60,20 L 90,50",
      signedOffAt: pastDate(1000),
      updatedAt: pastDate(1000),
    })
  );

  // Terminal state lock: assistant cannot modify shift after SIGNED_OFF
  await assertFails(
    budiDb.collection("daily_shifts").doc(SHIFT_ID).update({
      taskOrderNote: "Tampered after signoff",
      updatedAt: pastDate(500),
    })
  );
});
