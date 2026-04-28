# Observability Package

In-process metric collection and aggregation for SymmetricDS nodes. Spans two Gradle modules:

- **`symmetric-core`** — interfaces and constants only; no OTel or stats dependencies
- **`symmetric-stats`** — all concrete implementations; depends on `symmetric-core`

## Package layout

```
symmetric-core / org.jumpmind.symmetric.observability.interfaces/
├── IMetricsService, IEngineMetricsService   – service contracts
├── IUpDownCounter, IIncreasingCounter, ISymDoubleGauge, ISymLongGauge  – instrument interfaces
├── ISymMetric, ISymMetricDefinition         – metric/definition contracts
├── ISymObservation, ISymIntervalStats       – data contracts
├── IStatsAccumulator, IPrimaryMetricAggregator
├── InvalidMetricDataException
└── SymMetricConstants                       – stable metric ID, unit, and InstrumentType constants

symmetric-stats / org.jumpmind.symmetric.observability/
├── metrics/     – instruments, service layer, MetricsManager, MetricDefinitionFactory
├── models/      – immutable data types: observations, keys, interval stats
├── repository/  – database read/write for metric keys and interval data
└── stats/       – background aggregation into fixed-width time windows
```

## Key types

| Type | Module / Package | Role |
|------|-----------------|------|
| `SymMetricConstants` | core / interfaces | Stable metric ID and unit string constants (`METRIC_ID_*`, `METRIC_UNIT_*`); also owns `InstrumentType` enum |
| `ISymMetricDefinition` | core / interfaces | Interface for metric metadata: `id()`, `description()`, `unit()` |
| `SymMetricDefinition` | stats / metrics | Record implementing `ISymMetricDefinition`; carries `InstrumentType` (UPDOWN_COUNTER, COUNTER, GAUGE, LONG_GAUGE, HISTOGRAM) |
| `IMetricDefinitionFactory` | stats / metrics | Interface for definition registry + `initializeMetrics()` |
| `MetricDefinitionFactory` | stats / metrics | Default registry; pre-populated from `defaultMetrics` list (parallel to `defaultContexts`); `registerDefaultMetric()` adds to list and registers; `getDefaultMetrics()` returns unmodifiable view |
| `MetricsManager` | stats / metrics | Singleton; owns OTel `Meter`, `MetricDefinitionFactory`, registered services, aggregator lifecycle |
| `EngineMetricsService` | stats / metrics | Engine-scoped service; owns instruments, persists intervals via `MetricsRepository`; `getStatisticManager()` returns engine's `StatisticManager` |
| `HostMetricsService` | stats / metrics | Host-scoped service for system-level metrics |
| `UpDownCounter` / `IncreasingCounter` / `SymDoubleGauge` / `SymLongGauge` | stats / metrics | Instruments; each update enqueues an `ObservationLong` / `ObservationDouble` |
| `MetricKey` | stats / models | Composite identity: `hostname + engineName + metricId` |
| `MetricIntervalStats` | stats / models | Immutable stats for one closed window: avg, min, max, stdDev, count, isOutlier |
| `MetricSeries` | stats / models | Ordered series of interval stats for a single metric key |
| `MetricsRepository` | stats / repository | DB access for `metric_key`, `metric_context`, `metric_stats_float64`, `metric_stats_int64`; caches surrogate keys in-memory |
| `PrimaryMetricAggregator` | stats / stats | Daemon thread; drains observation queues, closes intervals, triggers persistence |
| `AbstractStatsAccumulator` | stats / stats | Mutable accumulator for one open time window; subclassed by `Float64StatsAccumulator` and `Int64StatsAccumulator` |
| `MetricSeriesSlidingWorkset` | stats / stats | Sliding window of recent intervals for IQR-based outlier detection |

## Metric registration flow

```
MetricsManager()
  └── new MetricDefinitionFactory()          ← pre-registers default metric definitions from defaultMetrics list

EngineMetricsService.initRepository()
  └── initializeDefaultMetrics()
        └── metricsManager.getMetricDefinitionFactory().initializeMetrics(this)
              └── service.registerUpDownCounter(def) / registerIncreasingCounter(def) / registerDoubleGauge(def) / registerLongGauge(def)
                    └── UpDownCounter / IncreasingCounter / SymDoubleGauge / SymLongGauge created, stored in AbstractMetricsService (single Map<String, ISymMetric>)
```

Callers (e.g. `ConcurrentConnectionManager`) look up materialized instruments by ID:
```
metricsService.getUpDownCounter(SymMetricConstants.METRIC_ID_SERVER_CONNECTIONS_RESERVATIONS)
```

## Data flow (high level)

```
App thread  →  instrument.add()  →  ObservationsQueue (per metric)
                                            ↓  (every 10 s)
                              PrimaryMetricAggregator.processAll()
                                            ↓
                              AbstractStatsAccumulator  →  MetricIntervalStats
                                            ↓
                              MetricsRepository.saveIntervals()
                                            ↓
                    {prefix}_metric_key / {prefix}_metric_context /
                    {prefix}_metric_stats_float64 / {prefix}_metric_stats_int64
```

## DB tables

| Table | Purpose |
|-------|---------|
| `{prefix}_metric_key` | Dimension — maps surrogate `metric_key` to `hostname`, `engine_name`, `metric_id`, `fact_type`, `metric_type` |
| `{prefix}_metric_context` | Optional attribute context for a metric observation (up to 3 key-value pairs) |
| `{prefix}_metric_stats_float64` | Fact — aggregated float64 interval stats (avg, min, max, std_dev, count) |
| `{prefix}_metric_stats_int64` | Fact — aggregated int64 interval stats (same shape, integer types) |
