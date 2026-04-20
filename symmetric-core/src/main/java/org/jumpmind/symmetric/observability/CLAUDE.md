# Observability Package

This package implements in-process metric collection and aggregation for SymmetricDS nodes.
It has three sub-packages: `models`, `metrics`, and `stats`.

---

## Package layout

```
observability/
├── models/      – plain data types (observations, keys, results)
├── metrics/     – metric instruments, queues, and service layer
├── repository/  – database serialization layer
└── stats/       – background aggregation into 5-minute windows
```

---

## models — data types

### Raw observations

Every change to a metric value produces one observation. Observations carry the **new value** and the **wall-clock timestamp** of the change. They are immutable value types.

| Type | Value type | Used by |
|------|-----------|---------|
| `ISymObservation` | interface — `getTimestamp()` | all metric types |
| `ObservationLong(long value, long ms)` | `long` | counters (`UpDownCounter`, `IncreasingCounter`) |
| `ObservationDouble(double value, long ms)` | `double` | gauges (`SymDoubleGauge`) |

### Aggregation model types

| Type | Description |
|------|-------------|
| `MetricKey(hostname, engineName, metricId)` | Composite key that uniquely identifies one metric series |
| `MetricSeries(metricAttrId, key)` | Pairs the DB surrogate ID with a `MetricKey` |
| `MetricIntervalStats` | Immutable record of one closed 5-minute window: `intervalStart`, `intervalEnd`, `avg` (time-weighted), `min`, `max`, `stdDev`, `mean` (arithmetic), `observationCount`, `isOutlier` |
| `MetricIntervalStatsRecord(key, stats)` | Pairs a `MetricIntervalStats` with its `MetricKey` for bulk DB writes |

---

## metrics — instruments and services

### Metric instruments

All instruments extend `AbstractQueuedMetric`, which holds an `ObservationsQueue` and exposes
`removeAllObservations()` for the aggregator to drain.

```
AbstractQueuedMetric  (implements ISymMetric)
├── AbstractCounterMetric   – long value; add(delta) / increment()
│   ├── UpDownCounter       – accepts positive and negative deltas; wraps LongUpDownCounter
│   └── IncreasingCounter   – rejects negative deltas; wraps LongCounter
└── AbstractGaugeMetric     – double value; setValue(v) / add(delta)
    └── SymDoubleGauge      – wraps DoubleGauge
```

Every call to `add()`, `increment()`, `setValue()`, or `add()` on a gauge:
1. Updates the instrument's `currentValue` atomically.
2. Creates an `ObservationLong` or `ObservationDouble` and enqueues it in `ObservationsQueue`.
3. If OTel publishing is enabled, publishes the delta/value to the OpenTelemetry SDK immediately.

`ObservationsQueue` is a bounded `ConcurrentLinkedQueue` (max 10 million entries).

### Service layer

| Type | Role |
|------|------|
| `IMetricsService` | Base interface — `saveCompletedIntervalStats()`, `shutdown()` |
| `IEngineMetricsService` | Engine-scoped extension — `getEngineName()`, `getOrCreate*()`, `getAllMetrics()`, `saveCompletedIntervalStats()` |
| `AbstractMetricsService` | Base implementation — owns the `UpDownCounter` and `SymDoubleGauge` maps; creates OTel instruments on demand |
| `EngineMetricsService` | Concrete engine-scoped service; registers itself with `MetricsManager` on construction, unregisters on `shutdown()`; implements `saveCompletedIntervalStats()` by persisting completed intervals via `MetricsRepository` |
| `HostMetricsService` | Host-scoped service (not engine-specific); `saveCompletedIntervalStats()` is a no-op — host metrics are in-memory only |

### MetricsManager

Singleton (`getGlobalInstance()`) that owns the OTel `Meter`, the list of registered
`IEngineMetricsService` instances, and the `PrimaryMetricAggregator` lifecycle.

- `register(svc)` / `unregister(svc)` — called by `EngineMetricsService` constructor/shutdown.
- `getEngineMetricsServices()` — read by `PrimaryMetricAggregator` during each processing cycle.
- `startAggregation()` — creates and starts the `PrimaryMetricAggregator` daemon thread.
- `shutdown()` — stops the aggregator, then tears down the OTel SDK if it was auto-initialized.

---

## stats — aggregation

### MetricIntervalAccumulator (package-private)

Mutable state for one in-progress 5-minute window. Uses a **step-function model**: the metric is
assumed to hold its last known value continuously until the next observation arrives.

Key fields: `weightedSum`, `weightedSumOfSquares`, `totalWeightMs` (for time-weighted avg and
std dev), `min`, `max`, `count`, plus `lastValue`/`lastTimestamp` for carry-forward.

- `addObservation(obs)` — credits the *previous* value for `(ts − lastTimestamp)` ms, then records the new value.
- `close()` / `closeAt(endTs)` — credits the last value for any remaining time up to the window boundary.
- `toMetricIntervalStats()` — produces an immutable `MetricIntervalStats`.

### MetricSeriesSlidingWorkset

Per-metric sliding window of recent closed intervals used for IQR-based outlier detection.
When a new interval closes, `workset.detectOutlier(interval)` is called; if flagged, the interval
is cloned with `isOutlier = true` before being enqueued. Seeded from DB history at service
initialization via `AbstractQueuedMetric.prewarmWorkset()`.

### PrimaryMetricAggregator

Runs on a single daemon thread (`"metrics-primary-aggregator"`). Lifecycle: `start()` / `stop()`.

Processing cycle (every **10 s**):
1. For each registered `IEngineMetricsService`, iterate over all its metrics.
2. For each metric, drain the raw observation queue with `removeAllObservations()`.
3. Feed the observations into `processObservations()`, which assigns each to the current
   5-minute `MetricIntervalAccumulator`. When an observation falls into a later bucket, the
   current accumulator is closed, run through outlier detection, and the resulting
   `MetricIntervalStats` is enqueued in the metric's `completedIntervals` queue. Carry-forward
   values fill any empty intermediate buckets.
4. After all metrics for a service are processed, call `svc.saveCompletedIntervalStats()` to persist any
   completed intervals to the database (see below).

On `stop()`: sets `running = false` and interrupts the thread. After the interrupt, one final
`processAll()` runs to drain buffered observations before the thread exits.

### MetricsRepository

Handles all DB access for a single engine. Owned by `EngineMetricsService`, initialized lazily
on the first aggregation cycle that has data to persist.

On first use, `ensureCacheLoaded()` reads `sym_metric_key` for this host/engine into an
in-memory `Map<MetricKey, Long>` (surrogate ID cache) and seeds `nextAttrId` from
`MAX(metric_key)`.

`saveIntervals(records)` writes a batch within a single transaction:
- Phase 1 — for each `MetricKey` not yet in the cache, `INSERT INTO sym_metric_key` and update
  the cache. Surrogate IDs are allocated from the in-process `AtomicLong`; no DB sequence needed.
- Phase 2 — batch-`INSERT INTO sym_metric_interval`, one row per `MetricIntervalStats`.

| Table | Columns |
|-------|---------|
| `{prefix}_metric_key` | `metric_key` (BIGINT PK), `hostname`, `engine_name`, `metric_id` |
| `{prefix}_metric_interval` | `metric_key` (FK), `interval_start` (PK with attr_id), `interval_end`, `avg`, `min`, `max`, `std_dev`, `observation_count` |

`loadRecentIntervals(key)` returns the most recent N intervals for a key (oldest-first) to
seed a freshly created `MetricSeriesSlidingWorkset`.

---

## End-to-end data flow — engine-specific metric

```
═══ APPLICATION THREAD ════════════════════════════════════════════════════════

  EngineMetricsService.getOrCreateUpDownCounter(metricId, desc, unit)
    └─ first call: new UpDownCounter(metricId, otelCounter, attributes)
                   registered in ConcurrentHashMap<String, UpDownCounter>

  counter.add(delta)  /  gauge.setValue(v)
    ├─ currentValue updated atomically (AtomicLong / DoubleAdder)
    ├─ new ObservationLong(newValue, System.currentTimeMillis())
    │    enqueued in ObservationsQueue (bounded ConcurrentLinkedQueue, max 10M)
    └─ if OTel enabled: otelCounter.add(delta, attributes) → OTel SDK (immediate)

═══ "metrics-primary-aggregator" THREAD  (every 10 s) ════════════════════════

  PrimaryMetricAggregator.processAll()
  │
  └─ for each IEngineMetricsService registered with MetricsManager:
       │
       ├─ for each AbstractQueuedMetric (UpDownCounter / SymDoubleGauge):
       │    │
       │    ├─ removeAllObservations()
       │    │    └─ atomically swaps ObservationsQueue for a fresh empty one;
       │    │       returns old queue contents as ISymObservation[]
       │    │
       │    └─ processObservations(obs[])
       │         └─ for each observation:
       │              ├─ calculate 5-min bucket start for observation.timestamp
       │              ├─ if no open accumulator → new MetricIntervalAccumulator(bucketStart)
       │              ├─ if observation is in-scope for the open accumulator
       │              │    └─ accumulator.addObservation(obs)   [step-function credit]
       │              └─ if observation is in a LATER bucket (interval rollover):
       │                   ├─ accumulator.close()               [credit remaining time]
       │                   ├─ workset.detectOutlier(stats)      [IQR-based flag]
       │                   ├─ completedIntervals.add(stats)     [MetricIntervalStatsQueue]
       │                   ├─ open new MetricIntervalAccumulator(nextBucketStart,
       │                   │                                    carryForwardValue)
       │                   │   (repeat for any empty intermediate buckets)
       │                   └─ accumulator.addObservation(obs)   [record in new bucket]
       │
       └─ svc.saveCompletedIntervalStats()
            └─ EngineMetricsService.saveCompletedIntervalsForAllMetrics()
                 │
                 ├─ getOrInitRepository()   [lazy; first call only]
                 │    ├─ new MetricsRepository(engine, hostname)
                 │    ├─ ensureCacheLoaded(): SELECT metric_key → in-memory attrId cache
                 │    ├─ initializeImportantMetrics(repo)
                 │    │    ├─ getOrCreateUpDownCounter(METRIC_CONNECTIONS_RESERVATIONS_ID)
                 │    │    └─ prewarmAllWorksets(repo):
                 │    │         for each metric → loadRecentIntervals(key) → workset.seed()
                 │    └─ (repository now ready for subsequent cycles)
                 │
                 ├─ for each AbstractQueuedMetric:
                 │    └─ metric.exportCompletedIntervals(key)
                 │         └─ completedIntervals.exportAll()   [atomic queue swap]
                 │              → List<MetricIntervalStats>
                 │
                 └─ MetricsRepository.saveIntervals(List<MetricIntervalStatsRecord>)
                      │
                      ├─ [single DB transaction]
                      ├─ Phase 1 — for each new MetricKey:
                      │    └─ INSERT INTO sym_metric_key (attr_id, hostname, engine_name, metric_id)
                      │         attr_id allocated from in-process AtomicLong
                      │         result cached in attrIdCache
                      └─ Phase 2 — batch INSERT INTO sym_metric_interval
                           one row per MetricIntervalStats:
                           (metric_key, interval_start, interval_end,
                            avg, min, max, std_dev, observation_count)
```

### Interval close timing

An accumulator closes only when `processObservations()` receives an observation whose timestamp
falls in a **later** N-minute bucket. For metrics with sparse observations (less than observation one per
interval) an accumulator may remain open longer than its window. Calling
`metric.saveCompletedIntervals()` (which delegates to `closeExpiredAccumulatorIfNeeded(now)`)
explicitly closes any accumulator whose window has already ended, ensuring intervals are not
delayed waiting for a future observation to trigger the rollover.
