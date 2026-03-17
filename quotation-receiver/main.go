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
	"quotation-receiver/internal/pubsub"
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

	redisPublisher, err := pubsub.NewRedisPublisher(
		ctx,
		appCfg.RedisAddr,
		appCfg.RedisPassword,
		appCfg.RedisDB,
		appCfg.RedisChannel,
	)
	if err != nil {
		log.Fatalf("init redis publisher: %v", err)
	}
	defer redisPublisher.Close()

	frameProcessor := processor.NewFrameProcessor(appCfg.InitialPrice, redisPublisher)

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
