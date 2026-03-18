# Quotation Receiver: Configuration and Driver Data Format

## Environment variables

The service reads configuration from environment variables at startup.
If a variable is not set, the default value is used.

| Variable | Type | Default | Description |
|---|---|---|---|
| `DEVICE_PATH` | string | `/dev/price_delta` | Path to the character device exposed by the kernel driver. |
| `INITIAL_PRICE` | float32 | `100.0` | Initial price for a ticker when it appears for the first time in the stream. |
| `BATCH_FRAME_COUNT` | int | `16` | Number of 12-byte frames expected per one low-level read buffer. Final read buffer size is `12 * BATCH_FRAME_COUNT`. Must be `> 0`. |
| `POLL_MIN_DELAY_MS` | int | `1` | Minimal delay (ms) between successful polling iterations. Must be `>= 0`. |
| `POLL_ERROR_BACKOFF_MS` | int | `10` | Delay (ms) after retryable read errors. Must be `>= 0`. |
| `REDIS_ADDR` | string | `localhost:6379` | Redis server address for Pub/Sub publishing. |
| `REDIS_PASSWORD` | string | `` | Redis password (optional). |
| `REDIS_DB` | int | `0` | Redis logical database index. Must be `>= 0`. |
| `REDIS_CHANNEL` | string | `quotes.ticks` | Redis Pub/Sub channel where JSON ticker events are published. |

## Driver payload format (binary)

The driver must send binary frames with fixed size **12 bytes**.
Frames can arrive one-by-one or in batches; the service buffers bytes and parses complete frames sequentially.

### Frame layout

| Offset | Size | Type | Field | Notes |
|---|---|---|---|---|
| `0` | `8` | bytes | `ticker` | ASCII ticker symbol. If shorter than 8 bytes, pad with `\x00` or spaces. |
| `8` | `4` | float32 | `delta` | Price delta in **IEEE-754 float32**, **little-endian**. |

Total: `8 + 4 = 12` bytes.

### Decoding rules in service

1. `ticker`: bytes `[0:8]`, trim trailing `\x00` and spaces.
2. `delta`: bytes `[8:12]`, decode as little-endian `uint32`, then reinterpret bits as `float32`.
3. Current price per ticker is accumulated as:
   - `price = previous_price + delta`
   - for a new ticker: `previous_price = INITIAL_PRICE`
4. The service emits JSON event:

```json
{
  "ticker": "AAPL",
  "price": 101.25,
  "timestamp": "2026-03-17T12:34:56.789Z"
}
```

## Example frame

Ticker: `AAPL` with delta `+1.5`.

- Ticker bytes (8): `41 41 50 4C 00 00 00 00`
- Delta float32 bits (`1.5`): `0x3FC00000`
- Little-endian delta bytes: `00 00 C0 3F`

Full frame (12 bytes):

```text
41 41 50 4C 00 00 00 00 00 00 C0 3F
```

## Redis Pub/Sub publication format

Each parsed driver frame is converted to a JSON event and published to Redis Pub/Sub.

### Channel

- Redis channel name is taken from `REDIS_CHANNEL` (default: `quotes.ticks`).

### Message payload

The message body is UTF-8 JSON with the following schema:

| Field | Type | Description |
|---|---|---|
| `ticker` | string | Ticker symbol parsed from driver frame. |
| `price` | float32 | Current accumulated ticker price after applying delta. |
| `timestamp` | string (RFC3339) | UTC timestamp when event was produced. |

Example published payload:

```json
{
  "ticker": "AAPL",
  "price": 101.25,
  "timestamp": "2026-03-17T12:34:56.789Z"
}
```

Equivalent redis-cli subscription test:

```bash
redis-cli SUBSCRIBE quotes.ticks
```
