#!/usr/bin/env node
/* Keep this entry point independent of the current Final setup, deployments and schema. */
const fs = require('node:fs/promises');
const path = require('node:path');
const { createRequire } = require('node:module');
const assert = require('node:assert/strict');
const config = require('./config.json');
const { prepare } = require('./seed.cjs');
const { snapshot, restore, reset } = require('./snapshot.cjs');
const sdk = createRequire(path.resolve(__dirname, '../functions/package.json'));

async function main(argv) {
  const [command, ...args] = argv;
  assert.ok(['snapshot', 'restore', 'reset'].includes(command),
    'Use snapshot --out PATH, restore --from PATH, or reset --actors PATH --mode practice|demo');
  const allowed = command === 'snapshot' ? ['out'] : command === 'restore' ? ['from', 'backup'] : ['actors', 'mode', 'anchor', 'backup'];
  const options = {};
  for (let i = 0; i < args.length; i += 2) {
    const key = args[i].slice(2);
    assert.ok(args[i].startsWith('--') && allowed.includes(key) && args[i + 1] && !args[i + 1].startsWith('--'), 'Invalid option: ' + args[i]);
    options[key] = args[i + 1];
  }
  if (command === 'snapshot') assert.ok(options.out, 'Use --out with a new backup directory');
  if (command === 'restore') assert.ok(options.from, 'Use --from with an existing snapshot directory');
  let actors, plan;
  if (command === 'reset') {
    assert.ok(options.actors, 'Use --actors with the installed identities JSON');
    actors = JSON.parse(await fs.readFile(options.actors, 'utf8'));
    plan = prepare(actors, { mode: options.mode || 'practice', ...(options.anchor ? { anchor: new Date(options.anchor) } : {}) });
  }
  const emulatorNames = ['FIRESTORE_EMULATOR_HOST', 'FIREBASE_AUTH_EMULATOR_HOST', 'FIREBASE_STORAGE_EMULATOR_HOST'];
  const emulated = emulatorNames.some(key => process.env[key]);
  assert.ok(!emulated || emulatorNames.every(key => process.env[key]), 'Configure all three emulators together');
  const projectId = emulated ? process.env.GCLOUD_PROJECT : config.projectId;
  assert.ok(projectId && (!emulated || projectId.startsWith('demo-')), 'Use a demo- project with the emulators');
  const storageBucket = emulated ? projectId + '.appspot.com' : config.storageBucket;
  const { initializeApp, deleteApp } = sdk('firebase-admin/app');
  const app = initializeApp({ projectId, storageBucket });
  const clients = { projectId, storageBucket, db: sdk('firebase-admin/firestore').getFirestore(app),
    bucket: sdk('firebase-admin/storage').getStorage(app).bucket(), auth: sdk('firebase-admin/auth').getAuth(app) };
  const backup = options.backup || path.join('.firebase/halfway-demo/backups', new Date().toISOString().replace(/[:.]/g, '-'));
  try {
    console.log(command + ': ' + projectId);
    if (command === 'snapshot') {
      const result = await snapshot(clients, options.out);
      console.log(`Saved ${result.documents.length} documents and ${result.objects.length} image files to ${options.out}`);
    } else {
      console.log('Pre-change backup directory: ' + backup);
      if (command === 'restore') await restore(clients, options.from, backup);
      else await reset(clients, plan, actors, backup);
      console.log('Verified replacement. Pre-change backup: ' + backup);
      if (plan) console.log('Start: ' + plan.anchor.toISOString() + '; registration closes: ' + new Date(plan.closesAt).toISOString());
    }
  } finally { await deleteApp(app); }
}

main(process.argv.slice(2)).catch(error => { console.error(error.message); process.exitCode = 1; });
