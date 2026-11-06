/*
 * Tests fixture relationships, validation and repeatable seed or reset operations with a controlled in-memory
 * backend.
 */

const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { compileFixture, applyFixture } = require('./seed-halfway');
const fixture = require('../fixtures/halfway.json');
const anchor = new Date('2027-05-10T18:00:00Z');
const actors = { host: 'host-uid', entrant: 'entrant-uid', other: 'other-uid', admin: 'admin-uid' };
const pair = (...ids) => ids.map(id => `${id.length}:${id}`).join('');

test('fixture materialization connects real people, relative windows, inventory and saved history', () => {
  const plan = compileFixture(fixture, actors, anchor);
  const site = plan.documents.get('campsites/halfway-river');
  assert.equal(site.ownerId, actors.host);
  assert.equal(site.timeZone, 'America/Edmonton');
  const live = plan.documents.get('lotteries/halfway-open');
  assert.equal(live.registrationClosesAt.getTime(), anchor.getTime() + 180000);
  assert.equal(live.waitingCount, 0);
  assert.equal(live._revision, 0);
  assert.equal(live.drawnAt, null);
  assert.equal(plan.documents.get('lotteries/halfway-won/entries/entrant-uid').state, 'OFFERED');
  const id = pair('halfway-booked', actors.entrant);
  const reservation = plan.documents.get(`reservations/${id}`);
  assert.equal(reservation.entrantId, actors.entrant);
  assert.equal(reservation.payment.amountCents, 13000);
  assert.equal(reservation.checkInDate, '2027-06-01');
  assert.equal(plan.documents.get('campsites/halfway-river/calendar/2027-06-01').reservationId, id);
  assert.equal(reservation.bookedCampsite.name, site.name);
  assert.equal(reservation.acceptedTerms.rules, site.rules);
  const events = [...plan.documents].filter(([key]) => key.startsWith('notifications/'));
  assert.ok(events.some(([, event]) => event.type === 'LOTTERY_LOST'));
  assert.ok(events.some(([, event]) => event.subject.id === id));
  for (const [key] of events) assert.ok(plan.documents.has(`accounts/${actors.entrant}/inbox/${key.split('/')[1]}`) || plan.documents.has(`accounts/${actors.other}/inbox/${key.split('/')[1]}`));
  for (const photo of plan.photos) assert.ok(fs.statSync(path.resolve(__dirname, '../../assets', photo.source)).size > 0);
});

test('invalid bindings, references and windows fail before preparing any writes', () => {
  assert.throws(() => compileFixture(fixture, { ...actors, admin: '' }, anchor), /admin/);
  assert.throws(() => compileFixture(fixture, { ...actors, other: actors.entrant }, anchor), /distinct/);
  assert.throws(() => compileFixture(fixture, actors, new Date('invalid')), /anchor/);
  const missing = structuredClone(fixture); missing.lotteries[0].campsiteId = 'missing';
  assert.throws(() => compileFixture(missing, actors, anchor), /campsite/);
  const timing = structuredClone(fixture); timing.lotteries[0].bookingOpensSeconds = -1;
  assert.throws(() => compileFixture(timing, actors, anchor), /window/);
});

// Exercise setup effects without Firebase credentials or a second implementation of the compiler.
function memoryBackend(initial = new Map()) {
  const records = new Map(initial); const uploads = [];
  return { records, uploads,
    get: async key => records.get(key),
    put: async (key, value) => records.set(key, value),
    removeTree: async key => { for (const p of [...records.keys()]) if (p === key || p.startsWith(key + '/')) records.delete(p); },
    upload: async photo => { uploads.push(photo); return photo.blobId; },
  };
}

test('seed preserves edits on rerun, grants declared roles and uploads before publishing images', async () => {
  const plan = compileFixture(fixture, actors, anchor);
  const backend = memoryBackend(new Map([
    ['accounts/admin-uid', { ...plan.documents.get('accounts/admin-uid'), name: 'Taylor edited', roles: ['ENTRANT'] }],
    ['campsites/halfway-river', { ...plan.documents.get('campsites/halfway-river'), name: 'Edited listing' }],
  ]));
  const put = backend.put;
  backend.put = async (key, value) => {
    if (key.startsWith('images/')) assert.ok(backend.uploads.some(photo => photo.blobId === value.blobId));
    return put(key, value);
  };
  await applyFixture(plan, backend);
  assert.equal(backend.records.get('campsites/halfway-river').name, 'Edited listing');
  assert.equal(backend.records.get('accounts/admin-uid').name, 'Taylor edited');
  assert.deepEqual(new Set(backend.records.get('accounts/admin-uid').roles), new Set(['ENTRANT', 'ADMIN']));
  const uploads = backend.uploads.length;
  await applyFixture(plan, backend);
  assert.equal(backend.uploads.length, uploads);
  assert.ok(backend.records.has('images/campsite_halfway-river'));
});

test('reset replaces the declared dataset, clears child history and retains other records', async () => {
  const plan = compileFixture(fixture, actors, anchor);
  const backend = memoryBackend(new Map([
    ['campsites/halfway-river/calendar/2030-01-01', { isOffered: true, reservationId: null }],
    ['lotteries/halfway-open/entries/extra-uid', { state: 'WAITING' }],
    ['campsites/unrelated', { name: 'Keep this' }],
    ['accounts/entrant-uid/inbox/unrelated', { readAt: anchor }],
  ]));
  await applyFixture(plan, backend, true);
  assert.equal(backend.records.has('lotteries/halfway-open/entries/extra-uid'), false);
  assert.equal(backend.records.has('campsites/halfway-river/calendar/2030-01-01'), false);
  assert.equal(backend.records.get('campsites/unrelated').name, 'Keep this');
  assert.equal(backend.records.get('accounts/entrant-uid/inbox/unrelated').readAt, anchor);
  assert.equal(backend.records.get('campsites/halfway-river').name, 'River Camp');
  assert.ok(backend.uploads.length > 0);
});
