package main

import (
	"context"
	"errors"
	"log"
	"os"
	"os/signal"
	"syscall"
	"time"

	"quotation-receiver/internal/driver"
)

func main() {
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	cfg := driver.PollConfig{
		DevicePath:   "/dev/price_delta",
		BufferSize:   256,
		MinDelay:     time.Millisecond,
		ErrorBackoff: 10 * time.Millisecond,
	}

	err := driver.PollDriver(ctx, cfg, func(payload []byte) error {
		log.Printf("driver payload: %q", string(payload))
		return nil
	})
	if err != nil && !errors.Is(err, context.Canceled) {
		log.Fatalf("polling failed: %v", err)
	}
}
