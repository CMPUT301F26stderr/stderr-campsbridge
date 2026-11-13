const { test, before, after, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const { mkdtemp, rm, readFile, writeFile } = require('node:fs/promises');
const { tmpdir } = require('node:os');
const path = require('node:path');
const { createRequire } = require('node:module');
const sdk = createRequire(path.resolve(__dirname, '../functions/package.json'));
const { initializeApp, deleteApp } = sdk('firebase-admin/app');
const { getFirestore, Timestamp, GeoPoint } = sdk('firebase-admin/firestore');
const { getAuth } = sdk('firebase-admin/auth');
const { getStorage } = sdk('firebase-admin/storage');
const { snapshot, restore, reset } = require('./snapshot.cjs');
const { prepare } = require('./seed.cjs');
const config = require('./config.json');
const actors = { host: 'demo-owner', camper: 'demo-camper', newcamper: 'demo-newcamper', admin: 'demo-admin' };
const anchor = new Date('2026-11-17T18:00:00-07:00');
let clients, app, work;
before(async () => {
  for (const name of ['FIRESTORE_EMULATOR_HOST', 'FIREBASE_AUTH_EMULATOR_HOST', 'FIREBASE_STORAGE_EMULATOR_HOST']) {
    assert.ok(process.env[name], 'Run with the disposable Firebase emulators: ' + name);
  }
  const projectId = process.env.GCLOUD_PROJECT || 'demo-stderr';
  assert.ok(projectId.startsWith('demo-'), 'Tests only use demo emulator projects');
  app = initializeApp({ projectId, storageBucket: projectId + '.appspot.com' }, 'halfway-tool-tests');
  clients = { projectId, storageBucket: projectId + '.appspot.com', db: getFirestore(app), auth: getAuth(app), bucket: getStorage(app).bucket() };
  work = await mkdtemp(path.join(tmpdir(), 'halfway-snapshot-test-'));
  for (const uid of Object.values(actors)) {
    try { await clients.auth.getUser(uid); } catch { await clients.auth.createUser({ uid }); }
  }
});
after(async () => { if (app) await deleteApp(app); if (work) await rm(work, { recursive: true, force: true }); });
beforeEach(async () => {
  for (const c of await clients.db.listCollections()) await clients.db.recursiveDelete(c);
  const [files] = await clients.bucket.getFiles();
  await Promise.all(files.map(f => f.delete()));
});
const folder = name => path.join(work, name);

test('snapshot and restore round-trip nested records, Firestore values and image bytes, removing later practice data', async () => {
  const values = { at: new Timestamp(1794963600, 123456789), location: new GeoPoint(52.8734, -118.0814),
    owner: clients.db.doc('accounts/' + actors.host), bytes: Buffer.from([0, 127, 255]),
    nested: { text: 'Campsbridge', values: [null, true, 7.5, 'map', { type: 'timestamp' }] }, unusual: [NaN, Infinity, -Infinity] };
  await clients.db.doc('campsites/river').set(values);
  const savedValues = (await clients.db.doc('campsites/river').get()).data();
  await clients.db.doc('accounts/' + actors.host + '/inbox/win').set({ readAt: null });
  await clients.db.doc('deleted-parent/root/children/kept').set({ text: 'subcollection without a parent document' });
  await clients.bucket.file('images/original-photo/original').save(Buffer.from('original bytes'), { metadata: { contentType: 'image/jpeg', metadata: { firebaseStorageDownloadTokens: 'original-token' } } });
  await clients.bucket.file('unrelated/object').save('keep outside image prefix');
  const beforeUsers = (await clients.auth.listUsers()).users.map(u => u.uid).sort();
  await snapshot(clients, folder('baseline'));
  await clients.db.doc('campsites/river').set({ changed: true });
  await clients.db.doc('practice/new/nested/extra').set({ remove: true });
  await clients.bucket.file('images/original-photo/original').delete();
  await clients.bucket.file('images/practice-photo/original').save('remove this upload');
  await restore(clients, folder('baseline'), folder('before-restore'));
  assert.deepEqual((await clients.db.doc('campsites/river').get()).data(), savedValues);
  assert.equal((await clients.db.doc('accounts/' + actors.host + '/inbox/win').get()).data().readAt, null);
  assert.equal((await clients.db.doc('deleted-parent/root/children/kept').get()).exists, true);
  assert.equal((await clients.db.doc('deleted-parent/root').get()).exists, false);
  assert.equal((await clients.db.doc('practice/new/nested/extra').get()).exists, false);
  assert.equal((await clients.bucket.file('images/practice-photo/original').exists())[0], false);
  assert.equal((await clients.bucket.file('images/original-photo/original').download())[0].toString(), 'original bytes');
  assert.equal((await clients.bucket.file('images/original-photo/original').getMetadata())[0].metadata.firebaseStorageDownloadTokens, 'original-token');
  assert.equal((await clients.bucket.file('unrelated/object').exists())[0], true);
  assert.deepEqual((await clients.auth.listUsers()).users.map(u => u.uid).sort(), beforeUsers);
  assert.equal((JSON.parse(await readFile(path.join(folder('before-restore'), 'snapshot.json'), 'utf8'))).documents.some(d => d.path === 'practice/new/nested/extra'), true);
});

test('invalid snapshot targets and missing or corrupt bytes fail before deleting current data', async () => {
  await clients.db.doc('practice/keep').set({ keep: true });
  await clients.bucket.file('images/photo/original').save('original bytes');
  const dir = folder('validation'); await snapshot(clients, dir);
  const manifestPath = path.join(dir, 'snapshot.json');
  const original = JSON.parse(await readFile(manifestPath, 'utf8'));
  await writeFile(manifestPath, JSON.stringify({ ...original, projectId: 'another-project' }));
  await assert.rejects(restore(clients, dir, folder('wrong-target')), /project/i);
  await writeFile(manifestPath, JSON.stringify(original));
  const bytes = path.join(dir, original.objects[0].file);
  await writeFile(bytes, 'corrupted');
  await assert.rejects(restore(clients, dir, folder('corrupt')), /checksum/i);
  await rm(bytes);
  await assert.rejects(restore(clients, dir, folder('missing')), /ENOENT|missing/i);
  assert.equal((await clients.db.doc('practice/keep').get()).exists, true);
  await assert.rejects(snapshot(clients, dir), /exist/i);
});

test('Halfway preparation keeps the frozen schema and timed relationships without Final fields or a newcomer profile', () => {
  const demo = prepare(actors, { mode: 'demo', anchor });
  const get = key => demo.documents.get(key);
  assert.equal(get('lotteries/halfway-open').registrationClosesAt.toISOString(), '2026-11-18T01:08:00.000Z');
  assert.equal(get('lotteries/halfway-open').bookingOpensAt.getTime(), get('lotteries/halfway-open').registrationClosesAt.getTime());
  assert.equal(get('lotteries/halfway-next').publishedAt, null);
  assert.equal(get('campsites/halfway-draft').publishedAt, null);
  assert.equal(get('accounts/' + actors.newcamper), undefined);
  assert.deepEqual(get('accounts/' + actors.host).roles, ['ENTRANT', 'OWNER']);
  assert.deepEqual(get('accounts/' + actors.admin).roles, ['ADMIN']);
  assert.equal(get('accounts/' + actors.host).email, 'host.demo@example.com');
  assert.equal('participantOwnerIds' in get('lotteries/halfway-open'), false);
  assert.equal('ratingSummary' in get('campsites/halfway-river'), false);
  const stay = [...demo.documents].find(([key, value]) => key.startsWith('reservations/') && value.checkInDate === '2026-12-09');
  assert.ok(stay); assert.equal(stay[1].checkOutDate, '2026-12-11'); assert.equal(stay[1].payment.amountCents, 13000);
  for (const date of ['2026-12-09', '2026-12-10']) assert.equal(get('campsites/halfway-river/calendar/' + date).reservationId, stay[0].split('/')[1]);
  assert.equal(get('campsites/halfway-river/calendar/2026-12-01').reservationId, null);
  const practice = prepare(actors, { mode: 'practice', anchor });
  assert.equal(practice.documents.get('lotteries/halfway-open').registrationClosesAt.getTime() - anchor.getTime(), config.practiceRegistrationMinutes * 60000);
  const boundary = prepare(actors, { mode: 'practice', anchor: new Date('2026-11-18T00:30:00Z') });
  assert.ok(boundary.documents.has('campsites/halfway-river/calendar/2026-12-01'));
  assert.throws(() => prepare({ ...actors, camper: actors.host }, { mode: 'demo', anchor }), /distinct/i);
  assert.throws(() => prepare(actors, { mode: 'demo', anchor: new Date(NaN) }), /anchor|date/i);
});

test('reset backs up all current practice state, recreates Halfway photos and claims, and can be undone from that backup', async () => {
  await clients.db.doc('practice/new').set({ keepInBackup: true });
  await clients.db.doc('accounts/' + actors.newcamper).set({ name: 'Practice Riley', roles: ['ENTRANT'] });
  await clients.bucket.file('images/practice/original').save('practice bytes');
  const users = (await clients.auth.listUsers()).users.map(u => u.uid).sort();
  const plan = prepare(actors, { mode: 'demo', anchor });
  await reset(clients, plan, actors, folder('before-reset'));
  assert.equal((await clients.db.doc('practice/new').get()).exists, false);
  assert.equal((await clients.db.doc('accounts/' + actors.newcamper).get()).exists, false);
  const site = (await clients.db.doc('campsites/halfway-river').get()).data();
  assert.ok(site.location instanceof GeoPoint);
  const photo = (await clients.db.doc('images/campsite_halfway-river').get()).data();
  assert.ok(photo.blobId); assert.ok((await clients.bucket.file('images/' + photo.blobId + '/original').download())[0].length > 0);
  assert.equal((await clients.bucket.file('images/practice/original').exists())[0], false);
  assert.deepEqual((await clients.auth.listUsers()).users.map(u => u.uid).sort(), users);
  await restore(clients, folder('before-reset'), folder('undo-reset'));
  assert.equal((await clients.db.doc('practice/new').get()).data().keepInBackup, true);
  assert.equal((await clients.bucket.file('images/practice/original').download())[0].toString(), 'practice bytes');
  const missingActors = { ...actors, host: 'not-an-installed-identity' };
  await assert.rejects(reset(clients, prepare(missingActors, { mode: 'demo', anchor }), missingActors, folder('invalid-actors')), /user|identity|UID/i);
  assert.equal((await clients.db.doc('practice/new').get()).exists, true);
});
