package driver

import (
	"context"
	"errors"
	"io"
	"syscall"
	"testing"
	"time"
)

type readStep struct {
	data []byte
	err  error
}

type stepReader struct {
	steps []readStep
	idx   int
}

func (r *stepReader) Read(p []byte) (int, error) {
	if r.idx >= len(r.steps) {
		return 0, io.EOF
	}

	step := r.steps[r.idx]
	r.idx++
	if len(step.data) > 0 {
		n := copy(p, step.data)
		return n, step.err
	}
	return 0, step.err
}

func TestPollDriver_Validation(t *testing.T) {
	err := PollDriver(context.Background(), PollConfig{}, func(payload []byte) error { return nil })
	if err == nil {
		t.Fatal("expected error for missing device path")
	}

	err = PollDriver(context.Background(), PollConfig{DevicePath: "/dev/null"}, nil)
	if err == nil {
		t.Fatal("expected error for nil data handler")
	}
}

func TestPoll_HandlesPayload(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	r := &stepReader{steps: []readStep{{data: []byte("abc"), err: nil}}}
	cfg := PollConfig{BufferSize: 8}

	var got []byte
	err := poll(ctx, r, cfg, func(payload []byte) error {
		got = append([]byte(nil), payload...)
		cancel()
		return nil
	})
	if err != nil {
		t.Fatalf("poll returned error: %v", err)
	}
	if string(got) != "abc" {
		t.Fatalf("payload mismatch: %q", string(got))
	}
}

func TestPoll_ReturnsOnHandlerError(t *testing.T) {
	r := &stepReader{steps: []readStep{{data: []byte("abc"), err: nil}}}
	cfg := PollConfig{BufferSize: 8}

	err := poll(context.Background(), r, cfg, func(payload []byte) error {
		return errors.New("handler failed")
	})
	if err == nil {
		t.Fatal("expected handler error")
	}
}

func TestPoll_RetryableError(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	r := &stepReader{steps: []readStep{{err: syscall.EINTR}}}
	cfg := PollConfig{BufferSize: 8, ErrorBackoff: 20 * time.Millisecond}

	go func() {
		time.Sleep(2 * time.Millisecond)
		cancel()
	}()

	err := poll(ctx, r, cfg, func(payload []byte) error { return nil })
	if err != nil {
		t.Fatalf("expected nil on cancellation, got: %v", err)
	}
}

func TestPoll_NonRetryableReadError(t *testing.T) {
	r := &stepReader{steps: []readStep{{err: errors.New("disk error")}}}
	cfg := PollConfig{BufferSize: 8}

	err := poll(context.Background(), r, cfg, func(payload []byte) error { return nil })
	if err == nil {
		t.Fatal("expected read error")
	}
}

func TestIsRetryable(t *testing.T) {
	if !isRetryable(syscall.EINTR) {
		t.Fatal("EINTR should be retryable")
	}
	if isRetryable(errors.New("other")) {
		t.Fatal("generic error should not be retryable")
	}
}

func TestSleepContext_Canceled(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	if err := sleepContext(ctx, 10*time.Millisecond); err == nil {
		t.Fatal("expected cancellation error")
	}
}
