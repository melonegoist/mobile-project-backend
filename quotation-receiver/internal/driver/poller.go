package driver

import (
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"syscall"
	"time"
)

type PollConfig struct {
	DevicePath   string
	BufferSize   int
	MinDelay     time.Duration
	ErrorBackoff time.Duration
}

type DataHandler func(payload []byte) error

func PollDriver(ctx context.Context, cfg PollConfig, onData DataHandler) error {
	if cfg.DevicePath == "" {
		return errors.New("device path is required")
	}

	if cfg.BufferSize <= 0 {
		cfg.BufferSize = 256
	}

	if onData == nil {
		return errors.New("data handler is required")
	}

	device, err := os.OpenFile(cfg.DevicePath, os.O_RDONLY, 0)
	if err != nil {
		return fmt.Errorf("open device %s: %w", cfg.DevicePath, err)
	}
	defer device.Close()

	return poll(ctx, device, cfg, onData)
}

func poll(ctx context.Context, src io.Reader, cfg PollConfig, onData DataHandler) error {
	buf := make([]byte, cfg.BufferSize)

	for {
		select {
		case <-ctx.Done():
			return nil
		default:
		}

		n, err := src.Read(buf)
		if n > 0 {
			payload := make([]byte, n)
			copy(payload, buf[:n])

			if handleErr := onData(payload); handleErr != nil {
				return fmt.Errorf("handle payload: %w", handleErr)
			}

			if cfg.MinDelay > 0 {
				if sleepErr := sleepContext(ctx, cfg.MinDelay); sleepErr != nil {
					return nil
				}
			}
		}

		if err == nil {
			if n == 0 && cfg.MinDelay > 0 {
				if sleepErr := sleepContext(ctx, cfg.MinDelay); sleepErr != nil {
					return nil
				}
			}
			continue
		}

		if errors.Is(err, io.EOF) {
			if cfg.MinDelay > 0 {
				if sleepErr := sleepContext(ctx, cfg.MinDelay); sleepErr != nil {
					return nil
				}
			}
			continue
		}

		if isRetryable(err) {
			if cfg.ErrorBackoff > 0 {
				if sleepErr := sleepContext(ctx, cfg.ErrorBackoff); sleepErr != nil {
					return nil
				}
			}
			continue
		}

		return fmt.Errorf("read driver data: %w", err)
	}
}

func isRetryable(err error) bool {
	if errors.Is(err, syscall.EINTR) || errors.Is(err, syscall.EAGAIN) || errors.Is(err, syscall.EWOULDBLOCK) {
		return true
	}

	var errno syscall.Errno
	return errors.As(err, &errno) && (errno == syscall.EINTR || errno == syscall.EAGAIN || errno == syscall.EWOULDBLOCK)
}

func sleepContext(ctx context.Context, d time.Duration) error {
	timer := time.NewTimer(d)
	defer timer.Stop()

	select {
	case <-ctx.Done():
		return ctx.Err()
	case <-timer.C:
		return nil
	}
}
