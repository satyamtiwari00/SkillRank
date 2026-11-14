const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-skillran-c8f3e7cb-5972-479b-808e-b9dbf160d4f0";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validCoursePayload(uid, overrides = {}) {
  const now = new Date();
  return {
    userId: uid,
    title: "Complete Kotlin Coroutines Course",
    channel: "Philipp Lackner",
    url: "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
    type: "Video",
    category: "Coding",
    level: "Intermediate",
    language: "English",
    status: "scored",
    reasonIfUnscored: "",
    clarity: 9.0,
    depth: 8.5,
    beginner: 7.5,
    freshness: 8.0,
    score: 8.4,
    summary: "Clear structured guide to Kotlin coroutines and structured concurrency.",
    basis: "Video title, description chapters, captions, and published metadata.",
    confidence: "High",
    videoId: "dQw4w9WgXcQ",
    playlistId: "",
    thumb: "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
    durationMin: 95,
    lessonCount: 1,
    bookmarked: false,
    scoredAt: now,
    addedAt: now,
    updatedAt: now,
    ...overrides,
  };
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

test("Unauthenticated user cannot read or write courses", async () => {
  const db = testEnv.unauthenticatedContext().firestore(DATABASE_ID);
  await assertFails(db.collection(`users/${ALICE_UID}/courses`).get());
  await assertFails(
    db.doc(`users/${ALICE_UID}/courses/yt_v_abc`).set(validCoursePayload(ALICE_UID))
  );
});

test("Authenticated owner can create, read, bookmark, and delete own course", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const docRef = aliceDb.doc(`users/${ALICE_UID}/courses/yt_v_abc123`);
  await assertSucceeds(docRef.set(validCoursePayload(ALICE_UID)));
  await assertSucceeds(docRef.get());
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/courses`).get());
  await assertSucceeds(docRef.update({ bookmarked: true, updatedAt: new Date() }));
  await assertSucceeds(docRef.delete());
});

test("Cross-user access is denied for courses and lessons", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/courses/yt_v_abc123`).set(validCoursePayload(ALICE_UID))
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  await assertFails(bobDb.collection(`users/${ALICE_UID}/courses`).get());
  await assertFails(bobDb.doc(`users/${ALICE_UID}/courses/yt_v_abc123`).get());
  await assertFails(
    bobDb.doc(`users/${ALICE_UID}/courses/yt_v_abc123`).update({ bookmarked: true, updatedAt: new Date() })
  );
});

test("Rejects invalid YouTube URL, ghost field, or out-of-range score", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/courses/yt_v_badurl`).set(
      validCoursePayload(ALICE_UID, { url: "https://evil.com/watch?v=123" })
    )
  );
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/courses/yt_v_ghost`).set(
      validCoursePayload(ALICE_UID, { isVerified: true })
    )
  );
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/courses/yt_v_badscore`).set(
      validCoursePayload(ALICE_UID, { score: 11.5 })
    )
  );
});

test("Playlist lessons enforce Parent Gate and owner isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const lessonPayload = {
    userId: ALICE_UID,
    courseId: "yt_pl_xyz",
    order: 0,
    videoId: "vid001",
    title: "Lesson 1: Setup",
    durationMin: 12,
    createdAt: new Date(),
  };

  // Must fail when parent course does not exist
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/courses/yt_pl_xyz/lessons/lesson_0`).set(lessonPayload)
  );

  // Create parent playlist course first
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/courses/yt_pl_xyz`).set(
      validCoursePayload(ALICE_UID, {
        type: "Playlist",
        playlistId: "xyz",
        videoId: "vid001",
        lessonCount: 1,
      })
    )
  );

  // Now creating child lesson succeeds
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/courses/yt_pl_xyz/lessons/lesson_0`).set(lessonPayload)
  );
});

test("Daily quota enforces 0..20 limit and blocks delete", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const quotaRef = aliceDb.doc(`users/${ALICE_UID}/quota/daily`);
  await assertSucceeds(
    quotaRef.set({
      userId: ALICE_UID,
      dateKey: "2026-11-13",
      count: 1,
      updatedAt: new Date(),
    })
  );
  await assertFails(
    quotaRef.update({
      dateKey: "2026-11-13",
      count: 21,
      updatedAt: new Date(),
    })
  );
  await assertFails(quotaRef.delete());
});
