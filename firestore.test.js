const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_101";
const BOB_UID = "bob_uid_202";
const CHARLIE_UID = "charlie_uid_303";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

test("Unauthenticated users: denied across all collections", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").doc(ALICE_UID).get());
  await assertFails(unauth.collection("beacons").where("active", "==", true).get());
  await assertFails(unauth.collection("zone_rooms").doc("3778_-12242").collection("messages").where("zoneKey", "==", "3778_-12242").where("archived", "==", false).get());
  await assertFails(unauth.collection("chats").where("participantIds", "array-contains", ALICE_UID).get());
  await assertFails(unauth.collection("reports").where("reporterId", "==", ALICE_UID).get());
});

test("Users collection: strict owner isolation, shadow field rejection, and partial update", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(10000);

  // Valid create
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alicia Luz",
      bio: "Abierta a café y charlas de diseño.",
      avatarColor: "amber",
      online: true,
      showOnline: true,
      lastSeenAt: created,
      preferredPurposes: ["FRIENDSHIP", "NETWORKING"],
      blockedUserIds: [],
      safetyAccepted: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update succeeds (including toggling showOnline / online)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      bio: "Explorando el centro hoy.",
      blockedUserIds: [CHARLIE_UID],
      online: false,
      showOnline: false,
      lastSeenAt: pastDate(1000),
      updatedAt: pastDate(1000),
    })
  );

  // Invalid online type rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      online: "yes",
      updatedAt: pastDate(800),
    })
  );

  // Shadow field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastDate(500),
    })
  );

  // Cross-user read & write rejected
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ displayName: "Hack", updatedAt: pastDate(500) }));

  // Future timestamp rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      bio: "Future",
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("Beacons: owner writes, filtered active query succeeds, unfiltered query and hidden beacon read fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(10000);

  await assertSucceeds(
    aliceDb.collection("beacons").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alicia Luz",
      purpose: "TOURISM",
      statusText: "Buscando recomendaciones de librerías cerca.",
      avatarColor: "sage",
      online: true,
      lastSeenAt: created,
      accuracyMeters: 18,
      languages: ["Español", "English"],
      zoneKey: "3778_-12242",
      zoneName: "Zona Centro",
      approxLat: 37.78125,
      approxLon: -122.42125,
      active: true,
      verifiedGoogle: true,
      radiusKm: 500,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Exact (non-grid) coordinates are rejected; grid centers are accepted
  await assertFails(
    aliceDb.collection("beacons").doc(ALICE_UID).update({ approxLat: 37.78123, approxLon: -122.42123 })
  );
  await assertFails(
    aliceDb.collection("beacons").doc(ALICE_UID).update({ approxLat: 37.78125, approxLon: -122.4213 })
  );
  await assertSucceeds(
    aliceDb.collection("beacons").doc(ALICE_UID).update({ approxLat: 37.78375, approxLon: -122.42375 })
  );

  // Out-of-bounds accuracyMeters fails
  await assertFails(
    aliceDb.collection("beacons").doc(ALICE_UID).update({
      accuracyMeters: 60000,
      updatedAt: pastDate(2500),
    })
  );

  // Expanding radius up to 25000 km (global) succeeds, exceeding 25000 km fails
  await assertSucceeds(
    aliceDb.collection("beacons").doc(ALICE_UID).update({
      radiusKm: 25000,
      online: false,
      updatedAt: pastDate(2000),
    })
  );
  await assertFails(
    aliceDb.collection("beacons").doc(ALICE_UID).update({
      radiusKm: 30000,
      updatedAt: pastDate(1500),
    })
  );

  // Bob's filtered active query succeeds
  await assertSucceeds(bobDb.collection("beacons").where("active", "==", true).get());

  // Unfiltered list query fails
  await assertFails(bobDb.collection("beacons").get());

  // Bob cannot update Alice's beacon
  await assertFails(
    bobDb.collection("beacons").doc(ALICE_UID).update({
      statusText: "Hijacked",
      updatedAt: pastDate(500),
    })
  );

  // Alice hides her beacon
  await assertSucceeds(
    aliceDb.collection("beacons").doc(ALICE_UID).update({
      active: false,
      updatedAt: pastDate(500),
    })
  );

  // Bob cannot get hidden beacon directly, while Alice still can
  await assertFails(bobDb.collection("beacons").doc(ALICE_UID).get());
  await assertSucceeds(aliceDb.collection("beacons").doc(ALICE_UID).get());
});

test("Zone room messages: filtered query succeeds, sender can soft-archive, others cannot tamper", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const zoneKey = "-3505_-7176";
  const created = pastDate(8000);

  await assertSucceeds(
    aliceDb.collection("zone_rooms").doc(zoneKey).collection("messages").doc("msg_1").set({
      zoneKey,
      senderId: ALICE_UID,
      senderName: "Alicia Luz",
      senderColor: "amber",
      purpose: "HELP",
      text: "¿Alguien sabe dónde hay un café tranquilo con enchufes?",
      archived: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Bob's rule-aligned query succeeds
  await assertSucceeds(
    bobDb.collection("zone_rooms").doc(zoneKey).collection("messages")
      .where("zoneKey", "==", zoneKey)
      .where("archived", "==", false)
      .get()
  );

  // Unfiltered list fails
  await assertFails(bobDb.collection("zone_rooms").doc(zoneKey).collection("messages").get());

  // Spoofed senderId fails
  await assertFails(
    bobDb.collection("zone_rooms").doc(zoneKey).collection("messages").doc("msg_spoof").set({
      zoneKey,
      senderId: ALICE_UID,
      senderName: "Alicia Luz",
      senderColor: "amber",
      purpose: "CASUAL",
      text: "Soy Alicia falsa",
      archived: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Bob cannot archive Alice's message
  await assertFails(
    bobDb.collection("zone_rooms").doc(zoneKey).collection("messages").doc("msg_1").update({
      archived: true,
      updatedAt: pastDate(500),
    })
  );

  // Alice can archive her own message
  await assertSucceeds(
    aliceDb.collection("zone_rooms").doc(zoneKey).collection("messages").doc("msg_1").update({
      archived: true,
      updatedAt: pastDate(500),
    })
  );
});

test("1:1 Chats & Messages: participant isolation, delete & cleanup, legacy closed reopen, and third-party denial", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const chatId = `${ALICE_UID}_${BOB_UID}`;
  const created = pastDate(10000);

  // Pre-creation existence check parity with Kotlin startOrOpenDirectChat
  await assertSucceeds(aliceDb.collection("chats").doc(chatId).get());
  await assertFails(charlieDb.collection("chats").doc(chatId).get());

  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).set({
      participantIds: [ALICE_UID, BOB_UID],
      participantNames: { [ALICE_UID]: "Alicia Luz", [BOB_UID]: "Roberto Sol" },
      participantColors: { [ALICE_UID]: "amber", [BOB_UID]: "teal" },
      initiatorId: ALICE_UID,
      purpose: "FRIENDSHIP",
      status: "pending",
      lastMessage: "¡Hola! Vi tu baliza en Zona Centro.",
      zoneName: "Zona Centro",
      createdAt: created,
      updatedAt: created,
    })
  );
  // Bob acepta la solicitud (solo quien la recibe puede)
  await assertSucceeds(bobDb.collection("chats").doc(chatId).update({ status: "active", updatedAt: pastDate(5000) }));

  // Charlie cannot read, query, or delete Alice-Bob chat
  await assertFails(charlieDb.collection("chats").doc(chatId).get());
  await assertFails(charlieDb.collection("chats").where("participantIds", "array-contains", ALICE_UID).get());
  await assertFails(charlieDb.collection("chats").doc(chatId).delete());

  // Bob queries his chats and sends a message
  await assertSucceeds(bobDb.collection("chats").where("participantIds", "array-contains", BOB_UID).get());
  await assertSucceeds(
    bobDb.collection("chats").doc(chatId).collection("messages").doc("dm_1").set({
      chatId,
      participantIds: [ALICE_UID, BOB_UID],
      senderId: BOB_UID,
      senderName: "Roberto Sol",
      text: "¡Hola Alicia! Qué gusto saludarte.",
      createdAt: pastDate(4000),
    })
  );
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("dm_2").set({
      chatId,
      participantIds: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderName: "Alicia Luz",
      text: "¡Hola Roberto!",
      createdAt: pastDate(3800),
    })
  );
  await assertSucceeds(
    bobDb.collection("chats").doc(chatId).update({
      lastMessage: "¡Hola Alicia! Qué gusto saludarte.",
      updatedAt: pastDate(3500),
    })
  );

  // Charlie cannot read or inject into chat messages
  await assertFails(
    charlieDb.collection("chats").doc(chatId).collection("messages")
      .where("participantIds", "array-contains", ALICE_UID)
      .get()
  );

  // While chat exists, Alice cannot delete Bob's message individually
  await assertFails(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("dm_1").delete()
  );

  // Legacy closed chat can be messaged and reopened seamlessly
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("chats").doc(chatId).update({
      status: "closed",
    });
  });
  await assertSucceeds(
    bobDb.collection("chats").doc(chatId).collection("messages").doc("dm_3").set({
      chatId,
      participantIds: [ALICE_UID, BOB_UID],
      senderId: BOB_UID,
      senderName: "Roberto Sol",
      text: "Reabriendo conversación",
      createdAt: pastDate(1500),
    })
  );
  await assertSucceeds(
    bobDb.collection("chats").doc(chatId).update({
      status: "active",
      lastMessage: "Reabriendo conversación",
      updatedAt: pastDate(1000),
    })
  );

  // Participant Alice can delete the conversation and clean up all messages (including Bob's), while Charlie cannot
  await assertSucceeds(aliceDb.collection("chats").doc(chatId).delete());
  await assertFails(
    charlieDb.collection("chats").doc(chatId).collection("messages").doc("dm_1").delete()
  );
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("dm_1").delete()
  );
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("dm_2").delete()
  );
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("dm_3").delete()
  );
});

test("1:1 Rich messages: image/voice/location validation and sender-only live_location updates", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const chatId = `${ALICE_UID}_${BOB_UID}`;
  const created = pastDate(10000);

  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).set({
      participantIds: [ALICE_UID, BOB_UID],
      participantNames: { [ALICE_UID]: "Alicia Luz", [BOB_UID]: "Roberto Sol" },
      participantColors: { [ALICE_UID]: "amber", [BOB_UID]: "teal" },
      initiatorId: ALICE_UID,
      purpose: "FRIENDSHIP",
      status: "pending",
      lastMessage: "Hola",
      zoneName: "Zona Abierta",
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(bobDb.collection("chats").doc(chatId).update({ status: "active", updatedAt: pastDate(9500) }));

  // Text message can be edited by sender with edited=true, cannot be edited by peer or without edited=true, and can be deleted only by sender
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("dm_txt").set({
      chatId,
      participantIds: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderName: "Alicia Luz",
      messageType: "text",
      text: "Hola Roberto 😊",
      replyToMessageId: "dm_0",
      replyToSenderName: "Roberto Sol",
      replyToPreview: "¿Cómo estás?",
      createdAt: pastDate(8000),
    })
  );
  await assertFails(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("dm_txt").update({
      text: "Editado sin bandera",
    })
  );
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("dm_txt").update({
      text: "Hola Roberto, ¡editado!",
      edited: true,
      editedAt: pastDate(1500),
      updatedAt: pastDate(1500),
    })
  );
  await assertFails(
    bobDb.collection("chats").doc(chatId).collection("messages").doc("dm_txt").update({
      text: "Secuestro",
      edited: true,
      editedAt: pastDate(1000),
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    bobDb.collection("chats").doc(chatId).collection("messages").doc("dm_txt").delete()
  );
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("dm_txt").delete()
  );

  // Voice note with empty text succeeds; voice note without mediaData fails
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("dm_voice").set({
      chatId,
      participantIds: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderName: "Alicia Luz",
      messageType: "voice",
      text: "",
      mediaData: "UklGRg==",
      mediaMimeType: "audio/mp4",
      mediaName: "nota_voz.m4a",
      durationSec: 7,
      createdAt: pastDate(7000),
      updatedAt: pastDate(7000),
    })
  );
  await assertFails(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("dm_voice_bad").set({
      chatId,
      participantIds: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderName: "Alicia Luz",
      messageType: "voice",
      text: "",
      durationSec: 5,
      createdAt: pastDate(6500),
    })
  );

  // Live location message with comment and longer address label succeeds and sender can update coordinates/liveActive, peer cannot
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("dm_live").set({
      chatId,
      participantIds: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderName: "Alicia Luz",
      messageType: "live_location",
      text: "Voy caminando hacia la entrada principal",
      latitude: -35.05123,
      longitude: -71.76234,
      locationLabel: "Estanque del Retiro\nParque del Retiro, Madrid, España 28009",
      liveActive: true,
      liveExpiresAtMs: Date.now() + 900000,
      createdAt: pastDate(6000),
      updatedAt: pastDate(6000),
    })
  );

  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("dm_live").update({
      latitude: -35.05211,
      longitude: -71.76345,
      locationLabel: "Estanque del Retiro\nParque del Retiro, Madrid, España 28009",
      liveActive: true,
      updatedAt: pastDate(2000),
    })
  );

  await assertFails(
    bobDb.collection("chats").doc(chatId).collection("messages").doc("dm_live").update({
      liveActive: false,
      updatedAt: pastDate(1000),
    })
  );
});

test("Social groups: creator isolation, member messaging, and third-party denial", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const groupId = "group_fam_01";
  const created = pastDate(10000);

  await assertSucceeds(
    aliceDb.collection("groups").doc(groupId).set({
      name: "Familia Cercana",
      category: "FAMILY",
      colorKey: "amber",
      creatorId: ALICE_UID,
      memberIds: [ALICE_UID, BOB_UID],
      memberNames: { [ALICE_UID]: "Alicia Luz", [BOB_UID]: "Roberto Sol" },
      memberColors: { [ALICE_UID]: "amber", [BOB_UID]: "teal" },
      lastMessage: "Grupo creado",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Member Bob can query, send, edit and delete own group messages (Alice cannot tamper)
  await assertSucceeds(bobDb.collection("groups").where("memberIds", "array-contains", BOB_UID).get());
  await assertSucceeds(
    bobDb.collection("groups").doc(groupId).collection("messages").doc("gm_1").set({
      groupId,
      memberIds: [ALICE_UID, BOB_UID],
      senderId: BOB_UID,
      senderName: "Roberto Sol",
      messageType: "text",
      text: "¡Hola familia!",
      createdAt: pastDate(4000),
    })
  );
  await assertSucceeds(
    bobDb.collection("groups").doc(groupId).collection("messages").doc("gm_1").update({
      text: "¡Hola familia querida!",
      edited: true,
      editedAt: pastDate(3700),
      updatedAt: pastDate(3700),
    })
  );
  await assertFails(
    aliceDb.collection("groups").doc(groupId).collection("messages").doc("gm_1").delete()
  );
  await assertSucceeds(
    bobDb.collection("groups").doc(groupId).collection("messages").doc("gm_1").delete()
  );
  await assertSucceeds(
    bobDb.collection("groups").doc(groupId).update({
      lastMessage: "¡Hola familia querida!",
      updatedAt: pastDate(3500),
    })
  );

  // Non-member Charlie cannot read or message
  await assertFails(charlieDb.collection("groups").doc(groupId).get());
  await assertFails(
    charlieDb.collection("groups").doc(groupId).collection("messages").doc("gm_bad").set({
      groupId,
      memberIds: [ALICE_UID, BOB_UID, CHARLIE_UID],
      senderId: CHARLIE_UID,
      senderName: "Carlos",
      text: "Intruso",
      createdAt: pastDate(500),
    })
  );

  // Bob cannot rename group (only creator can), while creator Alice can
  await assertFails(
    bobDb.collection("groups").doc(groupId).update({
      name: "Otro Nombre",
      updatedAt: pastDate(400),
    })
  );
  await assertSucceeds(
    aliceDb.collection("groups").doc(groupId).update({
      name: "Familia & Amigos",
      updatedAt: pastDate(300),
    })
  );
});

test("Safety reports: reporter can create and list own reports, cannot self-report or tamper", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(5000);

  await assertSucceeds(
    aliceDb.collection("reports").doc("rep_1").set({
      reporterId: ALICE_UID,
      reportedUserId: CHARLIE_UID,
      targetType: "beacon",
      targetId: CHARLIE_UID,
      reason: "spam",
      details: "Publicidad repetida en la zona.",
      createdAt: created,
    })
  );

  await assertSucceeds(aliceDb.collection("reports").where("reporterId", "==", ALICE_UID).get());
  await assertFails(bobDb.collection("reports").doc("rep_1").get());
  await assertFails(aliceDb.collection("reports").doc("rep_1").update({ reason: "other" }));
  await assertFails(
    aliceDb.collection("reports").doc("rep_self").set({
      reporterId: ALICE_UID,
      reportedUserId: ALICE_UID,
      targetType: "beacon",
      targetId: ALICE_UID,
      reason: "spam",
      details: "",
      createdAt: created,
    })
  );
});

// --- Estados, reacciones y medios troceados ---
const firebaseCompat = require("firebase/compat/app");
require("firebase/compat/firestore");
const serverNow = () => firebaseCompat.firestore.FieldValue.serverTimestamp();
const deleteField = () => firebaseCompat.firestore.FieldValue.delete();

test("Chats: recibos de lectura/entrega, escribiendo y lastSenderId solo sobre la propia entrada", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const chatId = `${ALICE_UID}_${BOB_UID}`;
  const created = pastDate(10000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("chats").doc(chatId).set({
      participantIds: [ALICE_UID, BOB_UID],
      participantNames: { [ALICE_UID]: "Alicia Luz", [BOB_UID]: "Roberto Sol" },
      participantColors: { [ALICE_UID]: "amber", [BOB_UID]: "teal" },
      initiatorId: ALICE_UID,
      purpose: "FRIENDSHIP",
      status: "active",
      lastMessage: "Hola",
      zoneName: "Zona Abierta",
      createdAt: created,
      updatedAt: created,
    });
  });
  const chatRef = (db) => db.collection("chats").doc(chatId);

  await assertSucceeds(chatRef(bobDb).update({ [`lastReadAt.${BOB_UID}`]: serverNow(), [`lastDeliveredAt.${BOB_UID}`]: serverNow() }));
  await assertSucceeds(chatRef(aliceDb).update({ [`typingAt.${ALICE_UID}`]: serverNow() }));
  await assertSucceeds(chatRef(aliceDb).update({ [`typingAt.${ALICE_UID}`]: deleteField() }));
  // Envío normal con la entrada de lectura de Bob ya presente.
  await assertSucceeds(
    chatRef(aliceDb).update({ lastMessage: "Nuevo", lastSenderId: ALICE_UID, updatedAt: serverNow(), [`typingAt.${ALICE_UID}`]: deleteField() })
  );
  // No se puede escribir la entrada de otro, ni una hora inventada, ni suplantar lastSenderId.
  await assertFails(chatRef(aliceDb).update({ [`lastReadAt.${BOB_UID}`]: serverNow() }));
  await assertFails(chatRef(aliceDb).update({ [`lastReadAt.${ALICE_UID}`]: pastDate(99999) }));
  await assertFails(chatRef(aliceDb).update({ lastSenderId: BOB_UID }));
  await assertFails(chatRef(charlieDb).update({ [`typingAt.${CHARLIE_UID}`]: serverNow() }));
});

test("Mensajes: reacciones de cualquier participante solo sobre su propia entrada", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const chatId = `${ALICE_UID}_${BOB_UID}`;
  const groupId = "group_react_01";
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("chats").doc(chatId).set({
      participantIds: [ALICE_UID, BOB_UID],
      participantNames: { [ALICE_UID]: "Alicia Luz", [BOB_UID]: "Roberto Sol" },
      participantColors: { [ALICE_UID]: "amber", [BOB_UID]: "teal" },
      initiatorId: ALICE_UID, purpose: "FRIENDSHIP", status: "active", lastMessage: "Hola",
      zoneName: "Zona Abierta", createdAt: pastDate(10000), updatedAt: pastDate(10000),
    });
    await db.collection("chats").doc(chatId).collection("messages").doc("m1").set({
      chatId, participantIds: [ALICE_UID, BOB_UID], senderId: ALICE_UID, senderName: "Alicia Luz",
      messageType: "text", text: "Hola", createdAt: pastDate(9000),
    });
    await db.collection("groups").doc(groupId).set({
      name: "Amigos", category: "FRIENDS", colorKey: "amber", creatorId: ALICE_UID,
      memberIds: [ALICE_UID, BOB_UID], memberNames: {}, memberColors: {}, lastMessage: "",
      createdAt: pastDate(10000), updatedAt: pastDate(10000),
    });
    await db.collection("groups").doc(groupId).collection("messages").doc("g1").set({
      groupId, memberIds: [ALICE_UID, BOB_UID], senderId: ALICE_UID, senderName: "Alicia Luz",
      messageType: "text", text: "Hola grupo", createdAt: pastDate(9000),
    });
  });
  const dm = (db) => db.collection("chats").doc(chatId).collection("messages").doc("m1");
  const gm = (db) => db.collection("groups").doc(groupId).collection("messages").doc("g1");

  await assertSucceeds(dm(bobDb).update({ [`reactions.${BOB_UID}`]: "❤️" }));
  await assertSucceeds(dm(aliceDb).update({ [`reactions.${ALICE_UID}`]: "😂" }));
  await assertSucceeds(dm(bobDb).update({ [`reactions.${BOB_UID}`]: deleteField() }));
  await assertFails(dm(bobDb).update({ [`reactions.${ALICE_UID}`]: deleteField() }));
  await assertFails(dm(bobDb).update({ [`reactions.${BOB_UID}`]: "x".repeat(40) }));
  await assertFails(dm(bobDb).update({ text: "hackeado", [`reactions.${BOB_UID}`]: "👍" }));
  await assertFails(dm(charlieDb).update({ [`reactions.${CHARLIE_UID}`]: "👍" }));
  await assertSucceeds(gm(bobDb).update({ [`reactions.${BOB_UID}`]: "👍" }));
  await assertFails(gm(charlieDb).update({ [`reactions.${CHARLIE_UID}`]: "👍" }));
});

test("Medios troceados: trozos solo para participantes y mensajes de voz/video sin mediaData", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const chatId = `${ALICE_UID}_${BOB_UID}`;
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("chats").doc(chatId).set({
      participantIds: [ALICE_UID, BOB_UID],
      participantNames: { [ALICE_UID]: "Alicia Luz", [BOB_UID]: "Roberto Sol" },
      participantColors: { [ALICE_UID]: "amber", [BOB_UID]: "teal" },
      initiatorId: ALICE_UID, purpose: "FRIENDSHIP", status: "active", lastMessage: "Hola",
      zoneName: "Zona Abierta", createdAt: pastDate(10000), updatedAt: pastDate(10000),
    });
  });
  const msgRef = (db) => db.collection("chats").doc(chatId).collection("messages").doc("voice_long");
  const chunk = (db, i) => msgRef(db).collection("chunks").doc(String(i));

  await assertSucceeds(chunk(aliceDb, 0).set({ index: 0, senderId: ALICE_UID, data: "QUJD" }));
  await assertSucceeds(chunk(aliceDb, 1).set({ index: 1, senderId: ALICE_UID, data: "REVG" }));
  await assertFails(chunk(aliceDb, 2).set({ index: 3, senderId: ALICE_UID, data: "REVG" }));
  await assertFails(chunk(aliceDb, 4).set({ index: 4, senderId: BOB_UID, data: "REVG" }));
  await assertFails(chunk(charlieDb, 5).set({ index: 5, senderId: CHARLIE_UID, data: "REVG" }));

  await assertSucceeds(
    msgRef(aliceDb).set({
      chatId, participantIds: [ALICE_UID, BOB_UID], senderId: ALICE_UID, senderName: "Alicia Luz",
      messageType: "voice", text: "", mediaMimeType: "audio/mp4", durationSec: 280,
      chunkCount: 2, mediaSizeBytes: 900000, createdAt: serverNow(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("video_1").set({
      chatId, participantIds: [ALICE_UID, BOB_UID], senderId: ALICE_UID, senderName: "Alicia Luz",
      messageType: "video", text: "", mediaData: "dGh1bWI=", mediaMimeType: "video/mp4", durationSec: 30,
      chunkCount: 3, mediaSizeBytes: 1400000, createdAt: serverNow(),
    })
  );
  await assertFails(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("voice_empty").set({
      chatId, participantIds: [ALICE_UID, BOB_UID], senderId: ALICE_UID, senderName: "Alicia Luz",
      messageType: "voice", text: "", createdAt: serverNow(),
    })
  );

  await assertSucceeds(chunk(bobDb, 0).get());
  await assertFails(chunk(charlieDb, 0).get());
  await assertFails(chunk(bobDb, 0).delete());
  await assertSucceeds(chunk(aliceDb, 0).delete());
});

test("Chats: contador de no leídos (+1 al otro al enviar, 0 propio al leer) y onda de voz", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const chatId = `${ALICE_UID}_${BOB_UID}`;
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("chats").doc(chatId).set({
      participantIds: [ALICE_UID, BOB_UID],
      participantNames: { [ALICE_UID]: "Alicia Luz", [BOB_UID]: "Roberto Sol" },
      participantColors: { [ALICE_UID]: "amber", [BOB_UID]: "teal" },
      initiatorId: ALICE_UID, purpose: "FRIENDSHIP", status: "active", lastMessage: "Hola",
      zoneName: "Zona Abierta", createdAt: pastDate(10000), updatedAt: pastDate(10000),
    });
  });
  const chatRef = (db) => db.collection("chats").doc(chatId);
  const inc = () => firebaseCompat.firestore.FieldValue.increment(1);

  await assertSucceeds(chatRef(aliceDb).update({ lastMessage: "1", lastSenderId: ALICE_UID, updatedAt: serverNow(), [`unreadCount.${BOB_UID}`]: inc() }));
  await assertSucceeds(chatRef(aliceDb).update({ lastMessage: "2", lastSenderId: ALICE_UID, updatedAt: serverNow(), [`unreadCount.${BOB_UID}`]: inc() }));
  await assertFails(chatRef(aliceDb).update({ [`unreadCount.${BOB_UID}`]: 50 }));
  await assertFails(chatRef(aliceDb).update({ [`unreadCount.${BOB_UID}`]: 0 }));
  await assertFails(chatRef(aliceDb).update({ [`unreadCount.${ALICE_UID}`]: 3 }));
  await assertSucceeds(chatRef(bobDb).update({ [`lastReadAt.${BOB_UID}`]: serverNow(), [`unreadCount.${BOB_UID}`]: 0 }));
  await assertFails(chatRef(bobDb).update({ [`unreadCount.${CHARLIE_UID}`]: inc() }));

  const msgs = aliceDb.collection("chats").doc(chatId).collection("messages");
  const base = {
    chatId, participantIds: [ALICE_UID, BOB_UID], senderId: ALICE_UID, senderName: "Alicia Luz",
    messageType: "voice", text: "", mediaData: "QUJD", mediaMimeType: "audio/mp4", durationSec: 4, createdAt: serverNow(),
  };
  await assertSucceeds(msgs.doc("w1").set({ ...base, waveform: "0123456789abcdefghijklmnopqrstuv0123456789abcdef" }));
  await assertFails(msgs.doc("w2").set({ ...base, waveform: "x".repeat(65) }));
  // Reenviado: solo booleano.
  await assertSucceeds(msgs.doc("f1").set({ ...base, forwarded: true }));
  await assertFails(msgs.doc("f2").set({ ...base, forwarded: "si" }));
});

test("Mensaje fijado: ambos participantes pueden fijar; validación de tamaño y claves", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("chats").doc("pin_chat").set({
      participantIds: [ALICE_UID, BOB_UID],
      participantNames: { [ALICE_UID]: "Alicia Luz", [BOB_UID]: "Roberto Sol" },
      participantColors: { [ALICE_UID]: "amber", [BOB_UID]: "teal" },
      initiatorId: ALICE_UID, purpose: "FRIENDSHIP", status: "active", lastMessage: "Hola",
      zoneName: "Zona Abierta", createdAt: pastDate(10000), updatedAt: pastDate(10000),
    });
    await context.firestore().collection("groups").doc("pin_group").set({
      name: "Grupo Pin", category: "FRIENDS", colorKey: "amber", creatorId: ALICE_UID,
      memberIds: [ALICE_UID, BOB_UID], memberNames: { [ALICE_UID]: "Alicia", [BOB_UID]: "Roberto" },
      memberColors: { [ALICE_UID]: "amber", [BOB_UID]: "teal" }, lastMessage: "",
      createdAt: pastDate(10000), updatedAt: pastDate(10000),
    });
  });
  const chat = (db) => db.collection("chats").doc("pin_chat");
  await assertSucceeds(chat(aliceDb).update({ pinnedMessageId: "m1", pinnedPreview: "Hola mundo" }));
  await assertSucceeds(chat(bobDb).update({ pinnedMessageId: "", pinnedPreview: "" }));
  await assertFails(chat(aliceDb).update({ pinnedMessageId: "x".repeat(121), pinnedPreview: "a" }));
  await assertFails(chat(aliceDb).update({ pinnedMessageId: "m1", pinnedPreview: "y".repeat(161) }));
  await assertFails(chat(aliceDb).update({ pinnedMessageId: 5, pinnedPreview: "a" }));
  await assertFails(chat(charlieDb).update({ pinnedMessageId: "m1", pinnedPreview: "a" }));

  const group = (db) => db.collection("groups").doc("pin_group");
  await assertSucceeds(group(bobDb).update({ pinnedMessageId: "g1", pinnedPreview: "Reunión viernes" }));
  await assertSucceeds(group(aliceDb).update({ pinnedMessageId: "", pinnedPreview: "" }));
  await assertFails(group(bobDb).update({ pinnedMessageId: "g1", pinnedPreview: "z".repeat(161) }));
  await assertFails(group(bobDb).update({ pinnedMessageId: "g1", name: "Hackeado" }));
  await assertFails(group(charlieDb).update({ pinnedMessageId: "g1", pinnedPreview: "a" }));
});

test("Encuestas en grupos: crear, votar solo el propio voto, opciones válidas", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("groups").doc("poll_group").set({
      name: "Grupo Encuesta", category: "FRIENDS", colorKey: "amber", creatorId: ALICE_UID,
      memberIds: [ALICE_UID, BOB_UID], memberNames: { [ALICE_UID]: "Alicia", [BOB_UID]: "Roberto" },
      memberColors: { [ALICE_UID]: "amber", [BOB_UID]: "teal" }, lastMessage: "",
      createdAt: pastDate(10000), updatedAt: pastDate(10000),
    });
  });
  const msgs = (db) => db.collection("groups").doc("poll_group").collection("messages");
  const poll = {
    groupId: "poll_group", memberIds: [ALICE_UID, BOB_UID], senderId: ALICE_UID, senderName: "Alicia Luz",
    messageType: "poll", text: "¿Dónde nos juntamos?", pollOptions: ["Plaza", "Café", "Casa"], createdAt: serverNow(),
  };
  await assertSucceeds(msgs(aliceDb).doc("p1").set(poll));
  await assertFails(msgs(aliceDb).doc("p2").set({ ...poll, pollOptions: ["Solo una"] }));
  await assertFails(msgs(aliceDb).doc("p3").set({ ...poll, pollOptions: ["a","b","c","d","e","f","g"] }));
  await assertFails(msgs(aliceDb).doc("p4").set({ ...poll, text: "" }));

  const vote = (db, uid, v) => msgs(db).doc("p1").update({ [`pollVotes.${uid}`]: v });
  await assertSucceeds(vote(bobDb, BOB_UID, 1));
  await assertSucceeds(vote(bobDb, BOB_UID, 2));
  await assertSucceeds(vote(aliceDb, ALICE_UID, 0));
  await assertFails(vote(bobDb, BOB_UID, 3));
  await assertFails(vote(bobDb, BOB_UID, -1));
  await assertFails(vote(bobDb, BOB_UID, "1"));
  await assertFails(vote(bobDb, ALICE_UID, 2));
  await assertFails(vote(charlieDb, CHARLIE_UID, 0));
  await assertSucceeds(msgs(bobDb).doc("p1").update({ [`pollVotes.${BOB_UID}`]: firebaseCompat.firestore.FieldValue.delete() }));
  await assertFails(msgs(bobDb).doc("p1").update({ pollOptions: ["x", "y"] }));
});

test("Solicitudes de chat: nace pending, solo un saludo, solo el receptor acepta o rechaza", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const chatId = "req_chat";
  const base = {
    participantIds: [ALICE_UID, BOB_UID],
    participantNames: { [ALICE_UID]: "Alicia Luz", [BOB_UID]: "Roberto Sol" },
    participantColors: { [ALICE_UID]: "amber", [BOB_UID]: "teal" },
    initiatorId: ALICE_UID, purpose: "FRIENDSHIP", lastMessage: "Hola",
    zoneName: "Zona Abierta", createdAt: pastDate(10000), updatedAt: pastDate(10000),
  };
  // No se puede crear ya activo
  await assertFails(aliceDb.collection("chats").doc(chatId).set({ ...base, status: "active" }));
  await assertSucceeds(aliceDb.collection("chats").doc(chatId).set({ ...base, status: "pending" }));
  const msg = (uid, name, text) => ({
    chatId, participantIds: [ALICE_UID, BOB_UID], senderId: uid, senderName: name, text, createdAt: pastDate(9000),
  });
  const msgs = (db) => db.collection("chats").doc(chatId).collection("messages");
  // Solo el iniciador puede enviar el saludo, con id fijo
  await assertFails(msgs(aliceDb).doc("otro").set(msg(ALICE_UID, "Alicia Luz", "Hola")));
  await assertFails(msgs(bobDb).doc("greeting").set(msg(BOB_UID, "Roberto Sol", "Hola")));
  await assertSucceeds(msgs(aliceDb).doc("greeting").set(msg(ALICE_UID, "Alicia Luz", "Hola")));
  // El iniciador no puede aceptar su propia solicitud
  await assertFails(aliceDb.collection("chats").doc(chatId).update({ status: "active", updatedAt: pastDate(8000) }));
  // El receptor rechaza; después ni puede escribirse ni reabrir el iniciador
  await assertSucceeds(bobDb.collection("chats").doc(chatId).update({ status: "declined", updatedAt: pastDate(8000) }));
  await assertFails(msgs(aliceDb).doc("m2").set(msg(ALICE_UID, "Alicia Luz", "Por favor")));
  await assertFails(aliceDb.collection("chats").doc(chatId).update({ status: "active", updatedAt: pastDate(7000) }));
  await assertFails(aliceDb.collection("chats").doc(chatId).update({ status: "pending", updatedAt: pastDate(7000) }));
  // El receptor puede cambiar de opinión
  await assertSucceeds(bobDb.collection("chats").doc(chatId).update({ status: "active", updatedAt: pastDate(6000) }));
  await assertSucceeds(msgs(aliceDb).doc("m3").set(msg(ALICE_UID, "Alicia Luz", "Gracias")));
});

test("Posición fina privada: solo el dueño o quien tiene un chat aceptado la lee", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const fine = { userId: ALICE_UID, fineLat: 37.78125, fineLon: -122.42125, updatedAt: serverNow() };
  const ref = (db) => db.collection("beaconsPrivate").doc(ALICE_UID);
  await assertSucceeds(ref(aliceDb).set(fine));
  await assertFails(ref(bobDb).set({ ...fine, userId: BOB_UID }));
  await assertFails(ref(aliceDb).set({ ...fine, fineLat: 37.78123 }));
  await assertFails(ref(aliceDb).set({ ...fine, extra: 1 }));
  // Sin chat: nadie más la lee; tampoco se puede listar
  await assertSucceeds(ref(aliceDb).get());
  await assertFails(ref(bobDb).get());
  await assertFails(ref(charlieDb).get());
  await assertFails(testEnv.authenticatedContext(BOB_UID).firestore().collection("beaconsPrivate").get());
  // Chat pendiente: aún no
  const chatId = `${ALICE_UID}_${BOB_UID}`;
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("chats").doc(chatId).set({
      participantIds: [ALICE_UID, BOB_UID],
      participantNames: { [ALICE_UID]: "Alicia Luz", [BOB_UID]: "Roberto Sol" },
      participantColors: { [ALICE_UID]: "amber", [BOB_UID]: "teal" },
      initiatorId: BOB_UID, purpose: "FRIENDSHIP", status: "pending", lastMessage: "Hola",
      zoneName: "Zona Abierta", createdAt: pastDate(10000), updatedAt: pastDate(10000),
    });
  });
  await assertFails(ref(bobDb).get());
  // Aceptado: Bob sí, Charlie no
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("chats").doc(chatId).update({ status: "active" });
  });
  await assertSucceeds(ref(bobDb).get());
  await assertFails(ref(charlieDb).get());
  // Rechazado: se pierde el acceso
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("chats").doc(chatId).update({ status: "declined" });
  });
  await assertFails(ref(bobDb).get());
});
