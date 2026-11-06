const { initializeApp } = require('firebase-admin/app');
const { getFirestore, Timestamp } = require('firebase-admin/firestore');
const { onCall, HttpsError } = require('firebase-functions/v2/https');
initializeApp();
const db = getFirestore();
db.settings({ useBigInt: true });
const pair = (...ids) => ids.map(id => `${id.length}:${id}`).join('');
const fail = (reason, message) => { throw new HttpsError('failed-precondition', message, { reason }); };
const requireState = (condition, reason, message) => { if (!condition) fail(reason, message); };
const id = value => {
  requireState(typeof value === 'string' && value.length > 0 && !value.includes('/'), 'INVALID_STATE', 'Invalid identity');
  return value;
};
const integer = value => {
  requireState(typeof value === 'string' && /^\d+$/.test(value), 'INVALID_STATE', 'Invalid integer');
  const parsed = BigInt(value);
  requireState(parsed <= 9223372036854775807n, 'INVALID_STATE', 'Integer overflow');
  return parsed;
};
const dateMillis = value => {
  requireState(typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value), 'INVALID_STATE', 'Invalid date');
  const millis = Date.parse(`${value}T00:00:00Z`);
  requireState(Number.isFinite(millis) && new Date(millis).toISOString().slice(0, 10) === value, 'INVALID_STATE', 'Invalid date');
  return millis;
};
const nightsBetween = (start, end) => {
  const first = dateMillis(start), last = dateMillis(end);
  requireState(last > first, 'INVALID_STATE', 'Checkout must follow arrival');
  const result = [];
  for (let day = first; day < last; day += 86400000) result.push(new Date(day).toISOString().slice(0, 10));
  return result;
};
const today = (now, zone) => new Intl.DateTimeFormat('en-CA', {
  timeZone: zone.replace(/^(UTC|GMT|UT)(?=[+-])/, ''), year: 'numeric', month: '2-digit', day: '2-digit',
}).format(now.toDate());
// Controlled time is available only in the disposable Functions emulator.
const operationTime = request => process.env.FUNCTIONS_EMULATOR === 'true' && request.data?.emulatorTime
  ? new Timestamp(request.data.emulatorTime.seconds, request.data.emulatorTime.nanos) : Timestamp.now();
const read = async (tx, path) => {
  const snapshot = await tx.get(db.doc(path));
  requireState(snapshot.exists, 'NOT_FOUND', 'Record not found');
  return snapshot.data();
};
const active = (account, role) => requireState(account.removal == null && (!role || account.roles.includes(role)),
  account.removal != null ? 'INACTIVE' : 'NOT_ALLOWED', 'Account cannot perform this operation');
const noticeRefs = (reservationId, cancelled) => {
  const type = cancelled ? 'RESERVATION_CANCELLED' : 'RESERVATION_CONFIRMED';
  const eventId = pair(type, reservationId);
  return { type, event: db.doc(`notifications/${eventId}`), job: db.doc(`reservations/${reservationId}/_notificationDeliveries/${eventId}`) };
};
const notice = (tx, reservationId, entrantId, cancelled, at, existing) => {
  const refs = noticeRefs(reservationId, cancelled);
  if (!existing?.event.exists) tx.set(refs.event, {
    senderId: null, type: refs.type, subject: { type: 'RESERVATION', id: reservationId }, announcement: null, createdAt: at,
  });
  // A consumed delivery job must stay consumed; recreate it only when creating the missing event.
  if (!existing?.event.exists) tx.set(refs.job, { recipientIds: [entrantId] });
};

/** Authenticate the caller before any trusted Firestore operation. */
const authenticated = handler => onCall(async request => {
  if (!request.auth) throw new HttpsError('unauthenticated', 'Sign in first');
  return handler(request, request.auth.uid);
});

exports.reserve = authenticated(async (request, uid) => {
  const value = request.data;
  const lotteryId = id(value.lotteryId), campsiteId = id(value.campsiteId);
  requireState(uid === value.entrantId, 'NOT_ALLOWED', 'Cannot book for another account');
  const guests = integer(value.guestCount), expected = integer(value.expectedTotalCents);
  const firstDay = dateMillis(value.checkInDate), lastDay = dateMillis(value.checkOutDate);
  requireState(lastDay > firstDay, 'INVALID_STATE', 'Checkout must follow arrival');
  const reservationId = pair(lotteryId, uid), ref = db.doc(`reservations/${reservationId}`);
  await db.runTransaction(async tx => {
    const now = operationTime(request);
    const actor = await read(tx, `accounts/${uid}`); active(actor, 'ENTRANT');
    requireState(!(await tx.get(ref)).exists, 'ALREADY_USED', 'Winning opportunity has already been used');
    const lottery = await read(tx, `lotteries/${lotteryId}`);
    const owner = await read(tx, `accounts/${lottery.ownerId}`); active(owner, 'OWNER');
    requireState(lottery.publishedAt != null && lottery.cancellation == null && lottery.drawnAt != null &&
      now.toMillis() >= lottery.bookingOpensAt.toMillis() && now.toMillis() < lottery.bookingClosesAt.toMillis(),
    'WINDOW_CLOSED', 'Booking window is closed');
    const entry = await read(tx, `lotteries/${lotteryId}/entries/${uid}`);
    requireState(entry.state === 'OFFERED', 'NOT_ALLOWED', 'No unused winning offer');
    const offer = await read(tx, `lotteries/${lotteryId}/offers/${campsiteId}`);
    requireState((lastDay - firstDay) / 86400000 <= offer.nights.length, 'NOT_ALLOWED', 'Dates are not offered');
    const nights = nightsBetween(value.checkInDate, value.checkOutDate);
    requireState(nights.every(day => offer.nights.includes(day)), 'NOT_ALLOWED', 'Dates are not offered');
    const site = await read(tx, `campsites/${campsiteId}`);
    requireState(site.publishedAt != null && site.removal == null && site.ownerId === lottery.ownerId, 'INACTIVE', 'Campsite unavailable');
    requireState(value.checkInDate >= today(now, site.timeZone), 'WINDOW_CLOSED', 'Stay has already begun');
    requireState(guests > 0n && guests <= BigInt(site.maxGuests), 'CAPACITY_REACHED', 'Group exceeds capacity');
    const total = BigInt(site.nightlyPriceCents) * BigInt(nights.length);
    requireState(total <= 9223372036854775807n && total === expected && value.acceptedRules === site.rules &&
      value.policyVersion === 'full-refund-v1', 'STALE_QUOTE', 'Price or terms changed');
    const calendar = await tx.getAll(...nights.map(day => db.doc(`campsites/${campsiteId}/calendar/${day}`)));
    requireState(calendar.every(row => row.exists && row.data().isOffered && row.data().reservationId == null), 'CONFLICT', 'Some nights are no longer free');
    tx.set(ref, {
      lotteryId, entrantId: uid, campsiteId, checkInDate: value.checkInDate, checkOutDate: value.checkOutDate, guestCount: guests,
      bookedCampsite: { name: site.name, locationLabel: site.locationLabel, location: site.location, timeZone: site.timeZone },
      acceptedTerms: { rules: site.rules, cancellationPolicyVersion: value.policyVersion, acceptedAt: now }, createdAt: now,
      cancellation: null, payment: { amountCents: total, paidAt: now, refundedAt: null },
    });
    for (const row of calendar) tx.update(row.ref, { reservationId });
    notice(tx, reservationId, uid, false, now);
  });
  return { reservationId };
});

exports.cancelReservation = authenticated(async (request, uid) => {
  const value = request.data, reservationId = id(value.reservationId), ref = db.doc(`reservations/${reservationId}`);
  requireState(['ENTRANT', 'OWNER', 'ADMIN'].includes(value.initiatedAs), 'NOT_ALLOWED', 'Unknown role');
  requireState(value.reason == null || typeof value.reason === 'string', 'INVALID_STATE', 'Invalid cancellation reason');
  await db.runTransaction(async tx => {
    const now = operationTime(request);
    const actor = await read(tx, `accounts/${uid}`);
    const reservation = await read(tx, ref.path);
    const site = await read(tx, `campsites/${reservation.campsiteId}`);
    const entrant = await read(tx, `accounts/${reservation.entrantId}`);
    const removal = entrant.removal?.actorId === value.actorId ? entrant.removal :
      site.removal?.actorId === value.actorId ? site.removal : null;
    const isAdmin = actor.removal == null && actor.roles.includes('ADMIN');
    let attribution = { actorId: value.actorId, initiatedAs: value.initiatedAs, reason: value.reason ?? null };
    if (value.forRemoval) {
      requireState(removal != null && (uid === removal.actorId || isAdmin), 'NOT_ALLOWED', 'No recorded removal authority');
      attribution = { actorId: removal.actorId, reason: removal.reason,
        initiatedAs: removal.actorId === reservation.entrantId ? 'ENTRANT' : removal.actorId === site.ownerId ? 'OWNER' : 'ADMIN' };
    } else {
      requireState(uid === value.actorId, 'NOT_ALLOWED', 'Cannot cancel for another actor');
      active(actor, value.initiatedAs);
      requireState(value.initiatedAs === 'ADMIN' ? isAdmin : value.initiatedAs === 'OWNER' ? site.ownerId === uid :
        value.initiatedAs === 'ENTRANT' && reservation.entrantId === uid, 'NOT_ALLOWED', 'Not a participant');
    }
    const payment = reservation.payment;
    if (!payment || !['number', 'bigint'].includes(typeof payment.amountCents) || payment.amountCents < 0 ||
      !(payment.paidAt instanceof Timestamp) || (payment.refundedAt != null && !(payment.refundedAt instanceof Timestamp))) {
      throw new HttpsError('data-loss', 'Missing or invalid payment', { documentPath: ref.path });
    }
    requireState(reservation.cancellation != null || today(now, reservation.bookedCampsite.timeZone) < reservation.checkOutDate,
      'WINDOW_CLOSED', 'Cancellation must be before checkout');
    const calendar = await tx.getAll(...nightsBetween(reservation.checkInDate, reservation.checkOutDate)
      .map(day => db.doc(`campsites/${reservation.campsiteId}/calendar/${day}`)));
    const refs = noticeRefs(reservationId, true);
    const [event] = await tx.getAll(refs.event);
    if (reservation.cancellation == null) {
      requireState(calendar.every(row => row.exists && row.data().reservationId === reservationId), 'INVALID_STATE', 'Reservation/calendar mismatch');
      tx.update(ref, { cancellation: { at: now, ...attribution },
        'payment.refundedAt': reservation.payment.refundedAt ?? now });
    }
    // Retry repairs only this reservation's remaining claims and never overwrites another stay.
    for (const row of calendar) if (row.exists && row.data().reservationId === reservationId) tx.update(row.ref, { reservationId: null });
    notice(tx, reservationId, reservation.entrantId, true, reservation.cancellation?.at ?? now, { event });
  });
  return { reservationId };
});
