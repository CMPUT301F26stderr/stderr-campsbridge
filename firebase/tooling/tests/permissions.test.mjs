import { after, before, beforeEach, test } from 'node:test';
import { readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, getDoc, getDocs, collection, collectionGroup, query, where, setDoc, updateDoc, deleteDoc, writeBatch, Timestamp, GeoPoint } from 'firebase/firestore';
import { ref, uploadBytes, getBytes, deleteObject, updateMetadata } from 'firebase/storage';

// Test the deployed Rules language, not Kotlin mocks. Every actor is an authenticated UID.
const projectId = 'demo-stderr';
let env;
const now = () => Timestamp.now();
const offset = seconds => Timestamp.fromMillis(Date.now() + seconds * 1000);
const pair = (...ids) => ids.map(id => `${id.length}:${id}`).join('');
const contexts = new Map();
function context(uid) {
  if (!contexts.has(uid)) contexts.set(uid, env.authenticatedContext(uid));
  return contexts.get(uid);
}
const db = uid => context(uid).firestore();
const store = uid => context(uid).storage();
const account = roles => ({ name: 'Camper', email: null, phone: null, roles,
  preferences: { tags: [], amenities: [] },
  settings: { notificationsEnabled: true, colorMode: 'STANDARD', fontScale: 1, controlScale: 1 },
  createdAt: now(), removal: null });
const campsite = (ownerId = 'owner', published = true) => ({ ownerId, name: 'Camp', description: 'Quiet',
  location: new GeoPoint(53, -118), locationLabel: 'Jasper', timeZone: 'America/Edmonton', maxGuests: 8,
  amenities: [], tags: [], nightlyPriceCents: 6500, rules: 'Be kind', createdAt: now(),
  publishedAt: published ? now() : null, removal: null });
const lottery = state => ({ ownerId: 'owner', title: 'May', description: 'Weekend',
  registrationOpensAt: offset(-3600), registrationClosesAt: offset(state === 'open' ? 3600 : -60),
  bookingOpensAt: offset(state === 'open' ? 3600 : -60), bookingClosesAt: offset(7200),
  winnerCount: 1, waitlistCapacity: 1, waitingCount: 0, createdAt: offset(-7200),
  publishedAt: offset(-7200), drawnAt: state === 'book' ? offset(-30) : null, cancellation: null, _revision: 0 });
const entry = (accountId, state) => ({ accountId, state, joinedAt: offset(-120), stateChangedAt: now() });
const reservationId = pair('book', 'entrant');
const reservation = () => ({ lotteryId: 'book', entrantId: 'entrant', campsiteId: 'site',
  checkInDate: '2027-05-15', checkOutDate: '2027-05-17', guestCount: 4,
  bookedCampsite: { name: 'Camp', locationLabel: 'Jasper', location: new GeoPoint(53, -118), timeZone: 'America/Edmonton' },
  acceptedTerms: { rules: 'Be kind', cancellationPolicyVersion: 'full-refund-v1', acceptedAt: now() },
  createdAt: now(), cancellation: null, payment: { amountCents: 13000, paidAt: now(), refundedAt: null } });

async function seed(records) {
  await env.withSecurityRulesDisabled(async ctx => {
    const batch = writeBatch(ctx.firestore());
    for (const [path, data] of Object.entries(records)) batch.set(doc(ctx.firestore(), path), data);
    await batch.commit();
  });
}
function event(batch, database, type, subjectType, subjectId, recipients, senderId = null, announcement = null, reservationScope = null) {
  const id = pair(type, subjectId, ...(subjectType === 'LOTTERY' && type !== 'OWNER_ANNOUNCEMENT' ? recipients : []));
  batch.set(doc(database, `notifications/${id}`), { senderId, type, subject: { type: subjectType, id: subjectId }, announcement, createdAt: now() });
  const parent = subjectType === 'LOTTERY' ? `lotteries/${subjectId}` : `reservations/${reservationScope ?? subjectId}`;
  batch.set(doc(database, `${parent}/_notificationDeliveries/${id}`), { recipientIds: recipients });
  return id;
}
function booking(uid = 'entrant', amount = 13000) {
  const database = db(uid);
  const batch = writeBatch(database);
  batch.set(doc(database, `reservations/${reservationId}`), { ...reservation(), payment: { amountCents: amount, paidAt: now(), refundedAt: null } });
  for (const night of ['2027-05-15', '2027-05-16']) batch.update(doc(database, `campsites/site/calendar/${night}`), { reservationId });
  event(batch, database, 'RESERVATION_CONFIRMED', 'RESERVATION', reservationId, ['entrant']);
  return batch.commit();
}

before(async () => {
  const [firestoreHost, firestorePort] = process.env.FIRESTORE_EMULATOR_HOST.split(':');
  const [storageHost, storagePort] = process.env.FIREBASE_STORAGE_EMULATOR_HOST.split(':');
  env = await initializeTestEnvironment({ projectId,
    firestore: { host: firestoreHost, port: Number(firestorePort), rules: await readFile(new URL('../../firestore.rules', import.meta.url), 'utf8') },
    storage: { host: storageHost, port: Number(storagePort), rules: await readFile(new URL('../../storage.rules', import.meta.url), 'utf8') },
  });
});
after(async () => { await env?.cleanup(); });
beforeEach(async () => {
  contexts.clear();
  await env.clearFirestore();
  await seed({
    'accounts/entrant': account(['ENTRANT']), 'accounts/other': account(['ENTRANT']),
    'accounts/owner': account(['ENTRANT', 'OWNER']), 'accounts/owner2': account(['OWNER']),
    'accounts/admin': account(['ADMIN']), 'campsites/site': campsite(),
    'campsites/draft': campsite('owner', false), 'lotteries/open': lottery('open'),
    'lotteries/draw': { ...lottery('draw'), waitingCount: 1 }, 'lotteries/book': lottery('book'),
    'lotteries/book/offers/site': { campsiteId: 'site', nights: ['2027-05-15', '2027-05-16'] },
    'lotteries/book/entries/entrant': entry('entrant', 'OFFERED'),
    'lotteries/draw/entries/entrant': entry('entrant', 'WAITING'),
    'campsites/site/calendar/2027-05-15': { isOffered: true, reservationId: null },
    'campsites/site/calendar/2027-05-16': { isOffered: true, reservationId: null },
  });
});

test('unauthenticated reads/writes and undeclared collections are denied', async () => {
  const anon = env.unauthenticatedContext().firestore();
  await assertFails(getDoc(doc(anon, 'accounts/entrant')));
  await assertFails(setDoc(doc(anon, 'accounts/new'), account(['ENTRANT'])));
  await assertFails(setDoc(doc(db('admin'), 'undeclared/example'), { completedAt: now() }));
});
test('own account creation is entrant-only; profile creation for another UID is denied', async () => {
  await assertSucceeds(setDoc(doc(db('new'), 'accounts/new'), account(['ENTRANT'])));
  await assertFails(setDoc(doc(db('new2'), 'accounts/new2'), account(['ADMIN'])));
  await assertFails(setDoc(doc(db('entrant'), 'accounts/another'), account(['ENTRANT'])));
});
test('self-service OWNER works; ADMIN grants, identity edits and editing others do not', async () => {
  await assertSucceeds(updateDoc(doc(db('entrant'), 'accounts/entrant'), { roles: ['ENTRANT', 'OWNER'] }));
  await assertFails(updateDoc(doc(db('entrant'), 'accounts/entrant'), { roles: ['ENTRANT', 'OWNER', 'ADMIN'] }));
  await assertFails(updateDoc(doc(db('admin'), 'accounts/other'), { roles: ['ADMIN'] }));
  await assertFails(updateDoc(doc(db('entrant'), 'accounts/other'), { name: 'Changed' }));
  await assertFails(updateDoc(doc(db('entrant'), 'accounts/entrant'), { createdAt: offset(-9000) }));
});
test('devices and private inboxes are restricted to their account/admin', async () => {
  await assertSucceeds(setDoc(doc(db('entrant'), 'accounts/entrant/devices/phone'), { fcmToken: 'token', updatedAt: now() }));
  await assertFails(getDoc(doc(db('other'), 'accounts/entrant/devices/phone')));
  await assertSucceeds(getDoc(doc(db('admin'), 'accounts/entrant/devices/phone')));
  await assertFails(setDoc(doc(db('other'), 'accounts/entrant/inbox/fake'), { readAt: null }));
});
test('published listing query succeeds; drafts and unfiltered scans remain private', async () => {
  await assertSucceeds(getDocs(query(collection(db('entrant'), 'campsites'), where('publishedAt', '!=', null))));
  await assertFails(getDoc(doc(db('entrant'), 'campsites/draft')));
  await assertSucceeds(getDoc(doc(db('owner'), 'campsites/draft')));
  await assertFails(getDocs(collection(db('entrant'), 'campsites')));
  await assertSucceeds(getDocs(collection(db('admin'), 'campsites')));
});
test('only owner creates/edits listings; admin moderation cannot rewrite listing details', async () => {
  await assertSucceeds(setDoc(doc(db('owner'), 'campsites/new'), campsite('owner', false)));
  await assertFails(setDoc(doc(db('entrant'), 'campsites/new2'), campsite('entrant', false)));
  await assertSucceeds(updateDoc(doc(db('owner'), 'campsites/site'), { name: 'New name' }));
  await assertFails(updateDoc(doc(db('owner2'), 'campsites/site'), { name: 'Changed' }));
  await assertFails(updateDoc(doc(db('admin'), 'campsites/site'), { name: 'Changed' }));
  await assertFails(updateDoc(doc(db('owner'), 'campsites/site'), { ownerId: 'owner2' }));
  await assertSucceeds(updateDoc(doc(db('admin'), 'campsites/site'), { removal: { at: now(), actorId: 'admin', reason: 'Moderation' } }));
});
test('lottery owner configuration is frozen after registration opens', async () => {
  await assertSucceeds(updateDoc(doc(db('owner'), 'lotteries/open'), { title: 'Updated', _revision: 1 }));
  await assertFails(updateDoc(doc(db('owner'), 'lotteries/open'), { winnerCount: 20 }));
  await assertFails(updateDoc(doc(db('owner2'), 'lotteries/open'), { title: 'Changed' }));
  await assertFails(setDoc(doc(db('owner'), 'lotteries/open/offers/site'), { campsiteId: 'site', nights: ['2027-05-15'] }));
});
test('join requires own entry plus atomic count; a full waitlist rejects another join', async () => {
  const database = db('entrant');
  await assertFails(setDoc(doc(database, 'lotteries/open/entries/entrant'), entry('entrant', 'WAITING')));
  const b = writeBatch(database);
  b.set(doc(database, 'lotteries/open/entries/entrant'), entry('entrant', 'WAITING'));
  b.update(doc(database, 'lotteries/open'), { waitingCount: 1 });
  await assertSucceeds(b.commit());
  const full = writeBatch(db('other'));
  full.set(doc(db('other'), 'lotteries/open/entries/other'), entry('other', 'WAITING'));
  full.update(doc(db('other'), 'lotteries/open'), { waitingCount: 2 });
  await assertFails(full.commit());
  await assertFails(updateDoc(doc(database, 'lotteries/open'), { waitingCount: 0 }));
});
test('entrants cannot read other entries or choose their own winning outcome', async () => {
  await assertFails(getDoc(doc(db('other'), 'lotteries/book/entries/entrant')));
  await assertSucceeds(getDoc(doc(db('owner'), 'lotteries/book/entries/entrant')));
  await assertFails(updateDoc(doc(db('entrant'), 'lotteries/draw/entries/entrant'), { state: 'OFFERED' }));
  await assertFails(setDoc(doc(db('entrant'), '_draws/draw'), { seed: '1' }));
  await assertFails(setDoc(doc(db('owner'), '_draws/open'), { seed: '1' }));
});
test('owner draw seed/outcome/completion and delayed receipt work; seed is immutable', async () => {
  const database = db('owner');
  await assertSucceeds(setDoc(doc(database, '_draws/draw'), { seed: '1' }));
  await assertFails(updateDoc(doc(database, '_draws/draw'), { seed: '2' }));
  const b = writeBatch(database);
  b.update(doc(database, 'lotteries/draw/entries/entrant'), { state: 'OFFERED', stateChangedAt: now() });
  b.update(doc(database, 'lotteries/draw'), { waitingCount: 0 });
  const id = event(b, database, 'LOTTERY_WON', 'LOTTERY', 'draw', ['entrant']);
  await assertSucceeds(b.commit());
  await assertFails(setDoc(doc(database, `accounts/entrant/inbox/${id}`), { readAt: null }));
  await assertSucceeds(updateDoc(doc(database, 'lotteries/draw'), { drawnAt: now() }));
  await assertSucceeds(setDoc(doc(database, `accounts/entrant/inbox/${id}`), { readAt: null }));
});
test('coordinated winning booking succeeds; outsider booking, duplicate booking and payment edits fail', async () => {
  await assertFails(booking('other'));
  await assertFails(booking('entrant', 1));
  await assertSucceeds(booking());
  await assertFails(booking());
  await assertFails(updateDoc(doc(db('entrant'), `reservations/${reservationId}`), { 'payment.amountCents': 1 }));
  await assertFails(getDoc(doc(db('other'), `reservations/${reservationId}`)));
  await assertSucceeds(getDoc(doc(db('owner'), `reservations/${reservationId}`)));
});
test('cancellation requires matching refund, releases nights and retains booking identity', async () => {
  await assertSucceeds(booking());
  const cancellation = { at: now(), actorId: 'entrant', initiatedAs: 'ENTRANT', reason: null };
  await assertFails(updateDoc(doc(db('entrant'), `reservations/${reservationId}`), { cancellation }));
  const database = db('entrant');
  const b = writeBatch(database);
  b.update(doc(database, `reservations/${reservationId}`), { cancellation, 'payment.refundedAt': now() });
  for (const night of ['2027-05-15', '2027-05-16']) b.update(doc(database, `campsites/site/calendar/${night}`), { reservationId: null });
  event(b, database, 'RESERVATION_CANCELLED', 'RESERVATION', reservationId, ['entrant']);
  await assertSucceeds(b.commit());
  await assertFails(deleteDoc(doc(database, `reservations/${reservationId}`)));
  await assertFails(booking());
});
test('message participant and immutable content rules apply; only recipient marks read', async () => {
  await seed({ [`reservations/${reservationId}`]: reservation() });
  const message = { reservationId, senderId: 'owner', body: 'Welcome', createdAt: now(), readAt: null };
  await assertFails(setDoc(doc(db('other'), 'messages/msg'), { ...message, senderId: 'other' }));
  await assertSucceeds(setDoc(doc(db('owner'), 'messages/msg'), message));
  await assertFails(updateDoc(doc(db('owner'), 'messages/msg'), { readAt: now() }));
  await assertSucceeds(updateDoc(doc(db('entrant'), 'messages/msg'), { readAt: now() }));
  await assertFails(updateDoc(doc(db('owner'), 'messages/msg'), { body: 'Changed' }));
  await assertSucceeds(getDoc(doc(db('admin'), 'messages/msg')));
});
test('reviews/replies/reactions are attributed to the authenticated actor', async () => {
  const review = { rating: 5, body: null, createdAt: now(), updatedAt: now() };
  await assertFails(setDoc(doc(db('other'), 'campsites/site/reviews/entrant'), review));
  await assertSucceeds(setDoc(doc(db('entrant'), 'campsites/site/reviews/entrant'), review));
  await assertFails(setDoc(doc(db('entrant'), 'campsites/site/reviews/entrant'), { ...review, rating: 6 }));
  const reply = { authorId: 'owner', parentReplyId: null, body: 'Thanks', createdAt: now() };
  await assertSucceeds(setDoc(doc(db('owner'), 'campsites/site/reviews/entrant/replies/reply'), reply));
  await assertFails(setDoc(doc(db('other'), 'campsites/site/reviews/entrant/replies/fake'), reply));
  await assertSucceeds(setDoc(doc(db('other'), 'campsites/site/reviews/entrant/reactions/other'), { type: 'LIKE' }));
});
test('removed account cannot write or recreate itself; admin removal scrubs profile', async () => {
  await assertSucceeds(updateDoc(doc(db('admin'), 'accounts/other'), {
    name: '', email: null, phone: null, roles: [], preferences: { tags: [], amenities: [] },
    settings: { notificationsEnabled: false, colorMode: 'STANDARD', fontScale: 1, controlScale: 1 },
    removal: { at: now(), actorId: 'admin', reason: 'Moderation' },
  }));
  await assertFails(updateDoc(doc(db('other'), 'accounts/other'), { name: 'Back', removal: null, roles: ['ENTRANT'] }));
  await assertFails(setDoc(doc(db('other'), 'campsites/site/reviews/other'), { rating: 5, body: null, createdAt: now(), updatedAt: now() }));
});
test('upload intent and Storage ownership work; missing intent, wrong target, non-image and overwrites fail', async () => {
  const blob = 'test-upload';
  await assertSucceeds(setDoc(doc(db('entrant'), `_imageUploads/${blob}`), {
    attachmentId: 'account_entrant', expiresAt: offset(900), abandoned: false, uploadFinished: false,
  }));
  const bytes = new Uint8Array([137, 80, 78, 71]);
  const metadata = { contentType: 'image/png', customMetadata: { attachmentId: 'account_entrant' } };
  await assertFails(uploadBytes(ref(store('other'), `images/${blob}/original`), bytes, metadata));
  await assertFails(uploadBytes(ref(store('entrant'), 'images/no-intent/original'), bytes, metadata));
  await assertFails(uploadBytes(ref(store('entrant'), `images/${blob}/original`), bytes, { ...metadata, contentType: 'text/plain' }));
  await assertSucceeds(uploadBytes(ref(store('entrant'), `images/${blob}/original`), bytes, metadata));
  assert.equal((await assertSucceeds(getBytes(ref(store('other'), `images/${blob}/original`)))).byteLength, 4);
  await assertFails(updateMetadata(ref(store('entrant'), `images/${blob}/original`), { customMetadata: { attachmentId: 'account_other' } }));
  await assertFails(deleteObject(ref(store('other'), `images/${blob}/original`)));
  await assertSucceeds(deleteObject(ref(store('admin'), `images/${blob}/original`)));
});

test('leave and rejoin update entry and count together', async () => {
  await seed({ 'lotteries/open': { ...lottery('open'), waitingCount: 1 },
    'lotteries/open/entries/entrant': entry('entrant', 'WAITING') });
  const database = db('entrant');
  const leave = writeBatch(database);
  leave.update(doc(database, 'lotteries/open/entries/entrant'), { state: 'WITHDRAWN', stateChangedAt: now() });
  leave.update(doc(database, 'lotteries/open'), { waitingCount: 0 });
  await assertSucceeds(leave.commit());
  const join = writeBatch(database);
  join.update(doc(database, 'lotteries/open/entries/entrant'), { state: 'WAITING', joinedAt: now(), stateChangedAt: now() });
  join.update(doc(database, 'lotteries/open'), { waitingCount: 1 });
  await assertSucceeds(join.commit());
});
test('owner announcement delivery uses captured membership and recipient-owned read state', async () => {
  await seed({ 'lotteries/open/entries/entrant': entry('entrant', 'WAITING') });
  const database = db('owner');
  const b = writeBatch(database);
  const id = event(b, database, 'OWNER_ANNOUNCEMENT', 'LOTTERY', 'open', ['entrant'], 'owner', { title: 'News', body: 'Hello' });
  await assertSucceeds(b.commit());
  await assertFails(getDoc(doc(db('other'), `notifications/${id}`)));
  await assertSucceeds(setDoc(doc(database, `accounts/entrant/inbox/${id}`), { readAt: null }));
  await assertSucceeds(getDoc(doc(db('entrant'), `notifications/${id}`)));
  await assertFails(updateDoc(doc(database, `accounts/entrant/inbox/${id}`), { readAt: now() }));
  await assertSucceeds(updateDoc(doc(db('entrant'), `accounts/entrant/inbox/${id}`), { readAt: now() }));
  await assertFails(updateDoc(doc(db('entrant'), `accounts/entrant/inbox/${id}`), { readAt: now() }));
  await assertFails(setDoc(doc(database, `accounts/other/inbox/${id}`), { readAt: null }));
});
test('booking cannot grant an unrelated account a confirmation receipt', async () => {
  const database = db('entrant');
  const b = writeBatch(database);
  b.set(doc(database, `reservations/${reservationId}`), reservation());
  b.update(doc(database, 'campsites/site/calendar/2027-05-15'), { reservationId });
  b.update(doc(database, 'campsites/site/calendar/2027-05-16'), { reservationId });
  event(b, database, 'RESERVATION_CONFIRMED', 'RESERVATION', reservationId, ['other']);
  await assertFails(b.commit());
});
test('cancellation actor and role cannot be spoofed; an owner rejection succeeds', async () => {
  await assertSucceeds(booking());
  const database = db('owner');
  function reject(actorId, initiatedAs) {
    const b = writeBatch(database);
    b.update(doc(database, `reservations/${reservationId}`), { cancellation: { at: now(), actorId, initiatedAs, reason: 'Closed' }, 'payment.refundedAt': now() });
    return b.commit();
  }
  await assertFails(reject('entrant', 'ENTRANT'));
  await assertFails(reject('owner', 'ADMIN'));
  await assertSucceeds(reject('owner', 'OWNER'));
});
test('image publication and obsolete cleanup queue are restricted to target ownership', async () => {
  await seed({ 'images/campsite_site': { blobId: 'old', uploadedAt: now() } });
  const database = db('owner');
  await assertFails(setDoc(doc(db('owner2'), '_imageUploads/new'), {
    attachmentId: 'campsite_site', expiresAt: offset(900), abandoned: false, uploadFinished: false,
  }));
  await assertSucceeds(setDoc(doc(database, '_imageUploads/new'), {
    attachmentId: 'campsite_site', expiresAt: offset(900), abandoned: false, uploadFinished: false,
  }));
  await assertFails(setDoc(doc(database, 'images/campsite_site'), { blobId: 'new', uploadedAt: now() }));
  await assertSucceeds(updateDoc(doc(database, '_imageUploads/new'), { uploadFinished: true }));
  const b = writeBatch(database);
  b.set(doc(database, 'images/campsite_site'), { blobId: 'new', uploadedAt: now() });
  b.delete(doc(database, '_imageUploads/new'));
  b.set(doc(database, '_storageCleanup/old'), { queuedAt: now(), attachmentId: 'campsite_site' });
  await assertSucceeds(b.commit());
  await assertFails(getDoc(doc(db('other'), '_storageCleanup/old')));
  await assertSucceeds(deleteDoc(doc(database, '_storageCleanup/old')));
  await assertSucceeds(getDoc(doc(database, '_imageUploads/no-pending-upload')));
  await assertSucceeds(deleteDoc(doc(db('admin'), 'images/campsite_site')));
});
test('self-removal records cleanup authority without granting access to unrelated jobs', async () => {
  const database = db('owner');
  const removal = { at: now(), actorId: 'owner', reason: null };
  const b = writeBatch(database);
  b.update(doc(database, 'accounts/owner'), { name: '', email: null, phone: null, roles: [],
    preferences: { tags: [], amenities: [] },
    settings: { notificationsEnabled: false, colorMode: 'STANDARD', fontScale: 1, controlScale: 1 }, removal });
  b.set(doc(database, '_removals/account_owner'), removal);
  await assertSucceeds(b.commit());
  await assertSucceeds(getDocs(query(collection(database, '_removals'), where('actorId', '==', 'owner'))));
  await assertFails(getDocs(collection(database, '_removals')));
  await assertSucceeds(updateDoc(doc(database, 'campsites/site'), { removal }));
  await assertSucceeds(getDoc(doc(database, '_draws/draw')));
});
test('published-site image upload works with two cross-service document reads', async () => {
  const blob = 'site-upload';
  await assertSucceeds(setDoc(doc(db('owner'), `_imageUploads/${blob}`), {
    attachmentId: 'campsite_site', expiresAt: offset(900), abandoned: false, uploadFinished: false,
  }));
  const object = ref(store('owner'), `images/${blob}/original`);
  await assertSucceeds(uploadBytes(object, new Uint8Array([1, 2]), {
    contentType: 'image/jpeg', customMetadata: { attachmentId: 'campsite_site' },
  }));
  await assertFails(deleteObject(ref(store('owner2'), `images/${blob}/original`)));
  await assertSucceeds(deleteObject(object));
});
test('offer mutation needs owner scope and the matching revision update', async () => {
  await seed({ 'lotteries/future': { ...lottery('open'), registrationOpensAt: offset(1800), registrationClosesAt: offset(3600) } });
  const database = db('owner');
  await assertFails(setDoc(doc(database, 'lotteries/future/offers/site'), { campsiteId: 'site', nights: ['2027-05-15'] }));
  const b = writeBatch(database);
  b.set(doc(database, 'lotteries/future/offers/site'), { campsiteId: 'site', nights: ['2027-05-15'] });
  b.update(doc(database, 'lotteries/future'), { _revision: 1 });
  await assertSucceeds(b.commit());
});
test('owner cancellation and admin removal cancellation preserve lottery identity', async () => {
  await assertFails(updateDoc(doc(db('entrant'), 'lotteries/open'), { cancellation: { at: now(), actorId: 'entrant', reason: null } }));
  await assertSucceeds(updateDoc(doc(db('owner'), 'lotteries/open'), { cancellation: { at: now(), actorId: 'owner', reason: null } }));
  await assertFails(updateDoc(doc(db('owner'), 'lotteries/open'), { cancellation: null }));
  await assertSucceeds(updateDoc(doc(db('admin'), 'lotteries/book'), { cancellation: { at: now(), actorId: 'admin', reason: 'Moderation' } }));
});
test('draft campsite images remain private in both metadata and Storage', async () => {
  const blob = 'draft-upload';
  await assertSucceeds(setDoc(doc(db('owner'), `_imageUploads/${blob}`), {
    attachmentId: 'campsite_draft', expiresAt: offset(900), abandoned: false, uploadFinished: false,
  }));
  const object = ref(store('owner'), `images/${blob}/original`);
  await assertSucceeds(uploadBytes(object, new Uint8Array([1, 2]), {
    contentType: 'image/jpeg', customMetadata: { attachmentId: 'campsite_draft' },
  }));
  await seed({ 'images/campsite_draft': { blobId: blob, uploadedAt: now() } });
  await assertFails(getDoc(doc(db('entrant'), 'images/campsite_draft')));
  await assertFails(getBytes(ref(store('entrant'), `images/${blob}/original`)));
  await assertSucceeds(getBytes(object));
  await assertSucceeds(getBytes(ref(store('admin'), `images/${blob}/original`)));
  await assertSucceeds(deleteObject(object));
});


test('campsite-scoped offer query and owner-scoped published lottery query work for entrants', async () => {
  await seed({
    'lotteries/draft-lottery': { ...lottery('open'), publishedAt: null },
    'lotteries/draft-lottery/offers/site': { campsiteId: 'site', nights: ['2027-05-15'] },
    'lotteries/draft-lottery/offers/draft': { campsiteId: 'draft', nights: ['2027-05-15'] },
  });
  const database = db('entrant');
  const offers = await assertSucceeds(getDocs(query(collectionGroup(database, 'offers'), where('campsiteId', '==', 'site'))));
  assert.equal(offers.docs.length, 2);
  const published = await assertSucceeds(getDocs(query(collection(database, 'lotteries'), where('ownerId', '==', 'owner'), where('publishedAt', '!=', null))));
  assert.deepEqual(new Set(published.docs.map(d => d.id)), new Set(['open', 'draw', 'book']));
  await assertFails(getDocs(collectionGroup(database, 'offers')));
  const invalid = writeBatch(db('owner'));
  invalid.set(doc(db('owner'), 'lotteries/draft-lottery/offers/site'), { campsiteId: 'draft', nights: ['2027-05-15'] });
  invalid.update(doc(db('owner'), 'lotteries/draft-lottery'), { _revision: 1 });
  await assertFails(invalid.commit());
});

test('delivery queues are discoverable through permitted parents and globally only by admin', async () => {
  await seed({
    [`reservations/${reservationId}`]: reservation(),
    'lotteries/open/_notificationDeliveries/announcement': { recipientIds: ['entrant'] },
    [`reservations/${reservationId}/_notificationDeliveries/message`]: { recipientIds: ['owner'] },
  });
  await assertSucceeds(getDocs(collection(db('owner'), 'lotteries/open/_notificationDeliveries')));
  await assertFails(getDocs(collection(db('entrant'), 'lotteries/open/_notificationDeliveries')));
  for (const uid of ['owner', 'entrant']) {
    await assertSucceeds(getDocs(collection(db(uid), `reservations/${reservationId}/_notificationDeliveries`)));
  }
  await assertFails(getDocs(collection(db('other'), `reservations/${reservationId}/_notificationDeliveries`)));
  await assertSucceeds(getDocs(collectionGroup(db('admin'), '_notificationDeliveries')));
  await assertFails(getDocs(collectionGroup(db('owner'), '_notificationDeliveries')));
  await assertFails(getDocs(collection(db('owner'), '_notificationDeliveries')));
});

test('message event and delivery job share the reservation context', async () => {
  await seed({ [`reservations/${reservationId}`]: reservation() });
  const database = db('entrant');
  const valid = writeBatch(database);
  valid.set(doc(database, 'messages/m'), { reservationId, senderId: 'entrant', body: 'Hello', createdAt: now(), readAt: null });
  event(valid, database, 'NEW_MESSAGE', 'MESSAGE', 'm', ['owner'], 'entrant', null, reservationId);
  await assertSucceeds(valid.commit());
  const invalid = writeBatch(database);
  invalid.set(doc(database, 'messages/wrong'), { reservationId, senderId: 'entrant', body: 'Hello', createdAt: now(), readAt: null });
  event(invalid, database, 'NEW_MESSAGE', 'MESSAGE', 'wrong', ['owner'], 'entrant', null, 'elsewhere');
  await assertFails(invalid.commit());
});
