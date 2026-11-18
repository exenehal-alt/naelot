const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CHARLIE_UID = "charlie_789";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

function pastDate(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
}

function validUserPayload(uid, username = "alice_dev") {
  const ts = pastDate();
  return {
    userId: uid,
    name: "Alice Chen",
    username,
    statusText: "Building CircleChat",
    avatarData: "preset:cyan",
    circleId: "friends_main",
    online: true,
    lastSeen: ts,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validCirclePayload(ownerId) {
  const ts = pastDate();
  return {
    name: "Inner Circle",
    inviteCode: "FRIENDS26",
    ownerId,
    memberUids: [ownerId],
    members: {
      [ownerId]: {
        userId: ownerId,
        name: "Alice Chen",
        username: "alice_dev",
        avatarData: "preset:cyan",
        statusText: "Online",
        online: true,
        lastSeenMillis: Date.now() - 5000,
      },
    },
    createdAt: ts,
    updatedAt: ts,
  };
}

function validConversationPayload(creatorId, participantIds) {
  const ts = pastDate();
  return {
    groupChat: participantIds.length > 2,
    groupName: participantIds.length > 2 ? "Weekend Crew" : "",
    groupAvatar: "preset:indigo",
    participantIds,
    participantNames: {
      [ALICE_UID]: "Alice Chen",
      [BOB_UID]: "Bob Rivera",
    },
    lastMessageText: "",
    lastMessageType: "text",
    lastMessageSenderId: creatorId,
    lastMessageAt: ts,
    pinnedMessageIds: [],
    typingUserIds: [],
    createdBy: creatorId,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validMessagePayload(conversationId, senderId, participantIds) {
  const ts = pastDate();
  return {
    conversationId,
    senderId,
    senderName: "Alice Chen",
    participantIds,
    type: "text",
    content: "Hey friends!",
    fontFamily: "sans",
    textColorKey: "default",
    bold: false,
    italic: false,
    underline: false,
    strikethrough: false,
    inlineCode: false,
    blockquote: false,
    reactions: {},
    seenBy: [senderId],
    deliveredTo: [senderId],
    edited: false,
    deleted: false,
    pinned: false,
    createdAt: ts,
    updatedAt: ts,
  };
}

test("Unauthenticated requests are denied across all collections", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").doc(ALICE_UID).get());
  await assertFails(unauth.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID)));
  await assertFails(unauth.collection("circles").doc("friends_main").get());
  await assertFails(unauth.collection("conversations").get());
  await assertFails(unauth.collection("calls").get());
});

test("Users collection: owner create, partial update, cross-user denial, and @DocumentId guard", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(alice.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID)));
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).update({
      statusText: "In a call",
      online: false,
      lastSeen: pastDate(1000),
      updatedAt: pastDate(1000),
    })
  );

  // Filtered query succeeds, unfiltered query fails
  await assertSucceeds(alice.collection("users").where("userId", "==", ALICE_UID).get());
  await assertFails(alice.collection("users").get());

  // Cross-user read/write fails
  await assertFails(bob.collection("users").doc(ALICE_UID).get());
  await assertFails(bob.collection("users").doc(ALICE_UID).update({ statusText: "Hacked" }));

  // Reject ghost field & future timestamp & @DocumentId 'id'
  await assertFails(
    alice.collection("users").doc("extra_doc").set({
      ...validUserPayload(ALICE_UID),
      id: "extra_doc",
    })
  );
  await assertFails(
    alice.collection("users").doc(ALICE_UID).update({
      createdAt: pastDate(100),
      updatedAt: pastDate(100),
    })
  );
  await assertFails(
    alice.collection("users").doc(ALICE_UID).update({
      updatedAt: new Date(Date.now() + 120000),
    })
  );
});

test("Circles collection: create, invite join, self member update, and isolate non-owner updates", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    alice.collection("circles").doc("friends_main").set(validCirclePayload(ALICE_UID))
  );

  // Bob joins circle by adding only himself to memberUids and members[BOB_UID]
  await assertSucceeds(
    bob.collection("circles").doc("friends_main").update({
      memberUids: [ALICE_UID, BOB_UID],
      [`members.${BOB_UID}`]: {
        userId: BOB_UID,
        name: "Bob Rivera",
        username: "bob_r",
        avatarData: "preset:emerald",
        statusText: "Ready",
        online: true,
        lastSeenMillis: Date.now() - 2000,
      },
      updatedAt: pastDate(1000),
    })
  );

  // Bob cannot overwrite Alice's member summary or change inviteCode
  await assertFails(
    bob.collection("circles").doc("friends_main").update({
      [`members.${ALICE_UID}`]: { userId: ALICE_UID, name: "Hijacked" },
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    bob.collection("circles").doc("friends_main").update({
      inviteCode: "HACKED",
      updatedAt: pastDate(500),
    })
  );

  // Filtered circle list query succeeds
  await assertSucceeds(
    bob.collection("circles").where("memberUids", "array-contains", BOB_UID).get()
  );
  await assertFails(bob.collection("circles").get());
});

test("Conversations and messages: participant scoping, rich payload, reactions/polls, and isolation", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlie = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  const convId = "dm_alice_bob";
  const participants = [ALICE_UID, BOB_UID];

  await assertSucceeds(
    alice.collection("conversations").doc(convId).set(validConversationPayload(ALICE_UID, participants))
  );

  // List queries must filter by participantIds
  await assertSucceeds(
    bob.collection("conversations").where("participantIds", "array-contains", BOB_UID).get()
  );
  await assertFails(charlie.collection("conversations").doc(convId).get());
  await assertFails(charlie.collection("conversations").get());

  // Send poll message with optional pollData & replyTo fields
  const msgRef = alice.collection("conversations").doc(convId).collection("messages").doc("msg_1");
  await assertSucceeds(
    msgRef.set({
      ...validMessagePayload(convId, ALICE_UID, participants),
      type: "poll",
      content: "Dinner tonight?",
      fontFamily: "display",
      textColorKey: "cyan",
      bold: true,
      pollData: {
        question: "Dinner tonight?",
        allowMultiple: false,
        options: [
          { id: "opt_1", text: "Ramen", voterIds: [ALICE_UID] },
          { id: "opt_2", text: "Tacos", voterIds: [] },
        ],
      },
    })
  );

  // Participant Bob reads messages using matching array-contains filter
  await assertSucceeds(
    bob
      .collection("conversations")
      .doc(convId)
      .collection("messages")
      .where("participantIds", "array-contains", BOB_UID)
      .get()
  );

  // Participant Bob votes on poll and reacts
  await assertSucceeds(
    bob.collection("conversations").doc(convId).collection("messages").doc("msg_1").update({
      reactions: { "🔥": [BOB_UID] },
      pollData: {
        question: "Dinner tonight?",
        allowMultiple: false,
        options: [
          { id: "opt_1", text: "Ramen", voterIds: [ALICE_UID] },
          { id: "opt_2", text: "Tacos", voterIds: [BOB_UID] },
        ],
      },
      seenBy: [ALICE_UID, BOB_UID],
      updatedAt: pastDate(500),
    })
  );

  // Bob CANNOT edit Alice's message content
  await assertFails(
    bob.collection("conversations").doc(convId).collection("messages").doc("msg_1").update({
      content: "Edited by Bob",
      edited: true,
      updatedAt: pastDate(400),
    })
  );

  // Alice CAN edit her own message
  await assertSucceeds(
    msgRef.update({
      content: "Dinner tonight at 8pm?",
      edited: true,
      updatedAt: pastDate(300),
    })
  );

  // Charlie cannot read or write in Alice & Bob's conversation
  await assertFails(
    charlie.collection("conversations").doc(convId).collection("messages").doc("msg_1").get()
  );
  await assertFails(
    charlie
      .collection("conversations")
      .doc(convId)
      .collection("messages")
      .doc("msg_2")
      .set(validMessagePayload(convId, CHARLIE_UID, [ALICE_UID, BOB_UID, CHARLIE_UID]))
  );
});

test("Calls collection: ringing -> active -> ended terminal state lock and participant isolation", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlie = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  const callRef = alice.collection("calls").doc("call_1");
  const ts = pastDate();

  await assertSucceeds(
    callRef.set({
      conversationId: "dm_alice_bob",
      callerId: ALICE_UID,
      callerName: "Alice Chen",
      calleeIds: [BOB_UID],
      participantIds: [ALICE_UID, BOB_UID],
      callType: "video",
      status: "ringing",
      offerSdp: "v=0...",
      answerSdp: "",
      callerMuted: false,
      callerCameraOn: true,
      screenSharing: false,
      durationSeconds: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob accepts call
  await assertSucceeds(
    bob.collection("calls").doc("call_1").update({
      status: "active",
      answerSdp: "v=0-answer...",
      updatedAt: pastDate(1000),
    })
  );

  // End call (terminal state)
  await assertSucceeds(
    callRef.update({
      status: "ended",
      durationSeconds: 45,
      updatedAt: pastDate(500),
    })
  );

  // Terminal state locking: cannot update after ended
  await assertFails(
    callRef.update({
      status: "active",
      updatedAt: pastDate(200),
    })
  );

  // Charlie cannot read call
  await assertFails(charlie.collection("calls").doc("call_1").get());
});
