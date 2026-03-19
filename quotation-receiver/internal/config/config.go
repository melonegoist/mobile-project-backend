package config

import (
	"fmt"
	"os"
	"strconv"
	"time"
)

type AppConfig struct {
	DevicePath      string
	BatchFrameCount int
	MinDelay        time.Duration
	ErrorBackoff    time.Duration
	RedisAddr       string
	RedisPassword   string
	RedisDB         int
	RedisChannel    string
}

func Load() (AppConfig, error) {
	batchFrameCount, err := getIntEnv("BATCH_FRAME_COUNT", 16)
	if err != nil {
		return AppConfig{}, err
	}
	if batchFrameCount <= 0 {
		return AppConfig{}, fmt.Errorf("BATCH_FRAME_COUNT must be > 0")
	}

	minDelayMs, err := getIntEnv("POLL_MIN_DELAY_MS", 1)
	if err != nil {
		return AppConfig{}, err
	}
	if minDelayMs < 0 {
		return AppConfig{}, fmt.Errorf("POLL_MIN_DELAY_MS must be >= 0")
	}

	errorBackoffMs, err := getIntEnv("POLL_ERROR_BACKOFF_MS", 10)
	if err != nil {
		return AppConfig{}, err
	}
	if errorBackoffMs < 0 {
		return AppConfig{}, fmt.Errorf("POLL_ERROR_BACKOFF_MS must be >= 0")
	}

	redisDB, err := getIntEnv("REDIS_DB", 0)
	if err != nil {
		return AppConfig{}, err
	}
	if redisDB < 0 {
		return AppConfig{}, fmt.Errorf("REDIS_DB must be >= 0")
	}

	return AppConfig{
		DevicePath:      getStringEnv("DEVICE_PATH", "/dev/price_delta"),
		BatchFrameCount: batchFrameCount,
		MinDelay:        time.Duration(minDelayMs) * time.Millisecond,
		ErrorBackoff:    time.Duration(errorBackoffMs) * time.Millisecond,
		RedisAddr:       getStringEnv("REDIS_ADDR", "localhost:6379"),
		RedisPassword:   os.Getenv("REDIS_PASSWORD"),
		RedisDB:         redisDB,
		RedisChannel:    getStringEnv("REDIS_CHANNEL", "quotes.ticks"),
	}, nil
}

func getStringEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}

	return defaultValue
}

func getIntEnv(key string, defaultValue int) (int, error) {
	value := os.Getenv(key)
	if value == "" {
		return defaultValue, nil
	}

	parsed, err := strconv.Atoi(value)
	if err != nil {
		return 0, fmt.Errorf("parse %s: %w", key, err)
	}

	return parsed, nil
}
