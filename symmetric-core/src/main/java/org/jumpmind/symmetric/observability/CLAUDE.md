# Observability Package

This package implements in-process metric collection and aggregation for SymmetricDS nodes.
It has three sub-packages: `models`, `metrics`, and `stats`.

---

## Package layout

```
observability/
├── models/      – plain data types (observations, keys, results)
├── metrics/     – metric instruments, queues, and service layer
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
| `MetricKey(hostname, engineName, metricId)` | Composite key that uniquely identifies one metric time-series across the cluster |
| `MetricInterval(intervalStart, intervalEnd, avg, min, max, stdDev, observationCount)` | Immutable result of one closed 5-minute aggregation window for a single `MetricKey`. `avg` is time-weighted (see *stats* section). |

---

## metrics — instruments and services

### Metric instruments

All instruments extend `AbstractQueuedMetric`, which holds an `ObservationsQueue` and exposes
`removeAllObservations()` for the aggregator to drain.

```
AbstractQueuedMetric  (implements ISymMetric)
├── AbstractCounter   – long value; add(delta) / increment()
│   ├── UpDownCounter       – accepts positive and negative deltas; wraps LongUpDownCounter
│   └── IncreasingCounter   – rejects negative deltas; wraps LongCounter
└── AbstractGauge     – double value; setValue(v) / add(delta)
    └── SymDoubleGauge      – wraps DoubleGauge
```

Every call to `add()`, `increment()`, `setValue()`, or `add()` on a gauge:
1. Updates the instrument's `currentValue` atomically.
2. Creates an `ObservationLong` or `ObservationDouble` and enqueues it.
3. If OTel publishing is enabled, publishes the delta/value to the OpenTelemetry SDK immediately.

`ObservationsQueue` is a bounded `ConcurrentLinkedQueue` (max 10 million entries). It also
provides `peekBetween(start, end)` and `removeAllBetween(start, end)` for time-windowed access.

### Service layer

| Type | Role |
|------|------|
| `IMetricsService` | Base interface — `shutdown()` |
| `IEngineMetricsService` | Engine-scoped extension — `getEngineName()`, `getOrCreate*()`, `getAllMetrics()` |
| `AbstractMetricsService` | Base implementation — owns the `UpDownCounter` and `SymDoubleGauge` maps; creates OTel instruments on demand |
| `EngineMetricsService` | Concrete engine-scoped service; registers itself with `MetricsManager` on construction, unregisters on `shutdown()` |
| `HostMetricsService` | Host-scoped service (not engine-specific); created and owned by `MetricsManager`, not registered in the engine list |

### MetricsManager

Singleton (`getGlobalInstance()`) that owns the OTel `Meter`, the list of registered
`IEngineMetricsService` instances, and the `MetricAggregator` lifecycle.

- `register(svc)` / `unregister(svc)` — called by `EngineMetricsService` constructor/shutdown.
- `getEngineMetricsServices()` — read by `MetricAggregator` during each processing cycle.
- `startAggregation()` — creates and starts the `MetricAggregator` daemon thread.
- `shutdown()` — stops the aggregator, then tears down the OTel SDK if it was auto-initialized.

---

## stats — aggregation

### MetricIntervalAccumulator (package-private)

Mutable state for one in-progress 5-minute window. Uses a **step-function model**: the metric is
assumed to hold its last known value continuously until the next observation arrives.

Key fields: `weightedSum`, `weightedSumOfSquares`, `totalWeightMs` (for time-weighted avg and
std dev), `min`, `max`, `count`, plus `lastValue`/`lastTimestamp` for carry-forward.

- `addObservation(value, ts)` — credits the *previous* value for `(ts − lastTimestamp)` ms, then
  records the new value.
- `closeAt(endTs)` — credits the last value for any remaining time up to the window boundary.
- `toMetricInterval()` — produces an immutable `MetricInterval`.

### MetricAggregator

Runs on a single daemon thread (`"metrics-aggregator"`). Lifecycle: `start()` / `stop()`.

Processing cycle (every 30 s):
1. For each registered `IEngineMetricsService`, call `getAllMetrics()`.
2. For each metric, drain the queue with `removeAllObservations()`.
3. Assign each observation to the correct 5-minute bucket accumulator (keyed by `MetricKey`).
4. When an observation belongs to a later bucket than the current open one, close the current
   accumulator, fill any empty intermediate buckets with carry-forward values, and open a new one.
5. After processing all observations, close any accumulators whose window has expired.
6. Completed `MetricInterval` records are prepended to a per-key `ArrayDeque` capped at 12
   entries (1 hour of history).

On `stop()`: sets `running = false` and interrupts the thread. The thread restores the interrupt
flag and breaks out of the loop, then performs one final `processAll()` to drain any buffered
observations before exiting.

---

## Flow of a single observation

```
Application code
  └─ counter.add(5)  /  gauge.setValue(3.14)
       │
       ├─ currentValue updated atomically
       ├─ ObservationLong / ObservationDouble created (value + System.currentTimeMillis())
       ├─ enqueued in ObservationsQueue
       └─ if OTel enabled → published to OpenTelemetry SDK immediately

(every 30 s on "metrics-aggregator" thread)
MetricAggregator.processAll()
  └─ for each EngineMetricsService
       └─ for each metric (UpDownCounter / SymDoubleGauge)
            └─ removeAllObservations()  ← atomic queue swap
                 └─ for each ISymObservation
                      └─ assign to MetricIntervalAccumulator bucket
                           └─ when bucket closes → MetricInterval (avg, min, max, stdDev)
                                └─ stored in ArrayDeque<MetricInterval> per MetricKey (last 12)
```
