# Upgrading and backups

## Before you upgrade

Read the [changelog](https://github.com/itobey/fddb-exporter/blob/master/CHANGELOG.md) for the versions you are skipping
over. Breaking changes are called out there, and they have so far been about **configuration property names** rather
than about stored data — for example, Spring Boot 4 moved the MongoDB settings from `spring.data.mongodb.*` to
`spring.mongodb.*`, which means `SPRING_DATA_MONGODB_HOST` became `SPRING_MONGODB_HOST`.

A property that no longer exists is not an error: the application starts with the built-in default instead. That is how
an upgrade ends up connecting to `localhost` and finding nothing, with no obvious complaint. After an upgrade, compare
your environment against the [configuration reference](/details/configuration.md) and check the startup log for the
values actually in use.

The application tells you when a new release exists — the Web UI shows a notice in the sidebar menu and the check is
logged. The check runs on startup and on a schedule.

### Duplicate days must be removed before 2.5.0 <Badge type="warning" text="2.5.0+" />

From 2.5.0 the `date` field carries a **unique index**, created automatically on startup. That is what enforces "one
entry per calendar day" — until then it was only application logic, and a race between two concurrent exports could
leave two documents for the same day, quietly skewing every average and count for it.

If your database already holds such a duplicate, MongoDB refuses to build the index and logs the failure on startup.
Check first, and clean up if the query returns anything:

```javascript
// mongosh, against your fddb database
db.fddb.aggregate([
  { $group: { _id: "$date", ids: { $push: "$_id" }, count: { $sum: 1 } } },
  { $match: { count: { $gt: 1 } } }
])
```

```javascript
// removes all but the newest document per duplicated date - take a dump first
db.fddb.aggregate([
  { $group: { _id: "$date", ids: { $push: "$_id" }, count: { $sum: 1 } } },
  { $match: { count: { $gt: 1 } } }
]).forEach(d => db.fddb.deleteMany({ _id: { $in: d.ids.slice(0, -1) } }));
```

Restart afterwards so the index is created. A second, non-unique index on `products.name` is created at the same time
and needs nothing from you.

## Upgrading

::: tip Pin a version
`:latest` makes an upgrade happen whenever the image is pulled, which is rarely when you intended. Pin the tag and
change it deliberately.
:::

### Docker

```bash
docker compose pull
docker compose up -d
```

### Helm

```bash
helm upgrade fddb-exporter oci://ghcr.io/itobey/charts/fddb-exporter --version __DOCS_VERSION__ --reuse-values
```

Drop `--reuse-values` and pass your own values file if you keep one, which is the more predictable habit. The chart is
versioned in [its own repository](https://artifacthub.io/packages/helm/fddb-exporter/fddb-exporter) — check there if the
version above does not resolve.

### Jar

Download the new jar from the [releases page](https://github.com/itobey/fddb-exporter/releases) and restart with it.
Nothing is stored next to the jar, so there is nothing to migrate on disk.

## What a version bump touches

- **Your data:** nothing. The exporter only ever inserts days or updates them in place. Documents written by an older
  version are read by a newer one. The one clean-up ever required is the duplicate-day removal for 2.4.0 above.
- **Your configuration:** possibly. See the changelog note above.
- **The database schema:** MongoDB has none to migrate. New fields simply appear on newly written documents; older
  documents keep whatever they were written with, so a field added in a later version is absent for older days until you
  re-export them. Indexes are the one exception — see the duplicate-day cleanup above for 2.4.0.
- **UI preferences** (custom rolling-average presets) live in MongoDB alongside the data and survive upgrades.

## Backing up

The exported data is the part worth backing up. Your fddb.info account still has the original for the last one to two
years, but the whole point of this tool is keeping data beyond that window — once FDDB drops it, your database is the
only copy.

### MongoDB

```bash
# dump (adjust host, credentials and database to your setup)
mongodump --uri="mongodb://mongodb_fddb_user:mongodb_fddb_password@localhost:27017/fddb?authSource=admin" \
          --out=/backup/fddb-$(date +%F)

# restore
mongorestore --uri="mongodb://mongodb_fddb_user:mongodb_fddb_password@localhost:27017/fddb?authSource=admin" \
             /backup/fddb-2026-01-31/fddb
```

Running against a containerised MongoDB:

```bash
docker exec mongodb mongodump --archive --db=fddb --username=... --password=... > fddb-$(date +%F).archive
```

`--authenticationDatabase` / `authSource` depends on how the MongoDB user was created; if authentication fails, try
`admin` and then the `fddb` database itself.

### InfluxDB

InfluxDB holds only the daily totals, which can be rebuilt from MongoDB (see below), so it is the less critical of the
two. To back it up anyway:

```bash
influx backup /backup/influx-$(date +%F) --bucket fddb-exporter --token <TOKEN>
```

### The cheap alternative

The [download endpoint](/details/rest-api.md#download-data-in-various-formats) and the Web UI's **Download Data** page
produce a single CSV or JSON file with everything, products included:

```bash
curl -o fddb-backup.json 'http://localhost:8080/api/v2/fddbdata/download?format=JSON&includeProducts=true'
```

This is not a restorable dump — there is no import endpoint — but it is a portable copy of the data that any tool can
read, and it takes one command. Keep it alongside a real dump rather than instead of one.

## Rebuilding one store from the other

If you enable InfluxDB after having exported data for a while, migrate rather than re-scrape:

```bash
curl -X POST http://localhost:8080/api/v2/migration/toInfluxDb
```

This reads the daily totals out of MongoDB and writes the InfluxDB points, so fddb.info is not touched at all. There is
no migration in the other direction — InfluxDB never had the product lists to give back.

## Re-exporting instead of restoring

Anything still inside your fddb.info retention window can simply be scraped again — days are updated in place, so
re-exporting is safe and never duplicates:

```bash
curl -X POST http://localhost:8080/api/v2/fddbdata \
     -H 'Content-Type: application/json' \
     -d '{"fromDate":"2024-01-01","toDate":"2024-12-31"}'
```

Each day is one request to fddb.info and takes about a second, so a year takes a few minutes. Only one export runs at a
time — see [only one export at a time](/details/exports-and-data.md#only-one-export-at-a-time).

## Downgrading

Going back to an earlier image works: no release has changed how data is stored, so an older version reads documents a
newer one wrote. What does not come back is configuration — if the newer version renamed a property, the older one
needs the old spelling again.
