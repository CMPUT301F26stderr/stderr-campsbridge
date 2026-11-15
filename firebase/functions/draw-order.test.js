const { test } = require('node:test');
const assert = require('node:assert/strict');
const { selectWinners } = require('./draw-order');
const vectors = require('../fixtures/draw-order-vectors.json');

test('shared vectors preserve UTF-8 ranking, signed seeds and exact large winner limits', () => {
  for (const vector of vectors) {
    assert.deepEqual(selectWinners({ ...vector, winnerCount: BigInt(vector.winnerCount) }), vector.winners);
    assert.deepEqual(selectWinners({ ...vector, accountIds: [...vector.accountIds].reverse(),
      winnerCount: BigInt(vector.winnerCount) }), vector.winners);
  }
});
