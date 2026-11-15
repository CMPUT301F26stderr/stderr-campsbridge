// Runs the production worker against the same leased emulator as the Kotlin interop contracts.
const assert = require('node:assert/strict');
const readline = require('node:readline');
const { initializeApp, deleteApp } = require('firebase-admin/app');
const { getFirestore, Timestamp } = require('firebase-admin/firestore');
const { runPendingWork } = require('../pending-work');

async function main() {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'An isolated Firestore emulator is required');
  const config = JSON.parse(process.argv[2]);
  assert.equal(config.projectId, 'demo-stderr');
  const app = initializeApp({ projectId: config.projectId });
  const db = getFirestore(app);
  db.settings({ useBigInt: true });
  const input = readline.createInterface({ input: process.stdin });
  try {
    // Warm the connection before releasing the concurrent Android operation.
    await db.doc('_draws/worker-contract-ready').get();
    process.stdout.write('READY\n');
    for await (const line of input) {
      assert.equal(line, 'START');
      await runPendingWork({ db, now: new Timestamp(config.seconds, config.nanos), newSeed: () => config.seed });
      break;
    }
  } finally {
    input.close();
    await db.terminate();
    await deleteApp(app);
  }
}

main().catch(failure => { console.error(failure); process.exitCode = 1; });
