# Performance tuning

Everything below is optional: each setting has a default, and each can be switched off or tuned without code changes
(Spring property, or environment variable such as `AUTOMATA_DATA_BATCH_LINGER_MS`).
Measure before and after with the k6 suite in `loadtest/`.

| Property | Default | Effect |
|---|---|---|
| `automata.data.batch.enabled` | `true` | Batch the MQTT `sendData` inserts. `false` = one synchronous insert per message (old behaviour). |
| `automata.data.batch.max-size` | `500` | Largest batch (documents per `insertMany`). |
| `automata.data.batch.linger-ms` | `200` | Longest a reading waits for its batch to fill. This is the added write latency. |
| `automata.data.batch.queue-capacity` | `20000` | Readings buffered in memory. When full, callers insert synchronously (back-pressure; nothing is dropped). |
| `automata.data.retention-days` | `400` | New readings get `expireAt = now + N days`; MongoDB deletes them after that. `0` = keep forever. |
| `automata.mongo.pool.max-size` | `100` | Maximum MongoDB connections. |
| `automata.mongo.pool.min-size` | `10` | Connections kept warm. |
| `automata.mongo.pool.max-wait-ms` | `5000` | How long a request waits for a connection before failing (driver default is 120 000). |
| `automata.mongo.pool.max-connecting` | `4` | Connections that may be *opened* concurrently (driver default 2). |
| `automata.auth.user-cache-ttl-seconds` | `60` | JWT filter user cache. `0` disables it. |
| `automata.auth.user-cache-max-size` | `10000` | Entries kept in that cache. |
| `automata.executor.automation.threads` / `.queue` | `8` / `500` | Automation evaluation pool. |
| `automata.executor.live-data.threads` / `.queue` | `4` / `1000` | MQTT ingestion pool. |

## Batched inserts (durability trade-off)

Readings from `sendData` are queued and written with one `insertMany` per batch. The queue lives in memory, so
**readings still queued when the process is killed abruptly (`kill -9`, OOM, power loss) are lost** — at most about
`linger-ms` worth of data. A normal shutdown (SIGTERM, `docker stop`) flushes the queue first.

Other callers (`/save/{deviceId}`, WLED polling, system metrics, anything calling `MainService.saveData`) are unchanged
and still write synchronously.

If Micrometer/Actuator is exposed, these meters show whether batching keeps up:
`automata.data.buffer.queued` (should hover near 0), `.written`, `.batches`, `.overflow` (should stay 0 — rising means
the queue is full and callers are writing synchronously), `.dropped` (must stay 0).

## TTL on `data`

`Data.expireAt` carries a MongoDB TTL index (`expireAfter = 0s`: the document is deleted once that instant passes).
It is a separate field because `updateDate` already has a plain index and MongoDB rejects a TTL index on the same key.

**Why 400 days:** `AnalyticsServiceV2` aggregates the raw `data` collection for every range, including `year` (365 days).
A shorter retention would silently truncate the yearly charts. Lower it only if you also add roll-ups (see below).

**Existing data is not touched.** Only readings written after this change get `expireAt`. To apply retention to history,
run once, ideally off-peak and per device or date range on a large collection (it rewrites every document):

```js
// mongosh — stamp existing readings (400 days after they were written)
const ms = 400 * 24 * 3600 * 1000;
db.data.updateMany(
  { expireAt: { $exists: false } },
  [{ $set: { expireAt: { $add: ["$updateDate", ms] } } }]
);
```

The first TTL pass after that deletes everything already past retention in one go, which can briefly load the disk.
MongoDB's TTL monitor runs about once a minute.

Longer term: keep raw readings for weeks and serve long ranges from hourly/daily roll-ups instead of scanning raw data.

## MongoDB pool sizing

Request concurrency is effectively unbounded (virtual threads), so the pool is the real limit on database concurrency.
Start with the defaults and watch for requests failing with a pool-wait timeout under load:

* raise `max-size` only if the server has headroom — it must stay below the server's `net.maxIncomingConnections`,
  and each connection costs the server memory (matters on a Raspberry Pi);
* keep `min-size` at roughly your steady-state concurrency so a burst does not pay connection-setup latency;
* a short `max-wait-ms` turns overload into fast, visible errors instead of requests queueing for two minutes.

## Authentication

* The JWT filter parses each token once (it used to parse three times, rebuilding the key and parser each time) and
  reads the user from a short-lived cache instead of MongoDB on every request.
* The cache hands every request its own copy of the user, and is used by the filter only — login still uses the real
  `UserDetailsService`.
* A change to a user (role, deletion) can take up to the TTL to reach requests already carrying a valid token.
  Call `UserPrincipalCache.invalidate(email)` wherever users are modified for immediate effect on that node.
* Login no longer runs a second `findByEmail` right after the password check.

## Known gap: `deviceActionState` never expires

`DeviceActionState.expireAt` has a plain `@Indexed`, and the comment above it says to create the TTL index by hand — on
`device_action_states`. The class has no `@Document`, so the collection is actually named **`deviceActionState`**, which
means that command (if it was ever run) targeted a collection that does not exist. These audit rows are written on every
automation dispatch and are probably growing without bound.

Do not just add `expireAfter` to the annotation: the existing plain index would conflict and startup would fail. Convert
the existing index once instead (MongoDB 5.1+), then it is self-maintaining:

```js
db.runCommand({
  collMod: "deviceActionState",
  index: { keyPattern: { expireAt: 1 }, expireAfterSeconds: 0 }
});
```
