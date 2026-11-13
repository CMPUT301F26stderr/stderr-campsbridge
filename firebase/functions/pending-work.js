const { drawLottery } = require('./lottery-draw');
const { deliverNotification } = require('./notification-delivery');

/** Process independent work serially; report errors after giving other records their turn. */
async function runPendingWork({ db, now, newSeed }) {
  const failures = [];
  const due = await db.collection('lotteries').where('drawnAt', '==', null)
    .where('registrationClosesAt', '<=', now).get();
  for (const lottery of due.docs) {
    try {
      await drawLottery({ db, lotteryId: lottery.id, now, newSeed });
    } catch (failure) {
      failures.push(new Error(`Draw failed: ${lottery.ref.path}`, { cause: failure }));
    }
  }
  const jobs = await db.collectionGroup('_notificationDeliveries').get();
  for (const job of jobs.docs) {
    try {
      await deliverNotification({ db, jobPath: job.ref.path });
    } catch (failure) {
      failures.push(new Error(`Delivery failed: ${job.ref.path}`, { cause: failure }));
    }
  }
  if (failures.length) throw new AggregateError(failures, 'Pending Firebase work failed');
}

module.exports = { runPendingWork };
