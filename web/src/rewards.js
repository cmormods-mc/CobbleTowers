// Reward and vendor rules, as pure functions.

/** Which picture a reward gets: the currencies have no item, so they are named by id or label; anything else is an item. */
export function grantIcon(grant) {
  const text = (grant.label || '') + ' ' + grant.item;
  if (/dollar/i.test(text)) return 'coin';
  if (/raid/i.test(text)) return 'raid';
  return 'item';
}

/** The label under a service: the price, and how many are left when that is limited. */
export const stockLine = (e) => (e.remainingPurchases < 0 ? 'Stock: unlimited' : 'Stock: ' + e.remainingPurchases);

/** Whether the buy button works for this service and recipient (the server re-checks everything). */
export const canBuy = (vendor, e, who) => !!who && who.online && vendor.cobbleDollars >= e.priceCobbleDollars && e.remainingPurchases !== 0;

