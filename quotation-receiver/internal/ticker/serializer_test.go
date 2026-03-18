package ticker

import (
	"encoding/json"
	"testing"
	"time"
)

func TestSerializeEvent_UsesUTCAndFields(t *testing.T) {
	ts := time.Date(2026, time.March, 17, 12, 34, 56, 0, time.FixedZone("MSK", 3*60*60))

	data, err := SerializeEvent("AAPL", 123.45, ts)
	if err != nil {
		t.Fatalf("SerializeEvent returned error: %v", err)
	}

	var event Event
	if err := json.Unmarshal(data, &event); err != nil {
		t.Fatalf("unmarshal event: %v", err)
	}

	if event.Ticker != "AAPL" {
		t.Fatalf("ticker mismatch: got %q", event.Ticker)
	}
	if event.Price != float32(123.45) {
		t.Fatalf("price mismatch: got %f", event.Price)
	}

	expectedUTC := ts.UTC()
	if !event.Timestamp.Equal(expectedUTC) {
		t.Fatalf("timestamp mismatch: got %s, want %s", event.Timestamp, expectedUTC)
	}
	if _, offset := event.Timestamp.Zone(); offset != 0 {
		t.Fatalf("timestamp is not UTC: offset=%d", offset)
	}
}
