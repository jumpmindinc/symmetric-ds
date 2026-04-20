# Observability Package

In-process metric collection and aggregation for SymmetricDS nodes.

## Package layout

```
observability/
├── models/      – immutable data types: observations, keys, interval stats
├── metrics/     – instruments (counters/gauges), service layer, MetricsManager
├── repository/  – database read/write for metric keys and interval data
└── stats/       – background aggregation into fixed-width time windows
```

## Key types

| Type | Package | Role |
|------|---------|------|
| `MetricKey` | models | Composite identity: `hostname + engineName + metricId` |
| `MetricIntervalStats` | models | Immutable stats for one closed window: avg, min, max, stdDev, mean, count, isOutlier |
| `UpDownCounter` / `SymDoubleGauge` | metrics | Instruments; each update enqueues an `ObservationLong`/`ObservationDouble` |
| `EngineMetricsService` | metrics | Engine-scoped service; owns instruments, persists intervals via `MetricsRepository` |
| `MetricsManager` | metrics | Singleton; owns OTel `Meter`, registered services, aggregator lifecycle |
| `MetricsRepository` | repository | DB access for `sym_metric_key` and `sym_metric_interval`; caches surrogate keys in-memory |
| `PrimaryMetricAggregator` | stats | Daemon thread; drains observation queues every 10 s, closes intervals, triggers persistence |
| `MetricIntervalAccumulator` | stats | Mutable accumulator for one open window (step-function, time-weighted) |
| `MetricSeriesSlidingWorkset` | stats | Sliding window of recent intervals for IQR-based outlier detection |

## Data flow (high level)

```
App thread  →  instrument.add()  →  ObservationsQueue (per metric)
                                            ↓  (every 10 s)
                              PrimaryMetricAggregator.processAll()
                                            ↓
                              MetricIntervalAccumulator  →  MetricIntervalStats
                                            ↓
                              MetricsRepository.saveIntervals()
                                            ↓
                              sym_metric_key / sym_metric_interval (DB)
```

## DB tables

| Table | Key columns |
|-------|-------------|
| `{prefix}_metric_key` | `metric_key` (PK), `hostname`, `engine_name`, `metric_id` |
| `{prefix}_metric_interval` | `metric_key` (FK), `interval_start`, `interval_end`, `avg`, `min`, `max`, `std_dev`, `observation_count`, `created_time` |
