/** Deliver captured recipients; leave unfinished work queued and never overwrite read state. */
async function deliverNotification({ db, jobPath }) {
  const ref = db.doc(jobPath), job = await ref.get();
  if (!job.exists) return;
  const event = (await db.doc(`notifications/${ref.id}`).get()).data();
  if (!event) throw new Error(`Missing notification for ${jobPath}`);
  if (event.type === 'LOTTERY_WON' || event.type === 'LOTTERY_LOST') {
    const lottery = (await db.doc(`lotteries/${event.subject.id}`).get()).data();
    if (!lottery) throw new Error(`Missing lottery for ${jobPath}`);
    if (lottery.drawnAt == null) {
      if (lottery.cancellation != null) await ref.delete();
      return;
    }
  }
  for (const recipient of job.get('recipientIds')) {
    const receipt = db.doc(`accounts/${recipient}/inbox/${ref.id}`);
    await db.runTransaction(async tx => {
      if (!(await tx.get(receipt)).exists) tx.create(receipt, { readAt: null });
    });
  }
  await ref.delete();
}

module.exports = { deliverNotification };
