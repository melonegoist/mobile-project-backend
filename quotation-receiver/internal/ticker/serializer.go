package ticker

import (
	"encoding/json"
	"time"
)

type Event struct {
	Ticker    string    `json:"ticker"`
	Price     float32   `json:"price"`
	Timestamp time.Time `json:"timestamp"`
}

func SerializeEvent(symbol string, price float32, ts time.Time) ([]byte, error) {
	event := Event{
		Ticker:    symbol,
		Price:     price,
		Timestamp: ts.UTC(),
	}

	return json.Marshal(event)
}
