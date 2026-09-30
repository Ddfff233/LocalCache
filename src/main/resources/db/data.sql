CREATE TABLE IF NOT EXISTS cache_entry (
                                           key TEXT PRIMARY KEY,
                                           value BLOB NOT NULL,
                                           expire_at INTEGER,  -- 毫秒时间戳，NULL 表示永不过期
                                           create_at INTEGER NOT NULL,
                                           update_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_expire_at ON cache_entry(expire_at);