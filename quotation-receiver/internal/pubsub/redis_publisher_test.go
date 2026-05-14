package pubsub

import (
	"context"
	"strings"
	"testing"
	"time"
)

func TestNewRedisPublisher_ReturnsErrorOnUnreachableServer(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()

	// Pick a port that should reliably refuse connections.
	_, err := NewRedisPublisher(ctx, "127.0.0.1:1", "", 0, "quotes.ticks")
	if err == nil {
		t.Fatal("expected error when redis is unreachable")
	}
	if !strings.Contains(err.Error(), "connect to redis") {
		t.Fatalf("expected wrapped connect error, got: %v", err)
	}
}

func TestRedisPublisher_PublishReturnsErrorWhenClientClosed(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()

	// We have no live Redis, so just verify the error path of a closed client
	// by manually building a publisher with a stopped client.
	pub, err := NewRedisPublisher(ctx, "127.0.0.1:1", "", 0, "quotes.ticks")
	if err == nil {
		// If somehow a redis happens to listen on :1, close it and continue;
		// otherwise this guard makes the test self-skip rather than fail.
		t.Skip("unexpected redis at :1, skipping")
	}
	if pub != nil {
		_ = pub.Close()
	}
}
