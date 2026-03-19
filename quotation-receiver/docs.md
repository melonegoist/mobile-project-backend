# Quotation Receiver: Application Structure

## Purpose

`quotation-receiver` is a Go service that:

1. polls a Linux character device;
2. parses binary quote frames from the driver;
3. builds JSON ticker events;
4. publishes events to Redis Pub/Sub.

## High-level architecture

The service is split into small internal packages with clear responsibilities:

- `main.go`: application wiring and startup.
- `internal/config`: environment-based configuration loading and validation.
- `internal/driver`: low-level polling loop over `/dev/...` via `read()`.
- `internal/processor`: binary frame parsing and price conversion from kopeycks to rubles.
- `internal/ticker`: JSON serialization of ticker events.
- `internal/pubsub`: Redis publisher implementation.

## Directory layout

```text
quotation-receiver/
  main.go
  go.mod
  go.sum
  Dockerfile
  interactions_protocol.md
  internal/
    config/
      config.go
    driver/
      poller.go
    processor/
      frame_processor.go
    ticker/
      serializer.go
    pubsub/
      redis_publisher.go
```

## Data flow

1. `main.go` loads settings from environment (`config.Load()`).
2. `main.go` creates:
   - Redis publisher (`pubsub.NewRedisPublisher`),
   - frame processor (`processor.NewFrameProcessor`).
3. `main.go` starts the polling loop (`driver.PollDriver`).
4. `PollDriver` reads raw bytes from `DEVICE_PATH` and forwards chunks to `FrameProcessor.HandlePayload`.
5. `FrameProcessor`:
   - buffers partial reads,
   - extracts fixed 12-byte frames,
  - parses frame fields (`ticker`, `price_kopecks`),
  - converts integer kopecks into rubles,
   - serializes event to JSON via `ticker.SerializeEvent`.
6. Serialized JSON is published to Redis channel using `publisher.Publish`.

## Runtime behavior details

- The processor is resilient to split reads: one `read()` may contain half-frame, exact frame, or multiple frames.
- Frame price is interpreted as an absolute value in kopecks and converted as:
  - `price_rub = price_kopecks / 100`.
- On graceful shutdown signal (`SIGTERM`/`Ctrl+C`), polling stops through context cancellation.

## Configuration summary

Main configuration is provided through environment variables (see full protocol details in `interactions_protocol.md`):

- `DEVICE_PATH`
- `BATCH_FRAME_COUNT`
- `POLL_MIN_DELAY_MS`
- `POLL_ERROR_BACKOFF_MS`
- `REDIS_ADDR`
- `REDIS_PASSWORD`
- `REDIS_DB`
- `REDIS_CHANNEL`
