# Benchmark

This document records reproducible load-test results. Do not update README highlight numbers until the values below are measured on the same environment.

## Environment

| Item | Value |
|---|---|
| Date | TBD |
| Machine | TBD |
| JDK | TBD |
| Application profile | TBD |
| MySQL | Docker Compose / local |
| Redis | Docker Compose / local |
| Dataset size | TBD |
| k6 version | TBD |

## Scenarios

| Scenario | Script | Target |
|---|---|---|
| Single lookup | `loadtest/k6/product-single.js` | 1k RPS, 5m ramp-up |
| Batch lookup | `loadtest/k6/product-batch.js` | 20 ids/request, 500 RPS, 5m ramp-up |
| Hot/cold lookup | `loadtest/k6/product-hot-cold.js` | 90% hot key, 10% cold keys |

## Metrics

- Latency: k6 `http_req_duration` p50/p95/p99
- Throughput: k6 request rate
- DB QPS proxy: `product.cache.fallback.items{result="requested"}` delta / test duration
- Redis hit ratio: `product.cache.read.items{result="hit"}` / (`hit` + `miss`)

## Results

Replace the table values after running both modes on the same dataset and machine.

| Scenario | Mode | p50 | p95 | p99 | Throughput | DB QPS | Redis hit ratio |
|---|---|---:|---:|---:|---:|---:|---:|
| Single lookup | DB only | TBD | TBD | TBD | TBD | TBD | N/A |
| Single lookup | Cache enabled | TBD | TBD | TBD | TBD | TBD | TBD |
| Batch lookup | DB only | TBD | TBD | TBD | TBD | TBD | N/A |
| Batch lookup | Cache enabled | TBD | TBD | TBD | TBD | TBD | TBD |
| Hot/cold lookup | DB only | TBD | TBD | TBD | TBD | TBD | N/A |
| Hot/cold lookup | Cache enabled | TBD | TBD | TBD | TBD | TBD | TBD |

## README Highlight

After measurement, update `docs/README.md` with the actual measured values, for example:

```md
- 단건 조회 p99 **{db_only_p99} → {cache_p99}**, 1k RPS 시 DB QPS **-{reduction_percent}%** *(k6 측정, `docs/benchmark.md` 참고)*
```
