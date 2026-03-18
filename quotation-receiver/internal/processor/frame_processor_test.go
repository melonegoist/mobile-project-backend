package processor

import (
	"encoding/binary"
	"encoding/json"
	"errors"
	"math"
	"strings"
	"testing"
	"time"

	"quotation-receiver/internal/ticker"
)

type publisherStub struct {
	events [][]byte
	err    error
}

func (p *publisherStub) Publish(eventJSON []byte) error {
	if p.err != nil {
		return p.err
	}

	copied := make([]byte, len(eventJSON))
	copy(copied, eventJSON)
	p.events = append(p.events, copied)
	return nil
}

func makeFrame(symbol string, delta float32) []byte {
	frame := make([]byte, frameSize)
	copy(frame[:tickerSize], []byte(symbol))
	binary.LittleEndian.PutUint32(frame[tickerSize:], math.Float32bits(delta))
	return frame
}

func TestParseTickerDeltaFrame_Success(t *testing.T) {
	frame := makeFrame("AAPL", 1.25)

	symbol, delta, err := parseTickerDeltaFrame(frame)
	if err != nil {
		t.Fatalf("parseTickerDeltaFrame returned error: %v", err)
	}

	if symbol != "AAPL" {
		t.Fatalf("symbol mismatch: got %q", symbol)
	}
	if delta != float32(1.25) {
		t.Fatalf("delta mismatch: got %f", delta)
	}
}

func TestParseTickerDeltaFrame_InvalidSize(t *testing.T) {
	_, _, err := parseTickerDeltaFrame([]byte{1, 2, 3})
	if err == nil {
		t.Fatal("expected error for invalid frame size")
	}
}

func TestParseTickerDeltaFrame_EmptyTicker(t *testing.T) {
	frame := make([]byte, frameSize)
	binary.LittleEndian.PutUint32(frame[tickerSize:], math.Float32bits(1.0))

	_, _, err := parseTickerDeltaFrame(frame)
	if err == nil {
		t.Fatal("expected error for empty ticker")
	}
}

func TestApplyDelta_UsesInitialAndAccumulates(t *testing.T) {
	p := NewFrameProcessor(100, nil)

	if got := p.applyDelta("AAPL", 1.5); got != float32(101.5) {
		t.Fatalf("first price mismatch: got %f", got)
	}
	if got := p.applyDelta("AAPL", -0.5); got != float32(101.0) {
		t.Fatalf("second price mismatch: got %f", got)
	}
}

func TestHandlePayload_ProcessesPendingAcrossCalls(t *testing.T) {
	pub := &publisherStub{}
	p := NewFrameProcessor(100, pub)
	frame := makeFrame("MSFT", 2.0)

	if err := p.HandlePayload(frame[:6]); err != nil {
		t.Fatalf("first partial payload failed: %v", err)
	}
	if len(pub.events) != 0 {
		t.Fatalf("expected no events yet, got %d", len(pub.events))
	}

	if err := p.HandlePayload(frame[6:]); err != nil {
		t.Fatalf("second partial payload failed: %v", err)
	}
	if len(pub.events) != 1 {
		t.Fatalf("expected one event, got %d", len(pub.events))
	}

	var event ticker.Event
	if err := json.Unmarshal(pub.events[0], &event); err != nil {
		t.Fatalf("unmarshal event: %v", err)
	}

	if event.Ticker != "MSFT" {
		t.Fatalf("ticker mismatch: got %q", event.Ticker)
	}
	if event.Price != float32(102.0) {
		t.Fatalf("price mismatch: got %f", event.Price)
	}
	if time.Since(event.Timestamp) > 5*time.Second {
		t.Fatalf("unexpected old timestamp: %s", event.Timestamp)
	}
}

func TestHandlePayload_PublisherError(t *testing.T) {
	pub := &publisherStub{err: errors.New("boom")}
	p := NewFrameProcessor(100, pub)

	err := p.HandlePayload(makeFrame("AAPL", 1.0))
	if err == nil {
		t.Fatal("expected publish error")
	}
	if !strings.Contains(err.Error(), "publish event") {
		t.Fatalf("unexpected error: %v", err)
	}
}
