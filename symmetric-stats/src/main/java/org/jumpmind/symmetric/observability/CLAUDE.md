# Observability Package

In-process metric collection and aggregation for SymmetricDS nodes. Spans two Gradle modules:

- **`symmetric-core`** — interfaces and constants only; no OTel or stats dependencies
- **`symmetric-stats`** — all concrete implementations; depends on `symmetric-core`

## Package layout

```
symmetric-core / org.jumpmind.symmetric.observability.interfaces/
├── IMetricsService, IEngineMetricsService   – service contracts
├── IUpDownCounter, ISymDoubleGauge          – instrument interfaces
├── ISymMetric, ISymMetricDefinition         – metric/definition contracts
├── ISymObservation, ISymIntervalStats       – data contracts
├── IStatsAccumulator, IPrimaryMetricAggregator
├── InvalidMetricDataException
└── SymMetricConstants                       – stable metric ID and unit constants

symmetric-stats / org.jumpmind.symmetric.observability/
├── metrics/     – instruments, service layer, MetricsManager, MetricDefinitionFactory
├── models/      – immutable data types: observations, keys, interval stats
├── repository/  – database read/write for metric keys and interval data
└── stats/       – background aggregation into fixed-width time windows
```

## Key types

| Type | Module / Package | Role |
|------|-----------------|------|
| `SymMetricConstants` | core / interfaces | Stable metric ID and unit string constants (`METRIC_ID_*`, `METRIC_UNIT_*`) |
| `ISymMetricDefinition` | core / interfaces | Interface for metric metadata: `id()`, `description()`, `unit()` |
| `SymMetricDefinition` | stats / metrics | Record implementing `ISymMetricDefinition`; carries `InstrumentType` (COUNTER, GAUGE) |
| `IMetricDefinitionFactory` | stats / metrics | Interface for definition registry + `initializeMetrics()` |
| `MetricDefinitionFactory` | stats / metrics | Default registry; pre-populated with default engine metrics; owned by `MetricsManager` |
| `MetricsManager` | stats / metrics | Singleton; owns OTel `Meter`, `MetricDefinitionFactory`, registered services, aggregator lifecycle |
| `EngineMetricsService` | stats / metrics | Engine-scoped service; owns instruments, persists intervals via `MetricsRepository` |
| `HostMetricsService` | stats / metrics | Host-scoped service for system-level metrics |
| `UpDownCounter` / `SymDoubleGauge` | stats / metrics | Instruments; each update enqueues an `ObservationLong` / `ObservationDouble` |
| `MetricKey` | stats / models | Composite identity: `hostname + engineName + metricId` |
| `MetricIntervalStats` | stats / models | Immutable stats for one closed window: avg, min, max, stdDev, count, isOutlier |
| `MetricSeries` | stats / models | Ordered series of interval stats for a single metric key |
| `MetricsRepository` | stats / repository | DB access for `sym_metric_key` and `sym_metric_interval`; caches surrogate keys in-memory |
| `PrimaryMetricAggregator` | stats / stats | Daemon thread; drains observation queues, closes intervals, triggers persistence |
| `AbstractStatsAccumulator` | stats / stats | Mutable accumulator for one open time window; subclassed by `Float64StatsAccumulator` and `Int64StatsAccumulator` |
| `MetricSeriesSlidingWorkset` | stats / stats | Sliding window of recent intervals for IQR-based outlier detection |

## Metric registration flow

```
MetricsManager()
  └── new MetricDefinitionFactory()          ← pre-registers default metric definitions

EngineMetricsService.initRepository()
  └── initializeImportantMetrics()
        └── metricsManager.getMetricDefinitionFactory().initializeMetrics(this)
              └── service.registerUpDownCounter(def) / registerGauge(def)
                    └── UpDownCounter / SymDoubleGauge created, stored in AbstractMetricsService
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
                              sym_metric_key / sym_metric_interval (DB)
```

## DB tables

| Table | Key columns |
|-------|-------------|
| `{prefix}_metric_key` | `metric_key` (PK), `hostname`, `engine_name`, `metric_id` |
| `{prefix}_metric_interval` | `metric_key` (FK), `interval_start`, `interval_end`, `avg`, `min`, `max`, `std_dev`, `observation_count`, `created_time` |
