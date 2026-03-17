-- V1__init.sql

-- Таблица пользователей
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(50),
    last_name VARCHAR(50),
    avatar_url TEXT,
    account_type VARCHAR(20) DEFAULT 'investor' CHECK (account_type IN ('investor', 'trader', 'beginner')),
    registration_date TIMESTAMPTZ DEFAULT now(),
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

-- Таблица счетов пользователей
CREATE TABLE accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    balance NUMERIC(19,4) DEFAULT 0,
    currency VARCHAR(3) DEFAULT 'USD' CHECK (currency IN ('USD', 'EUR', 'RUB')),
    total_profit_loss NUMERIC(19,4) DEFAULT 0,
    total_profit_loss_percent NUMERIC(10,2) DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(user_id)
);

-- Таблица справочника акций
CREATE TABLE stocks (
    symbol VARCHAR(20) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    sector VARCHAR(50),
    current_price NUMERIC(19,4) NOT NULL,
    change_percent NUMERIC(10,2) NOT NULL,
    volume BIGINT,
    market_cap NUMERIC(19,4),
    last_updated TIMESTAMPTZ DEFAULT now(),
    created_at TIMESTAMPTZ DEFAULT now()
);

-- Таблица портфеля (текущие позиции)
CREATE TABLE portfolio_items (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    stock_symbol VARCHAR(20) NOT NULL REFERENCES stocks(symbol) ON DELETE CASCADE,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    average_buy_price NUMERIC(19,4) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(user_id, stock_symbol)
);

-- Таблица сделок
CREATE TABLE trades (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    stock_symbol VARCHAR(20) NOT NULL REFERENCES stocks(symbol) ON DELETE CASCADE,
    trade_type VARCHAR(4) NOT NULL CHECK (trade_type IN ('buy', 'sell')),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    price NUMERIC(19,4) NOT NULL,
    total_amount NUMERIC(19,4) GENERATED ALWAYS AS (quantity * price) STORED,
    order_type VARCHAR(10) DEFAULT 'market' CHECK (order_type IN ('market', 'limit')),
    limit_price NUMERIC(19,4),
    status VARCHAR(20) DEFAULT 'completed' CHECK (status IN ('completed', 'pending', 'rejected')),
    message TEXT,
    trade_date TIMESTAMPTZ DEFAULT now(),
    created_at TIMESTAMPTZ DEFAULT now()
);

-- Таблица транзакций по счету
CREATE TABLE account_transactions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL CHECK (type IN ('deposit', 'withdraw', 'trade')),
    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD',
    status VARCHAR(20) DEFAULT 'completed' CHECK (status IN ('pending', 'completed', 'failed')),
    description TEXT,
    transaction_date TIMESTAMPTZ DEFAULT now(),
    created_at TIMESTAMPTZ DEFAULT now()
);

-- Таблица избранного
CREATE TABLE watchlist (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    stock_symbol VARCHAR(20) NOT NULL REFERENCES stocks(symbol) ON DELETE CASCADE,
    added_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(user_id, stock_symbol)
);

-- Таблица настроек пользователя
CREATE TABLE user_settings (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    notifications_email BOOLEAN DEFAULT true,
    notifications_push BOOLEAN DEFAULT true,
    notifications_price_alerts BOOLEAN DEFAULT true,
    language VARCHAR(5) DEFAULT 'en' CHECK (language IN ('en', 'ru', 'es', 'fr', 'de')),
    theme VARCHAR(10) DEFAULT 'system' CHECK (theme IN ('light', 'dark', 'system')),
    default_order_type VARCHAR(10) DEFAULT 'market' CHECK (default_order_type IN ('market', 'limit')),
    risk_level VARCHAR(10) DEFAULT 'medium' CHECK (risk_level IN ('low', 'medium', 'high')),
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

-- Таблица refresh токенов
CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token TEXT NOT NULL UNIQUE,
    expiry_date TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- Таблица исторических данных цен (свечи)
CREATE TABLE price_history (
    id BIGSERIAL PRIMARY KEY,
    stock_symbol VARCHAR(20) NOT NULL REFERENCES stocks(symbol) ON DELETE CASCADE,
    candle_interval VARCHAR(5) NOT NULL CHECK (candle_interval IN ('1m', '5m', '15m', '1h', '4h', '1d', '1w', '1M')),
    time_from TIMESTAMPTZ NOT NULL,
    open_price NUMERIC(19,4) NOT NULL,
    high_price NUMERIC(19,4) NOT NULL,
    low_price NUMERIC(19,4) NOT NULL,
    close_price NUMERIC(19,4) NOT NULL,
    volume BIGINT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(stock_symbol, candle_interval, time_from)
);

-- Таблица статистики пользователя
CREATE TABLE user_stats (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    total_trades INTEGER DEFAULT 0,
    successful_trades INTEGER DEFAULT 0,
    win_rate NUMERIC(5,2) DEFAULT 0,
    total_volume NUMERIC(19,4) DEFAULT 0,
    best_trade VARCHAR(100),
    worst_trade VARCHAR(100),
    average_holding_time INTERVAL,
    last_updated TIMESTAMPTZ DEFAULT now()
);

-- Индексы
CREATE INDEX idx_trades_user_id ON trades(user_id);
CREATE INDEX idx_trades_stock_symbol ON trades(stock_symbol);
CREATE INDEX idx_trades_trade_date ON trades(trade_date);
CREATE INDEX idx_portfolio_items_user_id ON portfolio_items(user_id);
CREATE INDEX idx_account_transactions_user_id ON account_transactions(user_id);
CREATE INDEX idx_price_history_stock_symbol ON price_history(stock_symbol);
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_expiry_date ON refresh_tokens(expiry_date);

-- Триггер для updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_accounts_updated_at BEFORE UPDATE ON accounts FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_portfolio_items_updated_at BEFORE UPDATE ON portfolio_items FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_user_settings_updated_at BEFORE UPDATE ON user_settings FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();