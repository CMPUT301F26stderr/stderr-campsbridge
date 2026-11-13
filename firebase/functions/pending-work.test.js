const { test, beforeEach, after } = require('node:test');
const assert = require('node:assert/strict');
const { initializeApp, deleteApp, getApps } = require('firebase-admin/app');
const { getFirestore, Timestamp } = require('firebase-admin/firestore');
const { runPendingWork } = require('./pending-work');
const { drawLottery } = require('./lottery-draw');
const { deliverNotification } = require('./notification-delivery');
const vectors = require('../fixtures/draw-order-vectors.json');

assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Worker persistence tests require the disposable Firestore emulator');
const app = initializeApp({ projectId: 'demo-stderr' }, 'worker-contracts');
const db = getFirestore(app);
db.settings({ useBigInt: true });
const now = new Timestamp(1800000000, 123456000);
const at = offset => new Timestamp(now.seconds + offset, now.nanoseconds);
const pair = (...ids) => ids.map(id => `${id.length}:${id}`).join('');
const get = async path => (await db.doc(path).get()).data();
const tick = () => runPendingWork({ db, now, newSeed: () => '17' });
const vector = vectors.find(value => value.seed === '17' && value.accountIds.length);
const outcome = account => vector.winners.includes(account) ? 'OFFERED' : 'LOST';
const noticeId = (lottery, account) => pair(outcome(account) === 'OFFERED' ? 'LOTTERY_WON' : 'LOTTERY_LOST', lottery, account);

beforeEach(async () => {
  const response = await fetch(`http://${process.env.FIRESTORE_EMULATOR_HOST}/emulator/v1/projects/demo-stderr/databases/(default)/documents`, { method: 'DELETE' });
  assert.equal(response.status, 200);
});
after(async () => {
  for (const instance of getApps()) {
    await getFirestore(instance).terminate();
    await deleteApp(instance);
  }
});

/** Seed the current lottery schema without depending on app creation or publication behavior. */
async function seedDraw(id, overrides = {}, accounts = vector.accountIds) {
  await db.doc(`lotteries/${id}`).set({ ownerId: 'host', participantOwnerIds: ['host'], title: 'River weekend', description: 'Two quiet nights',
    registrationOpensAt: at(-3600), registrationClosesAt: now, bookingOpensAt: now, bookingClosesAt: at(3600),
    winnerCount: 1n, waitlistCapacity: null, waitingCount: BigInt(accounts.length), createdAt: at(-7200),
    publishedAt: at(-3600), drawnAt: null, cancellation: null, _revision: 0n, ...overrides });
  for (const account of accounts) await db.doc(`lotteries/${id}/entries/${account}`).set({
    accountId: account, state: 'WAITING', joinedAt: at(-1800), stateChangedAt: at(-1800),
  });
}

/** Queue an existing event exactly as the mobile transaction writes it. */
async function seedJob(parent, id, recipients, event = {}) {
  await db.doc(`notifications/${id}`).set({ senderId: 'host', type: 'OWNER_ANNOUNCEMENT',
    subject: { type: 'LOTTERY', id: 'draw' }, announcement: { title: 'Arrival', body: 'Meet at the gate.' }, createdAt: at(-30), ...event });
  const path = `${parent}/_notificationDeliveries/${id}`;
  await db.doc(path).set({ recipientIds: recipients });
  return path;
}

test('a tick draws due and empty lotteries, preserves withdrawals and skips future or inactive records', async () => {
  await seedDraw('due');
  await db.doc('lotteries/due/entries/withdrawn').set({ accountId: 'withdrawn', state: 'WITHDRAWN', joinedAt: at(-1800), stateChangedAt: at(-1) });
  for (const [id, overrides] of Object.entries({ future: { registrationClosesAt: at(1), bookingOpensAt: at(1) }, draft: { publishedAt: null },
    cancelled: { cancellation: { actorId: 'host', at: at(-1), reason: null } },
    complete: { registrationClosesAt: at(-60), bookingOpensAt: at(-60), drawnAt: at(-1), waitingCount: 0n } })) {
    await seedDraw(id, overrides, []);
  }
  await seedDraw('empty', {}, []);
  await tick();
  assert.deepEqual((await get('lotteries/due')).drawnAt, now);
  assert.equal((await get('lotteries/due')).waitingCount, 0n);
  assert.deepEqual((await get('lotteries/empty')).drawnAt, now);
  for (const account of vector.accountIds) {
    assert.equal((await get(`lotteries/due/entries/${account}`)).state, outcome(account));
    assert.deepEqual(await get(`accounts/${account}/inbox/${noticeId('due', account)}`), { readAt: null });
  }
  assert.equal((await get('lotteries/due/entries/withdrawn')).state, 'WITHDRAWN');
  assert.equal(await get(`accounts/withdrawn/inbox/${pair('LOTTERY_LOST', 'due', 'withdrawn')}`), undefined);
  for (const id of ['future', 'draft', 'cancelled']) assert.equal((await get(`lotteries/${id}`)).drawnAt, null);
  assert.deepEqual((await get('lotteries/complete')).drawnAt, at(-1));
});

test('resuming a partial draw retains its seed, earlier outcome and event timestamps', async () => {
  await seedDraw('partial', { registrationClosesAt: at(-60), bookingOpensAt: at(-60), waitingCount: 2n });
  await db.doc('_draws/partial').set({ seed: '17' });
  const account = vector.accountIds[0], eventId = noticeId('partial', account);
  await db.doc(`lotteries/partial/entries/${account}`).update({ state: outcome(account), stateChangedAt: at(-20) });
  await seedJob('lotteries/partial', eventId, [account], { senderId: null,
    type: outcome(account) === 'OFFERED' ? 'LOTTERY_WON' : 'LOTTERY_LOST', subject: { type: 'LOTTERY', id: 'partial' },
    announcement: null, createdAt: at(-20) });
  await db.doc(`accounts/${account}/inbox/${eventId}`).set({ readAt: at(-10) });
  await tick();
  assert.deepEqual(await get('_draws/partial'), { seed: '17' });
  assert.deepEqual((await get(`lotteries/partial/entries/${account}`)).stateChangedAt, at(-20));
  assert.deepEqual((await get(`notifications/${eventId}`)).createdAt, at(-20));
  assert.deepEqual((await get(`accounts/${account}/inbox/${eventId}`)).readAt, at(-10));
  for (const id of vector.accountIds) assert.equal((await get(`lotteries/partial/entries/${id}`)).state, outcome(id));
  assert.equal((await get('lotteries/partial')).waitingCount, 0n);
  assert.ok((await get('lotteries/partial')).drawnAt);
});

test('overlapping workers converge and replay does not recreate consumed jobs or change saved outcomes', async () => {
  await seedDraw('draw');
  await Promise.all([tick(), tick()]);
  const before = await get('lotteries/draw');
  const entries = await db.collection('lotteries/draw/entries').get();
  await runPendingWork({ db, now: at(60), newSeed: () => '99' });
  assert.deepEqual(await get('lotteries/draw'), before);
  assert.deepEqual((await db.collection('lotteries/draw/entries').get()).docs.map(row => row.data()), entries.docs.map(row => row.data()));
  assert.equal((await db.collection('lotteries/draw/_notificationDeliveries').get()).size, 0);
  assert.equal((await db.collection('notifications').get()).size, vector.accountIds.length);
});

test('draw delivery waits for completion and discards cancelled unfinished results', async () => {
  await seedDraw('draw');
  const path = await seedJob('lotteries/draw', 'won', ['a'], { senderId: null, type: 'LOTTERY_WON', announcement: null });
  await deliverNotification({ db, jobPath: path });
  assert.ok(await get(path));
  assert.equal(await get('accounts/a/inbox/won'), undefined);
  await db.doc('lotteries/draw').update({ cancellation: { actorId: 'host', at: now, reason: null } });
  await deliverNotification({ db, jobPath: path });
  assert.equal(await get(path), undefined);
  assert.equal(await get('accounts/a/inbox/won'), undefined);
});

test('delivery resumes captured audiences across both parent types and preserves existing read receipts', async () => {
  const first = await seedJob('lotteries/draw', 'announcement', ['a', 'b']);
  const second = await seedJob('reservations/stay', 'message', ['b'], {
    type: 'NEW_MESSAGE', subject: { type: 'MESSAGE', id: 'message-body' }, announcement: null });
  await db.doc('accounts/a/inbox/announcement').set({ readAt: at(-10) });
  await Promise.all([tick(), tick()]);
  assert.deepEqual(await get('accounts/a/inbox/announcement'), { readAt: at(-10) });
  assert.deepEqual(await get('accounts/b/inbox/announcement'), { readAt: null });
  assert.deepEqual(await get('accounts/b/inbox/message'), { readAt: null });
  assert.equal(await get(first), undefined);
  assert.equal(await get(second), undefined);
  await deliverNotification({ db, jobPath: first });
  assert.deepEqual(await get('accounts/a/inbox/announcement'), { readAt: at(-10) });
});

test('broken durable work reports failure, remains queued and does not prevent independent delivery', async () => {
  await db.doc('lotteries/draw/_notificationDeliveries/broken').set({ recipientIds: ['a'] });
  const good = await seedJob('reservations/stay', 'good', ['b']);
  await assert.rejects(tick());
  assert.ok(await get('lotteries/draw/_notificationDeliveries/broken'));
  assert.equal(await get(good), undefined);
  assert.deepEqual(await get('accounts/b/inbox/good'), { readAt: null });
});

test('the scheduled export executes persisted work using production time without a mobile session', async () => {
  const clock = Date.now();
  await seedDraw('scheduled', { createdAt: Timestamp.fromMillis(clock - 7200000),
    publishedAt: Timestamp.fromMillis(clock - 3600000), registrationOpensAt: Timestamp.fromMillis(clock - 3600000),
    registrationClosesAt: Timestamp.fromMillis(clock - 10000), bookingOpensAt: Timestamp.fromMillis(clock - 10000),
    bookingClosesAt: Timestamp.fromMillis(clock + 3600000) });
  const scheduled = require('./index').processPendingWork;
  assert.equal(typeof scheduled?.run, 'function');
  await scheduled.run({});
  assert.ok((await get('lotteries/scheduled')).drawnAt);
  for (const account of vector.accountIds) {
    const state = (await get(`lotteries/scheduled/entries/${account}`)).state;
    assert.ok(['OFFERED', 'LOST'].includes(state));
    assert.ok(await get(`accounts/${account}/inbox/${pair(state === 'OFFERED' ? 'LOTTERY_WON' : 'LOTTERY_LOST', 'scheduled', account)}`));
  }
});
