/*
 * Materializes the published Halfway release schema. Keep Final seed changes out of this historical fixture.
 */

const assert = require('node:assert/strict');
const calendarDate = (instant, timeZone) => new Intl.DateTimeFormat('en-CA', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit' }).format(instant);
const pair = (...ids) => ids.map(id => `${id.length}:${id}`).join('');

// Validate the whole declaration before reset can remove any current data.
function compileFixture(fixture, actors, anchor) {
  assert.equal(fixture.checkpoint, 'halfway');
  assert.ok(anchor instanceof Date && Number.isFinite(anchor.getTime()), 'A valid anchor is required');
  const names = Object.keys(fixture.actors);
  for (const name of names) assert.ok(typeof actors[name] === 'string' && actors[name] && !actors[name].includes('/'), `Bind ${name} to its Firebase UID`);
  assert.equal(new Set(names.map(name => actors[name])).size, names.length, 'Actors need distinct UIDs');
  const documents = new Map(), photos = [];
  const at = seconds => new Date(anchor.getTime() + seconds * 1000);
  const today = calendarDate(anchor, fixture.timeZone);
  const day = offset => new Date(Date.parse(today + 'T00:00:00Z') + offset * 86400000).toISOString().slice(0, 10);
  const put = (key, value) => { assert.ok(!documents.has(key), `Duplicate record ${key}`); documents.set(key, value); };
  const notice = (type, subjectType, subjectId, recipients, createdAt) => {
    const id = subjectType === 'RESERVATION' ? pair(type, subjectId) : pair(type, subjectId, ...recipients);
    const key = `notifications/${id}`;
    put(key, { senderId: null, type, subject: { type: subjectType, id: subjectId }, announcement: null, createdAt });
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
    put(key, { ownerId: actors[site.owner], name: site.name, description: site.description,
      location: { latitude: site.latitude, longitude: site.longitude }, locationLabel: site.locationLabel,
      timeZone: fixture.timeZone, maxGuests: site.maxGuests, nightlyPriceCents: site.nightlyPriceCents,
      amenities: site.amenities, tags: site.tags, rules: site.rules, createdAt: at(-7776000),
      publishedAt: site.published ? at(-5184000) : null, removal: null });
    for (const offset of site.offeredDayOffsets) put(`${key}/calendar/${day(offset)}`, { isOffered: true, reservationId: null });
    if (site.photo) photos.push({ attachmentId: `campsite_${site.id}`, blobId: `halfway_${site.id}`, source: site.photo, uploadedAt: anchor });
  }
  for (const lottery of fixture.lotteries) {
    const site = documents.get(`campsites/${lottery.campsiteId}`);
    assert.ok(site?.publishedAt, 'A lottery needs a published campsite');
    const { registrationOpensSeconds: ro, registrationClosesSeconds: rc, bookingOpensSeconds: bo, bookingClosesSeconds: bc } = lottery;
    assert.ok([ro, rc, bo, bc].every(Number.isFinite) && ro < rc && rc <= bo && bo < bc, 'Invalid lottery window');
    const drawn = Object.keys(lottery.outcomes).length > 0;
    assert.ok(!drawn || rc <= 0, 'Draw outcomes require a closed registration window');
    const key = `lotteries/${lottery.id}`;
    put(key, { ownerId: site.ownerId, title: lottery.title, description: 'A whole campsite for a quiet break in the outdoors.',
      registrationOpensAt: at(ro), registrationClosesAt: at(rc), bookingOpensAt: at(bo), bookingClosesAt: at(bc),
      winnerCount: lottery.winnerCount, waitlistCapacity: null, waitingCount: 0,
      createdAt: at(ro - 86400), publishedAt: at(ro - 3600), drawnAt: drawn ? at(rc) : null, cancellation: null, _revision: 0 });
    const nights = lottery.nightOffsets.map(day);
    for (const date of nights) assert.ok(documents.get(`campsites/${lottery.campsiteId}/calendar/${date}`)?.isOffered, 'Lottery nights must be offered');
    put(`${key}/offers/${lottery.campsiteId}`, { campsiteId: lottery.campsiteId, nights });
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
    const site = documents.get(`campsites/${lottery.campsiteId}`);
    const nights = Array.from({ length: stay.nightCount }, (_, n) => day(stay.checkInDayOffset + n));
    assert.ok(stay.nightCount > 0 && stay.guestCount > 0 && stay.guestCount <= site.maxGuests, 'Invalid stay');
    const offered = documents.get(`lotteries/${lottery.id}/offers/${lottery.campsiteId}`).nights;
    for (const date of nights) {
      const night = documents.get(`campsites/${lottery.campsiteId}/calendar/${date}`);
      assert.ok(offered.includes(date) && night && night.reservationId === null, 'Reservation needs free offered nights');
      night.reservationId = id;
    }
    const bookedAt = at(lottery.bookingOpensSeconds + 60);
    put(key, { lotteryId: lottery.id, entrantId: uid, campsiteId: lottery.campsiteId,
      checkInDate: nights[0], checkOutDate: day(stay.checkInDayOffset + stay.nightCount), guestCount: stay.guestCount,
      bookedCampsite: { name: site.name, locationLabel: site.locationLabel, location: site.location, timeZone: site.timeZone },
      acceptedTerms: { rules: site.rules, cancellationPolicyVersion: 'full-refund-v1', acceptedAt: bookedAt },
      createdAt: bookedAt, cancellation: null, payment: { amountCents: site.nightlyPriceCents * stay.nightCount, paidAt: bookedAt, refundedAt: null } });
    notice('RESERVATION_CONFIRMED', 'RESERVATION', id, [uid], bookedAt);
  }
  return { documents, photos };
}


const fixture = require('./fixture.json');
const config = require('./config.json');

/** Prepare either relative practice windows or the scheduled presentation without changing Firebase. */
function prepare(actors, { mode = 'practice', anchor = new Date(mode === 'demo' ? config.demoStart : Date.now()) } = {}) {
  assert.ok(['practice', 'demo'].includes(mode), 'Mode must be practice or demo');
  assert.ok(anchor instanceof Date && Number.isFinite(anchor.getTime()), 'A valid anchor is required');
  for (const role of ['host', 'camper', 'newcamper', 'admin']) {
    assert.ok(typeof actors[role] === 'string' && actors[role].trim() && !actors[role].includes('/')
      && !actors[role].startsWith('REPLACE_WITH_'), `Bind ${role} to its installed Auth UID`);
  }
  assert.equal(new Set(['host', 'camper', 'newcamper', 'admin'].map(role => actors[role])).size, 4, 'Actors need distinct UIDs');
  const declaration = structuredClone(fixture);
  const live = declaration.lotteries.find(lottery => lottery.id === 'halfway-open');
  live.registrationClosesSeconds = live.bookingOpensSeconds = mode === 'practice' ? config.practiceRegistrationMinutes * 60 : 480;
  const plan = compileFixture(declaration, actors, anchor);
  plan.documents.get('lotteries/halfway-next').publishedAt = null;
  plan.documents.get('accounts/' + actors.host).email = 'host.demo@example.com';
  for (const [role, person] of Object.entries(declaration.actors)) {
    if (person.photo) plan.photos.push({ attachmentId: 'account_' + actors[role], source: person.photo, uploadedAt: anchor });
  }
  plan.anchor = anchor;
  plan.closesAt = live.registrationClosesSeconds * 1000 + anchor.getTime();
  return plan;
}

module.exports = { prepare };
