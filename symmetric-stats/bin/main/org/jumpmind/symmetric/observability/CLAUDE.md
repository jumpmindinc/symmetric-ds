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

## Metric definition ownership

All built-in metric definitions must be declared in `MetricDefinitionFactory.defaultMetrics` and registered there at engine startup. **Never create metric definitions inline** (e.g. anonymous `ISymMetricDefinition` implementations or ad-hoc `SymMetricDefinition` instances) in callers — `MetricDefinitionFactory` is the single source of truth for all metric metadata.

Callers register attribute-scoped instrument instances using only the metric ID string:

```java
// correct — definition already registered in MetricDefinitionFactory
metricsService.registerLongGauge(METRIC_ID_BATCHES_OUTGOING, List.of(new MetricAttribute(NODE_ID, nodeId)));

// wrong — never define metrics inline at the call site
metricsService.registerLongGauge(new SymMetricDefinition("my.metric", "desc", "rows", LONG_GAUGE), attrs);
```

If the metric ID has not been registered in `MetricDefinitionFactory`, the call throws `InvalidMetricDataException`. This is intentional — a missing definition is a programming error that must surface at startup, not be silently swallowed.

To add a new built-in metric: add a `SymMetricDefinition` entry to `MetricDefinitionFactory.defaultMetrics` and declare its ID constant in `SymMetricConstants`. No other changes are needed for the definition to be available to callers.

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

### Database query to report int64-typed recorded stats (counters and integer gauges):
select k.metric_id, s.context_id , s.interval_start_time, s.min, s.avg , s.max, s.observation_count, s.outlier 
from sym_metric_key as k
join sym_metric_stats_int64 as s on s.metric_key=k.metric_key
where k.metric_id like '%.dbpool.%' -- Filter on database connection pool-specific metrics
order by s.interval_start_time desc limit 100;

### Database query to report float64-typed recorded stats (double gauges and histograms):
select k.metric_id, f.context_id , f.interval_start_time, f.min, f.avg , f.max, f.observation_count, f.outlier 
from sym_metric_key as k
join sym_metric_stats_float64 as f on f.metric_key=k.metric_key
where k.metric_id like '%.dbpool.%' -- Filter on database connection pool-specific metrics
order by f.interval_start_time desc limit 100;


## Purge of record
Purge is implemented in MetricsRepository.purgeIntervalStats
