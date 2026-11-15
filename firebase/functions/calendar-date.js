/*
 * Resolves campsite-local calendar dates using the booking service's pinned timezone data.
 */

const moment = require('moment-timezone');

/** Returns a local date; fixed UTC offsets keep their existing Intl interpretation. */
function calendarDate(instant, zone) {
  if (moment.tz.zone(zone)) return moment.tz(instant, zone).format('YYYY-MM-DD');
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: zone.replace(/^(UTC|GMT|UT)(?=[+-])/, ''), year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(instant);
}

module.exports = { calendarDate };
