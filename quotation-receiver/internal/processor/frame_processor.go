package processor

import (
	"bytes"
	"encoding/binary"
	"encoding/hex"
	"fmt"
	"log"
	"time"

	"quotation-receiver/internal/ticker"
)

const (
	frameSize       = 12
	tickerSize      = 8
	kopecksPerRuble = 100
)

type FrameProcessor struct {
	pending   []byte
	publisher EventPublisher
}

type EventPublisher interface {
	Publish(eventJSON []byte) error
}

func NewFrameProcessor(publisher EventPublisher) *FrameProcessor {
	return &FrameProcessor{
		publisher: publisher,
	}
}

func (p *FrameProcessor) HandlePayload(payload []byte) error {
	p.pending = append(p.pending, payload...)

	for len(p.pending) >= frameSize {
		frame := p.pending[:frameSize]
		p.pending = p.pending[frameSize:]

		tickerSymbol, priceKopecks, err := parseTickerPriceFrame(frame)
		if err != nil {
			return err
		}

		price := kopecksToRubles(priceKopecks)
		log.Printf("driver frame raw=%s ticker=%s price_kopecks=%d price_rub=%.2f",
			hex.EncodeToString(frame), tickerSymbol, priceKopecks, price)

		eventJSON, err := ticker.SerializeEvent(tickerSymbol, price, time.Now())
		if err != nil {
			return fmt.Errorf("serialize ticker event: %w", err)
		}

		if p.publisher != nil {
			if err := p.publisher.Publish(eventJSON); err != nil {
				return fmt.Errorf("publish event: %w", err)
			}
		}

		log.Printf("ticker event: %s", eventJSON)
	}

	return nil
}

func parseTickerPriceFrame(frame []byte) (string, int32, error) {
	if len(frame) != frameSize {
		return "", 0, fmt.Errorf("invalid frame size: got %d, want %d", len(frame), frameSize)
	}

	tickerBytes := bytes.TrimRight(frame[:tickerSize], "\x00 ")
	if len(tickerBytes) == 0 {
		return "", 0, fmt.Errorf("ticker is empty")
	}

	priceKopecks := int32(binary.LittleEndian.Uint32(frame[tickerSize:]))

	return string(tickerBytes), priceKopecks, nil
}

func kopecksToRubles(priceKopecks int32) float32 {
	return float32(priceKopecks) / kopecksPerRuble
}
