/*
 * Compiles the declarative halfway fixture and seeds or resets its linked Firestore and Storage data. Binds demo
 * people to their real Firebase UIDs and supports cloud setup before deployment.
 */

const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { calendarDate } = require('./calendar-date');
const pair = (...ids) => ids.map(id => `${id.length}:${id}`).join('');

// Build current-schema records first so an invalid declaration cannot start a partial setup.
function compileFixture(fixture, actors, anchor) {
  assert.equal(fixture.checkpoint, 'halfway');
  assert.ok(anchor instanceof Date && Number.isFinite(anchor.getTime()), 'A valid anchor is required');
  const names = Object.keys(fixture.actors);
  for (const name of names) assert.ok(typeof actors[name] === 'string' && actors[name] && !actors[name].includes('/'), `Bind ${name} to its Firebase UID`);
  assert.equal(new Set(names.map(name => actors[name])).size, names.length, 'Actors need distinct UIDs');
  const documents = new Map(), photos = [], roots = new Set();
  const at = seconds => new Date(anchor.getTime() + seconds * 1000);
  const today = calendarDate(anchor, fixture.timeZone);
  const day = offset => new Date(Date.parse(today + 'T00:00:00Z') + offset * 86400000).toISOString().slice(0, 10);
  const put = (key, value) => { assert.ok(!documents.has(key), `Duplicate record ${key}`); documents.set(key, value); };
  const notice = (type, subjectType, subjectId, recipients, createdAt, senderId = null) => {
    const id = subjectType === 'LOTTERY' ? pair(type, subjectId, ...recipients) : pair(type, subjectId);
    const key = `notifications/${id}`;
    put(key, { senderId, type, subject: { type: subjectType, id: subjectId }, announcement: null, createdAt });
    roots.add(key);
    for (const uid of recipients) put(`accounts/${uid}/inbox/${id}`, { readAt: null });
  };
  for (const [name, actor] of Object.entries(fixture.actors)) {
    assert.ok(actor.roles.length && actor.roles.every(role => ['ENTRANT', 'OWNER', 'ADMIN'].includes(role)), `Invalid roles for ${name}`);
    put(`accounts/${actors[name]}`, { name: actor.name, email: null, phone: null, roles: actor.roles,
      preferences: { tags: [], amenities: [] }, settings: { notificationsEnabled: true, colorMode: 'STANDARD', fontScale: 1, controlScale: 1 },
      createdAt: at(-7776000), removal: null });
  }
  for (const site of fixture.campsites) {
    assert.ok(fixture.actors[site.owner]?.roles.includes('OWNER'), 'A campsite needs its declared owner');
    const key = `campsites/${site.id}`;
    roots.add(key);
    put(key, { ownerId: actors[site.owner], name: site.name, description: site.description,
      location: { latitude: site.latitude, longitude: site.longitude }, locationLabel: site.locationLabel,
      timeZone: fixture.timeZone, maxGuests: site.maxGuests, nightlyPriceCents: site.nightlyPriceCents,
      amenities: site.amenities, tags: site.tags, rules: site.rules, createdAt: at(-7776000),
      publishedAt: site.published ? at(-5184000) : null, removal: null });
    for (const offset of site.offeredDayOffsets) put(`${key}/calendar/${day(offset)}`, { isOffered: true, reservationId: null });
    if (site.photo) photos.push({ attachmentId: `campsite_${site.id}`, blobId: `halfway_${site.id}`, source: site.photo, uploadedAt: anchor });
  }
  for (const lottery of fixture.lotteries) {
    assert.ok(fixture.actors[lottery.organizer]?.roles.includes('OWNER'), 'A lottery needs an owner organizer');
    const participants = lottery.participants;
    assert.ok(Array.isArray(participants) && participants.includes(lottery.organizer)
      && new Set(participants).size === participants.length, 'Invalid lottery participants');
    for (const owner of participants) assert.ok(fixture.actors[owner]?.roles.includes('OWNER'), 'A participant needs owner capability');
    const { registrationOpensSeconds: ro, registrationClosesSeconds: rc, bookingOpensSeconds: bo, bookingClosesSeconds: bc } = lottery;
    assert.ok([ro, rc, bo, bc].every(Number.isFinite) && ro < rc && rc <= bo && bo < bc, 'Invalid lottery window');
    const drawn = Object.keys(lottery.outcomes).length > 0;
    assert.ok(!drawn || rc <= 0, 'Draw outcomes require a closed registration window');
    const key = `lotteries/${lottery.id}`;
    roots.add(key);
    put(key, { ownerId: actors[lottery.organizer], participantOwnerIds: participants.map(owner => actors[owner]).sort(),
      title: lottery.title, description: 'A whole campsite for a quiet break in the outdoors.',
      registrationOpensAt: at(ro), registrationClosesAt: at(rc), bookingOpensAt: at(bo), bookingClosesAt: at(bc),
      winnerCount: lottery.winnerCount, waitlistCapacity: null, waitingCount: 0,
      createdAt: at(ro - 86400), publishedAt: at(ro - 3600), drawnAt: drawn ? at(rc) : null, cancellation: null, _revision: 0 });
    assert.ok(Array.isArray(lottery.offers) && lottery.offers.length, 'A lottery needs campsite offers');
    for (const offer of lottery.offers) {
      const site = documents.get(`campsites/${offer.campsiteId}`);
      assert.ok(site?.publishedAt, 'A lottery needs a published campsite');
      assert.ok(participants.some(owner => actors[owner] === site.ownerId), 'Offered campsite owner must participate');
      const nights = offer.nightOffsets.map(day);
      assert.ok(nights.length && new Set(nights).size === nights.length, 'Offer nights must be unique');
      for (const date of nights) assert.ok(documents.get(`campsites/${offer.campsiteId}/calendar/${date}`)?.isOffered, 'Lottery nights must be offered');
      put(`${key}/offers/${offer.campsiteId}`, { campsiteId: offer.campsiteId, nights: nights.sort() });
    }
    for (const [name, state] of Object.entries(lottery.outcomes)) {
      assert.ok(fixture.actors[name]?.roles.includes('ENTRANT') && ['OFFERED', 'LOST'].includes(state), 'Invalid entrant outcome');
      const uid = actors[name];
      put(`${key}/entries/${uid}`, { accountId: uid, state, joinedAt: at(ro), stateChangedAt: at(rc) });
      notice(state === 'OFFERED' ? 'LOTTERY_WON' : 'LOTTERY_LOST', 'LOTTERY', lottery.id, [uid], at(rc));
    }
  }
  for (const stay of fixture.reservations) {
    const lottery = fixture.lotteries.find(item => item.id === stay.lotteryId);
    assert.ok(lottery, 'Reservation lottery missing');
    const uid = actors[stay.entrant], id = pair(lottery.id, uid), key = `reservations/${id}`;
    assert.equal(documents.get(`lotteries/${lottery.id}/entries/${uid}`)?.state, 'OFFERED', 'Reservation needs a winning entrant');
    const site = documents.get(`campsites/${stay.campsiteId}`);
    assert.ok(site, 'Reservation campsite missing');
    const nights = Array.from({ length: stay.nightCount }, (_, n) => day(stay.checkInDayOffset + n));
    assert.ok(stay.nightCount > 0 && stay.guestCount > 0 && stay.guestCount <= site.maxGuests, 'Invalid stay');
    const offer = documents.get(`lotteries/${lottery.id}/offers/${stay.campsiteId}`);
    assert.ok(offer, 'Reservation campsite must be offered');
    const offered = offer.nights;
    for (const date of nights) {
      const night = documents.get(`campsites/${stay.campsiteId}/calendar/${date}`);
      assert.ok(offered.includes(date) && night && night.reservationId === null, 'Reservation needs free offered nights');
      night.reservationId = id;
    }
    const bookedAt = at(lottery.bookingOpensSeconds + 60);
    put(key, { lotteryId: lottery.id, entrantId: uid, campsiteId: stay.campsiteId,
      checkInDate: nights[0], checkOutDate: day(stay.checkInDayOffset + stay.nightCount), guestCount: stay.guestCount,
      bookedCampsite: { name: site.name, locationLabel: site.locationLabel, location: site.location, timeZone: site.timeZone },
      acceptedTerms: { rules: site.rules, cancellationPolicyVersion: 'full-refund-v1', acceptedAt: bookedAt },
      createdAt: bookedAt, cancellation: null, payment: { amountCents: site.nightlyPriceCents * stay.nightCount, paidAt: bookedAt, refundedAt: null } });
    roots.add(key);
    notice('RESERVATION_CONFIRMED', 'RESERVATION', id, [uid], bookedAt);
  }
  for (const review of fixture.reviews) {
    assert.ok(documents.has(`campsites/${review.campsiteId}`), 'Review campsite missing');
    assert.ok(fixture.actors[review.author]?.roles.includes('ENTRANT'), 'Review needs an entrant author');
    assert.ok(review.rating === null || (Number.isInteger(review.rating) && review.rating >= 1 && review.rating <= 5), 'Invalid review rating');
    assert.ok(review.body === null || (typeof review.body === 'string' && review.body.trim()), 'Invalid review text');
    put(`campsites/${review.campsiteId}/reviews/${actors[review.author]}`, {
      rating: review.rating, body: review.body, createdAt: at(review.createdSeconds), updatedAt: at(review.createdSeconds),
    });
  }
  for (const message of fixture.messages) {
    const reservationId = pair(message.lotteryId, actors[message.entrant]);
    const reservation = documents.get(`reservations/${reservationId}`);
    assert.ok(reservation, 'Message reservation missing');
    const ownerId = documents.get(`campsites/${reservation.campsiteId}`).ownerId;
    const senderId = actors[message.sender];
    assert.ok(senderId === reservation.entrantId || senderId === ownerId, 'Message sender must be a reservation participant');
    assert.notEqual(ownerId, reservation.entrantId, 'Conversation needs two distinct participants');
    assert.ok(typeof message.body === 'string' && message.body.trim(), 'Message needs visible text');
    const key = `messages/${message.id}`;
    roots.add(key);
    put(key, { reservationId, senderId, body: message.body, createdAt: at(message.createdSeconds), readAt: null });
    const recipientId = senderId === ownerId ? reservation.entrantId : ownerId;
    notice('NEW_MESSAGE', 'MESSAGE', message.id, [recipientId], at(message.createdSeconds), senderId);
  }
  // Live actions derive these same IDs. Reset also clears results created during rehearsal.
  for (const lottery of fixture.lotteries) {
    roots.add(`_draws/${lottery.id}`);
    for (const uid of Object.values(actors)) {
      const reservationId = pair(lottery.id, uid);
      roots.add(`reservations/${reservationId}`);
      const events = [pair('LOTTERY_WON', lottery.id, uid), pair('LOTTERY_LOST', lottery.id, uid),
        pair('RESERVATION_CONFIRMED', reservationId), pair('RESERVATION_CANCELLED', reservationId)];
      for (const id of events) {
        roots.add(`notifications/${id}`);
        for (const recipient of Object.values(actors)) roots.add(`accounts/${recipient}/inbox/${id}`);
      }
    }
  }
  return { documents, photos, roots };
}

// The adapter keeps cloud setup and deterministic fixture contracts on the same write path.
async function applyFixture(plan, backend, reset = false) {
  if (reset) {
    for (const key of plan.roots) await backend.removeTree(key);
    for (const key of plan.documents.keys()) if (key.includes('/inbox/')) await backend.removeTree(key);
    for (const photo of plan.photos) await backend.removeTree(`images/${photo.attachmentId}`);
  }
  const retained = reset ? [] : await Promise.all([...plan.roots].map(async key => await backend.get(key) ? key : null));
  for (const [key, value] of plan.documents) {
    if (retained.some(root => root && (key === root || key.startsWith(root + '/')))) continue;
    const existing = await backend.get(key);
    if (!existing) await backend.put(key, value);
    else if (/^accounts\/[^/]+$/.test(key)) {
      const roles = [...new Set([...existing.roles, ...value.roles])];
      if (roles.length !== existing.roles.length) await backend.put(key, { ...existing, roles });
    }
  }
  for (const photo of plan.photos) {
    const key = `images/${photo.attachmentId}`;
    if (await backend.get(key)) continue;
    const blobId = await backend.upload(photo);
    await backend.put(key, { blobId, uploadedAt: photo.uploadedAt });
  }
}

async function main(argv) {
  const args = {};
  for (let i = 0; i < argv.length; i++) {
    const key = argv[i];
    if (['--reset', '--validate', '--configure'].includes(key)) args[key.slice(2)] = true;
    else { assert.ok(['--project', '--actors', '--anchor', '--fixture'].includes(key), `Unknown option ${key}`); args[key.slice(2)] = argv[++i]; }
  }
  const fixture = JSON.parse(fs.readFileSync(args.fixture || path.resolve(__dirname, '../fixtures/halfway.json')));
  if (args.configure) {
    assert.ok(args.project, 'Use --project with the Firebase project to configure');
    const { applicationDefault } = require('firebase-admin/app');
    const token = await applicationDefault().getAccessToken();
    const headers = { Authorization: `Bearer ${token.access_token}`, 'Content-Type': 'application/json' };
    const configUrl = `https://identitytoolkit.googleapis.com/admin/v2/projects/${args.project}/config`;
    const current = await fetch(configUrl, { headers });
    if (current.status === 404) {
      const initialized = await fetch(`https://identitytoolkit.googleapis.com/v2/projects/${args.project}/identityPlatform:initializeAuth`, { method: 'POST', headers, body: '{}' });
      assert.ok(initialized.ok, `Auth initialization failed: ${await initialized.text()}`);
    } else assert.ok(current.ok, `Auth configuration read failed: ${await current.text()}`);
    const updated = await fetch(configUrl + '?updateMask=signIn.anonymous.enabled', { method: 'PATCH', headers,
      body: JSON.stringify({ name: `projects/${args.project}/config`, signIn: { anonymous: { enabled: fixture.anonymousAuth } } }) });
    assert.ok(updated.ok, `Auth configuration failed: ${await updated.text()}`);
    const projectResponse = await fetch(`https://cloudresourcemanager.googleapis.com/v1/projects/${args.project}`, { headers });
    if (!projectResponse.ok) throw new Error(`Project lookup failed: ${await projectResponse.text()}`);
    const project = await projectResponse.json();
    const member = `serviceAccount:${project.projectNumber}-compute@developer.gserviceaccount.com`;
    const policyUrl = `https://cloudresourcemanager.googleapis.com/v1/projects/${args.project}`;
    const policyResponse = await fetch(policyUrl + ':getIamPolicy', { method: 'POST', headers, body: JSON.stringify({ options: { requestedPolicyVersion: 3 } }) });
    if (!policyResponse.ok) throw new Error(`Project roles read failed: ${await policyResponse.text()}`);
    const policy = await policyResponse.json();
    for (const role of fixture.functionServiceRoles) {
      let binding = policy.bindings.find(item => item.role === role && !item.condition);
      if (!binding) { binding = { role, members: [] }; policy.bindings.push(binding); }
      if (!binding.members.includes(member)) binding.members.push(member);
    }
    const assigned = await fetch(policyUrl + ':setIamPolicy', { method: 'POST', headers, body: JSON.stringify({ policy }) });
    assert.ok(assigned.ok, `Function service roles failed: ${await assigned.text()}`);
    for (const name of fixture.publicCallables) {
      const endpoint = await fetch(`https://cloudfunctions.googleapis.com/v2/projects/${args.project}/locations/us-central1/functions/${name}`, { headers });
      if (endpoint.status === 404) continue;
      if (!endpoint.ok) throw new Error(`Function lookup failed: ${await endpoint.text()}`);
      const service = (await endpoint.json()).serviceConfig?.service;
      if (!service) continue;
      const serviceUrl = `https://run.googleapis.com/v2/${service}`;
      const read = await fetch(serviceUrl + ':getIamPolicy', { headers });
      if (!read.ok) throw new Error(`Callable policy read failed: ${await read.text()}`);
      const policy = await read.json();
      policy.bindings ||= [];
      let binding = policy.bindings.find(item => item.role === 'roles/run.invoker' && !item.condition);
      if (!binding) { binding = { role: 'roles/run.invoker', members: [] }; policy.bindings.push(binding); }
      if (!binding.members.includes('allUsers')) binding.members.push('allUsers');
      const written = await fetch(serviceUrl + ':setIamPolicy', { method: 'POST', headers, body: JSON.stringify({ policy }) });
      assert.ok(written.ok, `Callable policy update failed: ${await written.text()}`);
    }
    console.log(`Halfway Auth and function service roles configured in ${args.project}.`);
    return;
  }
  assert.ok(args.actors, 'Use --actors path/to/actors.json with a Firebase UID for each declared person');
  const actors = JSON.parse(fs.readFileSync(args.actors));
  const anchor = new Date(args.anchor || Date.now());
  const plan = compileFixture(fixture, actors, anchor);
  if (args.validate) { console.log(`Halfway fixture is valid at ${anchor.toISOString()}.`); return; }
  assert.ok(args.project, 'Use --project with the Firebase project to seed');
  const { initializeApp } = require('firebase-admin/app');
  const { getFirestore, GeoPoint } = require('firebase-admin/firestore');
  const { getAuth } = require('firebase-admin/auth');
  const { getStorage } = require('firebase-admin/storage');
  const appConfig = JSON.parse(fs.readFileSync(path.resolve(__dirname, '../../app/google-services.json'))).project_info;
  const app = initializeApp({ projectId: args.project, storageBucket: process.env.FIREBASE_STORAGE_BUCKET || appConfig.storage_bucket });
  const db = getFirestore(app), bucket = getStorage(app).bucket(), batch = db.batch();
  for (const uid of Object.values(actors)) await getAuth(app).getUser(uid);
  await applyFixture(plan, {
    get: async key => (await db.doc(key).get()).data(),
    put: async (key, value) => {
      const stored = { ...value };
      if (stored.location) stored.location = new GeoPoint(stored.location.latitude, stored.location.longitude);
      if (stored.bookedCampsite) stored.bookedCampsite = { ...stored.bookedCampsite, location: new GeoPoint(stored.bookedCampsite.location.latitude, stored.bookedCampsite.location.longitude) };
      batch.set(db.doc(key), stored);
    },
    removeTree: async key => {
      if (key.startsWith('images/')) {
        const image = (await db.doc(key).get()).data();
        if (image) await bucket.file(`images/${image.blobId}/original`).delete({ ignoreNotFound: true });
      }
      await db.recursiveDelete(db.doc(key));
    },
    upload: async photo => {
      const { randomUUID } = require('node:crypto');
      const blobId = randomUUID();
      // A fresh blob reloads running clients after reset; Firebase download URLs need a token.
      await bucket.upload(path.resolve(__dirname, '../../assets', photo.source), {
        destination: `images/${blobId}/original`,
        metadata: { contentType: 'image/jpeg', metadata: { attachmentId: photo.attachmentId, firebaseStorageDownloadTokens: randomUUID() } },
      });
      return blobId;
    },
  }, args.reset);
  // Publish the related records together after every declared photo is uploaded.
  await batch.commit();
  await db.terminate();
  console.log(`Halfway ${args.reset ? 'reset' : 'seed'} complete in ${args.project}; anchor ${anchor.toISOString()}.`);
}

module.exports = { compileFixture, applyFixture };
if (require.main === module) main(process.argv.slice(2)).catch(error => { console.error(error.message); process.exitCode = 1; });
