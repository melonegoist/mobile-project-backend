-- Seed initial stock data required by the load-tester and mobile clients
-- Prices here are baseline values; quotation-receiver will update them in real-time

INSERT INTO stocks (symbol, name, sector, current_price, change_percent, volume, market_cap, last_updated)
VALUES
    ('AAPL',  'Apple Inc.',              'Technology',    178.50,  1.25,  58432100,  2800000000000, NOW()),
    ('GOOGL', 'Alphabet Inc.',           'Technology',    140.20, -0.45,  22154300,  1750000000000, NOW()),
    ('MSFT',  'Microsoft Corporation',   'Technology',    415.30,  0.89,  19832400,  3080000000000, NOW()),
    ('AMZN',  'Amazon.com Inc.',         'Consumer',      185.60,  2.10,  35621800,  1920000000000, NOW()),
    ('TSLA',  'Tesla Inc.',              'Automotive',    248.75, -1.82,  92341200,   790000000000, NOW()),
    ('NVDA',  'NVIDIA Corporation',      'Technology',    875.40,  3.45, 41253700,  2160000000000, NOW()),
    ('META',  'Meta Platforms Inc.',     'Technology',    520.15,  0.67,  18764300,  1330000000000, NOW()),
    ('NFLX',  'Netflix Inc.',            'Entertainment', 628.90,  1.34,   5432100,   271000000000, NOW()),
    ('AMD',   'Advanced Micro Devices',  'Technology',    178.25, -0.92,  48321600,   288000000000, NOW()),
    ('INTC',  'Intel Corporation',       'Technology',     32.40, -2.15,  63241500,   136000000000, NOW()),
    ('JPM',   'JPMorgan Chase & Co.',    'Finance',       202.35,  0.53,  12342100,   583000000000, NOW()),
    ('GS',    'Goldman Sachs Group',     'Finance',       452.10,  0.28,   3521400,   147000000000, NOW()),
    ('BAC',   'Bank of America Corp.',   'Finance',        38.75, -0.31,  46321800,   308000000000, NOW()),
    ('WFC',   'Wells Fargo & Company',   'Finance',        57.20,  0.45,  21543200,   208000000000, NOW()),
    ('V',     'Visa Inc.',               'Finance',       273.50,  0.78,  11234500,   561000000000, NOW()),
    ('MA',    'Mastercard Incorporated', 'Finance',       468.30,  0.95,   7654300,   438000000000, NOW()),
    ('PYPL',  'PayPal Holdings Inc.',    'Finance',        68.45, -1.23,  15432100,    73000000000, NOW()),
    ('DIS',   'Walt Disney Company',     'Entertainment', 112.60, -0.67,  14321500,   206000000000, NOW()),
    ('BABA',  'Alibaba Group Holding',   'Technology',     82.30, -0.89,  18765400,   210000000000, NOW()),
    ('UBER',  'Uber Technologies Inc.',  'Transportation', 78.15,  1.56,  22134500,   163000000000, NOW())
ON CONFLICT (symbol) DO NOTHING;
