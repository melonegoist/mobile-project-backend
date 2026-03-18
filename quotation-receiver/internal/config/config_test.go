package config

import (
	"strings"
	"testing"
	"time"
)

func clearConfigEnv(t *testing.T) {
	t.Helper()

	keys := []string{
		"DEVICE_PATH",
		"INITIAL_PRICE",
		"BATCH_FRAME_COUNT",
		"POLL_MIN_DELAY_MS",
		"POLL_ERROR_BACKOFF_MS",
		"REDIS_ADDR",
		"REDIS_PASSWORD",
		"REDIS_DB",
		"REDIS_CHANNEL",
	}
	for _, key := range keys {
		t.Setenv(key, "")
	}
}

func TestLoad_Defaults(t *testing.T) {
	clearConfigEnv(t)

	cfg, err := Load()
	if err != nil {
		t.Fatalf("Load returned error: %v", err)
	}

	if cfg.DevicePath != "/dev/price_delta" {
		t.Fatalf("DevicePath mismatch: %q", cfg.DevicePath)
	}
	if cfg.InitialPrice != float32(100.0) {
		t.Fatalf("InitialPrice mismatch: %f", cfg.InitialPrice)
	}
	if cfg.BatchFrameCount != 16 {
		t.Fatalf("BatchFrameCount mismatch: %d", cfg.BatchFrameCount)
	}
	if cfg.MinDelay != time.Millisecond {
		t.Fatalf("MinDelay mismatch: %s", cfg.MinDelay)
	}
	if cfg.ErrorBackoff != 10*time.Millisecond {
		t.Fatalf("ErrorBackoff mismatch: %s", cfg.ErrorBackoff)
	}
	if cfg.RedisAddr != "localhost:6379" {
		t.Fatalf("RedisAddr mismatch: %q", cfg.RedisAddr)
	}
	if cfg.RedisDB != 0 {
		t.Fatalf("RedisDB mismatch: %d", cfg.RedisDB)
	}
	if cfg.RedisChannel != "quotes.ticks" {
		t.Fatalf("RedisChannel mismatch: %q", cfg.RedisChannel)
	}
}

func TestLoad_ParsesCustomValues(t *testing.T) {
	clearConfigEnv(t)
	t.Setenv("DEVICE_PATH", "/tmp/dev")
	t.Setenv("INITIAL_PRICE", "42.5")
	t.Setenv("BATCH_FRAME_COUNT", "8")
	t.Setenv("POLL_MIN_DELAY_MS", "5")
	t.Setenv("POLL_ERROR_BACKOFF_MS", "15")
	t.Setenv("REDIS_ADDR", "redis:6379")
	t.Setenv("REDIS_PASSWORD", "secret")
	t.Setenv("REDIS_DB", "3")
	t.Setenv("REDIS_CHANNEL", "custom.channel")

	cfg, err := Load()
	if err != nil {
		t.Fatalf("Load returned error: %v", err)
	}

	if cfg.DevicePath != "/tmp/dev" ||
		cfg.InitialPrice != float32(42.5) ||
		cfg.BatchFrameCount != 8 ||
		cfg.MinDelay != 5*time.Millisecond ||
		cfg.ErrorBackoff != 15*time.Millisecond ||
		cfg.RedisAddr != "redis:6379" ||
		cfg.RedisPassword != "secret" ||
		cfg.RedisDB != 3 ||
		cfg.RedisChannel != "custom.channel" {
		t.Fatalf("unexpected config: %+v", cfg)
	}
}

func TestLoad_InvalidBatchCount(t *testing.T) {
	clearConfigEnv(t)
	t.Setenv("BATCH_FRAME_COUNT", "0")

	_, err := Load()
	if err == nil {
		t.Fatal("expected validation error")
	}
	if !strings.Contains(err.Error(), "BATCH_FRAME_COUNT") {
		t.Fatalf("unexpected error: %v", err)
	}
}

func TestLoad_InvalidFloat(t *testing.T) {
	clearConfigEnv(t)
	t.Setenv("INITIAL_PRICE", "NaN-abc")

	_, err := Load()
	if err == nil {
		t.Fatal("expected parse error")
	}
	if !strings.Contains(err.Error(), "INITIAL_PRICE") {
		t.Fatalf("unexpected error: %v", err)
	}
}

func TestGetIntEnv_EmptyAndInvalid(t *testing.T) {
	t.Setenv("MISSING_INT_KEY", "")
	if got, err := getIntEnv("MISSING_INT_KEY", 7); err != nil || got != 7 {
		t.Fatalf("expected default int, got %d err=%v", got, err)
	}

	t.Setenv("BROKEN_INT", "abc")
	if _, err := getIntEnv("BROKEN_INT", 0); err == nil {
		t.Fatal("expected parse error")
	}
}
