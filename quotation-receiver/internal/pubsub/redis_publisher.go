package pubsub

import (
	"context"
	"fmt"
	"time"

	redis "github.com/redis/go-redis/v9"
)

type RedisPublisher struct {
	client  *redis.Client
	channel string
	ctx     context.Context
}

func NewRedisPublisher(ctx context.Context, addr, password string, db int, channel string) (*RedisPublisher, error) {
	client := redis.NewClient(&redis.Options{
		Addr:     addr,
		Password: password,
		DB:       db,
	})

	pingCtx, cancel := context.WithTimeout(ctx, 2*time.Second)
	defer cancel()

	if err := client.Ping(pingCtx).Err(); err != nil {
		_ = client.Close()
		return nil, fmt.Errorf("connect to redis: %w", err)
	}

	return &RedisPublisher{
		client:  client,
		channel: channel,
		ctx:     ctx,
	}, nil
}

func (p *RedisPublisher) Publish(eventJSON []byte) error {
	if err := p.client.Publish(p.ctx, p.channel, eventJSON).Err(); err != nil {
		return fmt.Errorf("publish to redis channel %s: %w", p.channel, err)
	}

	return nil
}

func (p *RedisPublisher) Close() error {
	return p.client.Close()
}
