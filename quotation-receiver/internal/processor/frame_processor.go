package processor

import (
	"bytes"
	"encoding/binary"
	"fmt"
	"log"
	"math"
	"time"

	"quotation-receiver/internal/ticker"
)

const (
	frameSize  = 12
	tickerSize = 8
)

type FrameProcessor struct {
	pending      []byte
	currentPrice map[string]float32
	initialPrice float32
}

func NewFrameProcessor(initialPrice float32) *FrameProcessor {
	return &FrameProcessor{
		currentPrice: make(map[string]float32),
		initialPrice: initialPrice,
	}
}

func (p *FrameProcessor) HandlePayload(payload []byte) error {
	p.pending = append(p.pending, payload...)

	for len(p.pending) >= frameSize {
		frame := p.pending[:frameSize]
		p.pending = p.pending[frameSize:]

		tickerSymbol, deltaValue, err := parseTickerDeltaFrame(frame)
		if err != nil {
			return err
		}

		price := p.applyDelta(tickerSymbol, deltaValue)
		eventJSON, err := ticker.SerializeEvent(tickerSymbol, price, time.Now())
		if err != nil {
			return fmt.Errorf("serialize ticker event: %w", err)
		}

		log.Printf("ticker event: %s", eventJSON)
	}

	return nil
}

func parseTickerDeltaFrame(frame []byte) (string, float32, error) {
	if len(frame) != frameSize {
		return "", 0, fmt.Errorf("invalid frame size: got %d, want %d", len(frame), frameSize)
	}

	tickerBytes := bytes.TrimRight(frame[:tickerSize], "\x00 ")
	if len(tickerBytes) == 0 {
		return "", 0, fmt.Errorf("ticker is empty")
	}

	deltaBits := binary.LittleEndian.Uint32(frame[tickerSize:])
	deltaValue := math.Float32frombits(deltaBits)

	return string(tickerBytes), deltaValue, nil
}

func (p *FrameProcessor) applyDelta(tickerSymbol string, deltaValue float32) float32 {
	price, exists := p.currentPrice[tickerSymbol]
	if !exists {
		price = p.initialPrice
	}

	price += deltaValue
	p.currentPrice[tickerSymbol] = price

	return price
}
