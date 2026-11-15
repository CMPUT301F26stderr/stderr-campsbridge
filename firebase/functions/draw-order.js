const { createHash } = require('node:crypto');

/** Select from unique account IDs using the same UTF-8 digest and ordering as Kotlin. */
function selectWinners({ seed, accountIds, winnerCount }) {
  const ranked = accountIds.map(accountId => ({ accountId,
    hash: createHash('sha256').update(`${seed}:${accountId}`, 'utf8').digest('hex') }));
  ranked.sort((left, right) => {
    if (left.hash !== right.hash) return left.hash < right.hash ? -1 : 1;
    return left.accountId < right.accountId ? -1 : left.accountId > right.accountId ? 1 : 0;
  });
  const limit = Number(winnerCount < BigInt(ranked.length) ? winnerCount : BigInt(ranked.length));
  return ranked.slice(0, limit).map(value => value.accountId);
}

module.exports = { selectWinners };
