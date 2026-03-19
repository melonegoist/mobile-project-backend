package processor

import (
	"encoding/binary"
	"encoding/json"
	"errors"
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

func makeFrame(symbol string, priceKopecks int32) []byte {
	frame := make([]byte, frameSize)
	copy(frame[:tickerSize], []byte(symbol))
	binary.LittleEndian.PutUint32(frame[tickerSize:], uint32(priceKopecks))
	return frame
}

func TestParseTickerPriceFrame_Success(t *testing.T) {
	frame := makeFrame("AAPL", 12345)

	symbol, priceKopecks, err := parseTickerPriceFrame(frame)
	if err != nil {
		t.Fatalf("parseTickerPriceFrame returned error: %v", err)
	}

	if symbol != "AAPL" {
		t.Fatalf("symbol mismatch: got %q", symbol)
	}
	if priceKopecks != int32(12345) {
		t.Fatalf("price kopecks mismatch: got %d", priceKopecks)
	}
}

func TestParseTickerPriceFrame_InvalidSize(t *testing.T) {
	_, _, err := parseTickerPriceFrame([]byte{1, 2, 3})
	if err == nil {
		t.Fatal("expected error for invalid frame size")
	}
}

func TestParseTickerPriceFrame_EmptyTicker(t *testing.T) {
	frame := make([]byte, frameSize)
	binary.LittleEndian.PutUint32(frame[tickerSize:], uint32(100))

	_, _, err := parseTickerPriceFrame(frame)
	if err == nil {
		t.Fatal("expected error for empty ticker")
	}
}

func TestKopecksToRubles(t *testing.T) {
	if got := kopecksToRubles(12345); got != float32(123.45) {
		t.Fatalf("conversion mismatch: got %f", got)
	}
}

func TestHandlePayload_ProcessesPendingAcrossCalls(t *testing.T) {
	pub := &publisherStub{}
	p := NewFrameProcessor(pub)
	frame := makeFrame("MSFT", 200)

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
	if event.Price != float32(2.0) {
		t.Fatalf("price mismatch: got %f", event.Price)
	}
	if time.Since(event.Timestamp) > 5*time.Second {
		t.Fatalf("unexpected old timestamp: %s", event.Timestamp)
	}
}

func TestHandlePayload_PublisherError(t *testing.T) {
	pub := &publisherStub{err: errors.New("boom")}
	p := NewFrameProcessor(pub)

	err := p.HandlePayload(makeFrame("AAPL", 100))
	if err == nil {
		t.Fatal("expected publish error")
	}
	if !strings.Contains(err.Error(), "publish event") {
		t.Fatalf("unexpected error: %v", err)
	}
}
