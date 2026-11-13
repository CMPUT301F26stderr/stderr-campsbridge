/* Saves and replaces the Halfway backend while preserving installed Firebase Auth identities. */
const fs = require('node:fs/promises');
const path = require('node:path');
const { createHash, randomUUID } = require('node:crypto');
const { createRequire } = require('node:module');
const assert = require('node:assert/strict');
const sdk = createRequire(path.resolve(__dirname, '../functions/package.json'));
const { Timestamp, GeoPoint, DocumentReference } = sdk('firebase-admin/firestore');
const digest = bytes => createHash('sha256').update(bytes).digest('hex');
const editableMetadata = metadata => Object.fromEntries(
  ['contentType', 'cacheControl', 'contentDisposition', 'contentEncoding', 'contentLanguage', 'metadata']
    .filter(key => metadata[key] !== undefined).map(key => [key, metadata[key]]),
);

// Tag every container, so a user's ordinary map cannot be confused with a typed Firestore value.
function encode(value) {
  if (value instanceof Date) value = Timestamp.fromDate(value);
  if (value instanceof Timestamp) return ['timestamp', value.seconds, value.nanoseconds];
  if (value instanceof GeoPoint) return ['geopoint', value.latitude, value.longitude];
  if (value instanceof DocumentReference) return ['reference', value.path];
  if (Buffer.isBuffer(value) || value instanceof Uint8Array) return ['bytes', Buffer.from(value).toString('base64')];
  if (typeof value === 'number' && !Number.isFinite(value)) return ['number', String(value)];
  if (value === null || ['string', 'number', 'boolean'].includes(typeof value)) return ['value', value];
  if (Array.isArray(value)) return ['array', value.map(encode)];
  assert.ok(value && Object.getPrototypeOf(value) === Object.prototype, 'Unsupported Firestore value');
  return ['map', Object.fromEntries(Object.keys(value).sort().map(key => [key, encode(value[key])]))];
}

function decode(value, db) {
  assert.ok(Array.isArray(value), 'Invalid snapshot value');
  const [type, data, extra] = value;
  switch (type) {
    case 'timestamp': return new Timestamp(data, extra);
    case 'geopoint': return new GeoPoint(data, extra);
    case 'reference': return db.doc(data);
    case 'bytes': return Buffer.from(data, 'base64');
    case 'number': assert.ok(['NaN', 'Infinity', '-Infinity'].includes(data)); return Number(data);
    case 'value': return data;
    case 'array': return data.map(item => decode(item, db));
    case 'map': return Object.fromEntries(Object.entries(data).map(([key, item]) => [key, decode(item, db)]));
    default: throw new Error('Unknown snapshot value: ' + type);
  }
}

async function documents(db) {
  const result = [];
  async function visit(collection) {
    const refs = await collection.listDocuments();
    for (let offset = 0; offset < refs.length; offset += 100) {
      const batch = refs.slice(offset, offset + 100);
      const snapshots = await db.getAll(...batch);
      for (const doc of snapshots) {
        if (doc.exists) result.push({ path: doc.ref.path, data: encode(doc.data()) });
        // listDocuments includes missing parents whose subcollections still contain data.
        for (const child of await doc.ref.listCollections()) await visit(child);
      }
    }
  }
  for (const collection of await db.listCollections()) await visit(collection);
  return result.sort((a, b) => a.path.localeCompare(b.path));
}

async function snapshot(clients, directory) {
  await fs.mkdir(path.dirname(path.resolve(directory)), { recursive: true });
  await fs.mkdir(directory, { mode: 0o700 });
  await fs.mkdir(path.join(directory, 'objects'), { mode: 0o700 });
  const records = await documents(clients.db);
  const [files] = await clients.bucket.getFiles({ prefix: 'images/' });
  const objects = [];
  for (const file of files.sort((a, b) => a.name.localeCompare(b.name))) {
    const [bytes] = await file.download();
    const [metadata] = await file.getMetadata();
    const relative = 'objects/' + String(objects.length).padStart(6, '0') + '.bin';
    await fs.writeFile(path.join(directory, relative), bytes, { mode: 0o600 });
    objects.push({ name: file.name, file: relative, sha256: digest(bytes), metadata: editableMetadata(metadata) });
  }
  const manifest = { format: 'campsbridge-halfway-snapshot', version: 1, projectId: clients.projectId,
    storageBucket: clients.storageBucket, capturedAt: new Date().toISOString(), documents: records, objects };
  // Publish the manifest last; a failed capture must never look like a restorable backup.
  await fs.writeFile(path.join(directory, 'snapshot.json'), JSON.stringify(manifest, null, 2) + '\n', { mode: 0o600, flag: 'wx' });
  return manifest;
}

async function loadSnapshot(clients, directory) {
  const manifest = JSON.parse(await fs.readFile(path.join(directory, 'snapshot.json'), 'utf8'));
  assert.equal(manifest.format, 'campsbridge-halfway-snapshot', 'Unsupported snapshot format');
  assert.equal(manifest.version, 1, 'Unsupported snapshot version');
  assert.equal(manifest.projectId, clients.projectId, 'Snapshot project differs from the target project');
  assert.equal(manifest.storageBucket, clients.storageBucket, 'Snapshot Storage bucket differs from the target bucket');
  const paths = new Set();
  const records = manifest.documents.map(record => {
    assert.ok(typeof record.path === 'string' && record.path.split('/').length % 2 === 0 && !paths.has(record.path), 'Invalid or duplicate document path');
    paths.add(record.path);
    const data = decode(record.data, clients.db);
    assert.ok(data && Object.getPrototypeOf(data) === Object.prototype, 'Document needs map data');
    return { path: record.path, data };
  });
  const names = new Set();
  const objects = [];
  for (const object of manifest.objects) {
    assert.ok(object.name.startsWith('images/') && !names.has(object.name), 'Invalid or duplicate image object');
    assert.match(object.file, /^objects\/[0-9]{6}\.bin$/);
    names.add(object.name);
    const bytes = await fs.readFile(path.join(directory, object.file));
    assert.equal(digest(bytes), object.sha256, 'Image checksum differs: ' + object.name);
    objects.push({ ...object, bytes });
  }
  return { records, objects };
}

async function clearData(clients) {
  for (const collection of await clients.db.listCollections()) await clients.db.recursiveDelete(collection);
  const [files] = await clients.bucket.getFiles({ prefix: 'images/' });
  for (const file of files) await file.delete({ ignoreNotFound: true });
}

async function writeRecords(db, records) {
  for (let offset = 0; offset < records.length; offset += 400) {
    const batch = db.batch();
    for (const record of records.slice(offset, offset + 400)) batch.set(db.doc(record.path), record.data);
    await batch.commit();
  }
}

async function verify(clients, state) {
  const actual = await documents(clients.db);
  const expected = state.records.map(record => ({ path: record.path, data: encode(record.data) }))
    .sort((a, b) => a.path.localeCompare(b.path));
  assert.deepEqual(actual, expected, 'Firestore differs after replacement; keep apps closed and retry from the saved backup');
  const [files] = await clients.bucket.getFiles({ prefix: 'images/' });
  assert.deepEqual(files.map(f => f.name).sort(), state.objects.map(f => f.name).sort(), 'Image object list differs after replacement');
  for (const object of state.objects) {
    const file = clients.bucket.file(object.name);
    assert.equal(digest((await file.download())[0]), digest(object.bytes), 'Saved image checksum differs: ' + object.name);
    const [metadata] = await file.getMetadata();
    for (const [key, value] of Object.entries(object.metadata)) assert.deepEqual(metadata[key], value, 'Saved image metadata differs: ' + key);
  }
}

async function replace(clients, state, backupDirectory) {
  assert.ok(backupDirectory, 'A pre-change backup directory is required');
  await snapshot(clients, backupDirectory);
  await clearData(clients);
  // Restore bytes first so published attachment records never point at missing uploads.
  for (const object of state.objects) await clients.bucket.file(object.name).save(object.bytes, { resumable: false, metadata: object.metadata });
  await writeRecords(clients.db, state.records);
  await verify(clients, state);
}

async function restore(clients, directory, backupDirectory) {
  const state = await loadSnapshot(clients, directory);
  await replace(clients, state, backupDirectory);
}

function stored(value, key) {
  if (key === 'location' && value && typeof value.latitude === 'number' && typeof value.longitude === 'number') return new GeoPoint(value.latitude, value.longitude);
  if (value instanceof Date || value === null || typeof value !== 'object') return value;
  if (Array.isArray(value)) return value.map(item => stored(item));
  return Object.fromEntries(Object.entries(value).map(([name, item]) => [name, stored(item, name)]));
}

async function reset(clients, plan, actors, backupDirectory) {
  for (const uid of Object.values(actors)) await clients.auth.getUser(uid);
  const records = [...plan.documents].map(([key, value]) => ({ path: key, data: stored(value) }));
  const objects = [];
  for (const photo of plan.photos) {
    const bytes = await fs.readFile(path.resolve(__dirname, '../../assets', photo.source));
    const blobId = randomUUID();
    records.push({ path: 'images/' + photo.attachmentId, data: { blobId, uploadedAt: photo.uploadedAt } });
    objects.push({ name: 'images/' + blobId + '/original', bytes,
      metadata: { contentType: 'image/jpeg', metadata: { attachmentId: photo.attachmentId, firebaseStorageDownloadTokens: randomUUID() } } });
  }
  await replace(clients, { records, objects }, backupDirectory);
}

module.exports = { snapshot, restore, reset };
