package main

import (
	"context"
	"errors"
	"log"
	"os"
	"os/signal"
	"syscall"

	"quotation-receiver/internal/config"
	"quotation-receiver/internal/driver"
	"quotation-receiver/internal/processor"
)

const (
	frameSize = 12
)

func main() {
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	appCfg, err := config.Load()
	if err != nil {
		log.Fatalf("load config: %v", err)
	}

	frameProcessor := processor.NewFrameProcessor(appCfg.InitialPrice)

	cfg := driver.PollConfig{
		DevicePath:   appCfg.DevicePath,
		BufferSize:   frameSize * appCfg.BatchFrameCount,
		MinDelay:     appCfg.MinDelay,
		ErrorBackoff: appCfg.ErrorBackoff,
	}

	err = driver.PollDriver(ctx, cfg, frameProcessor.HandlePayload)
	if err != nil && !errors.Is(err, context.Canceled) {
		log.Fatalf("polling failed: %v", err)
	}
}
