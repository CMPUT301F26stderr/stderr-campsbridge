const { selectWinners } = require('./draw-order');

const pair = (...ids) => ids.map(id => `${id.length}:${id}`).join('');
const before = (left, right) => left.seconds < right.seconds ||
  (left.seconds === right.seconds && left.nanoseconds < right.nanoseconds);
const active = lottery => lottery?.publishedAt != null && lottery.cancellation == null;

/** Start or resume one draw. Transaction callbacks only read/write the shared Firestore protocol. */
async function drawLottery({ db, lotteryId, now, newSeed }) {
  const ref = db.doc(`lotteries/${lotteryId}`), marker = db.doc(`_draws/${lotteryId}`);
  const proposedSeed = newSeed();
  const seed = await db.runTransaction(async tx => {
    const [saved, previous] = await tx.getAll(ref, marker);
    const lottery = saved.data();
    if (!active(lottery) || lottery.drawnAt != null || before(now, lottery.registrationClosesAt)) return null;
    if (previous.exists) return previous.get('seed');
    tx.create(marker, { seed: proposedSeed });
    // Share the client's membership freeze barrier; in-flight joins must retry against the marker.
    tx.update(ref, { waitingCount: lottery.waitingCount });
    return proposedSeed;
  });
  if (seed == null) return;

  const lottery = (await ref.get()).data();
  if (!active(lottery) || lottery.drawnAt != null) return;
  const candidates = (await ref.collection('entries').get()).docs.filter(row => row.get('state') !== 'WITHDRAWN');
  // Applied outcomes remain candidates so resuming cannot promote a different winner.
  const winners = new Set(selectWinners({ seed, accountIds: candidates.map(row => row.id), winnerCount: BigInt(lottery.winnerCount) }));
  for (const candidate of candidates) {
    const keepGoing = await db.runTransaction(async tx => {
      const [parent, saved] = await tx.getAll(ref, candidate.ref);
      const current = parent.data(), entry = saved.data();
      if (!active(current) || current.drawnAt != null) return false;
      if (!entry) throw new Error(`Missing draw entry: ${candidate.ref.path}`);
      if (entry.state !== 'WAITING') return true;
      if (BigInt(current.waitingCount) <= 0n) throw new Error(`Invalid waiting count: ${ref.path}`);
      const selected = winners.has(candidate.id), type = selected ? 'LOTTERY_WON' : 'LOTTERY_LOST';
      const eventId = pair(type, lotteryId, candidate.id);
      tx.update(candidate.ref, { state: selected ? 'OFFERED' : 'LOST', stateChangedAt: now });
      tx.update(ref, { waitingCount: BigInt(current.waitingCount) - 1n });
      tx.set(db.doc(`notifications/${eventId}`), { senderId: null, type,
        subject: { type: 'LOTTERY', id: lotteryId }, announcement: null, createdAt: now });
      tx.set(ref.collection('_notificationDeliveries').doc(eventId), { recipientIds: [candidate.id] });
      return true;
    });
    if (!keepGoing) return;
  }
  await db.runTransaction(async tx => {
    const current = (await tx.get(ref)).data();
    if (!active(current) || current.drawnAt != null) return;
    if (BigInt(current.waitingCount) !== 0n) throw new Error(`Incomplete draw: ${ref.path}`);
    tx.update(ref, { drawnAt: now });
  });
}

module.exports = { drawLottery };
