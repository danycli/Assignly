-- Assignly Analytics Tracker Database Schema

-- Installations Table
CREATE TABLE installations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    installation_id UUID UNIQUE NOT NULL,
    platform VARCHAR(50) NOT NULL,
    installed_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    first_install_time BIGINT NOT NULL,
    current_version VARCHAR(50) NOT NULL,
    total_launches INT DEFAULT 1 NOT NULL,
    last_seen TIMESTAMPTZ DEFAULT now() NOT NULL
);

-- Heartbeats Table
CREATE TABLE heartbeats (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    installation_id UUID NOT NULL REFERENCES installations(installation_id) ON DELETE CASCADE,
    timestamp TIMESTAMPTZ DEFAULT now() NOT NULL
);

-- Downloads Table
CREATE TABLE downloads (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    platform VARCHAR(50) NOT NULL,
    timestamp TIMESTAMPTZ DEFAULT now() NOT NULL
);

-- Indexes for performance
CREATE INDEX idx_installations_platform ON installations(platform);
CREATE INDEX idx_heartbeats_installation_id ON heartbeats(installation_id);
CREATE INDEX idx_heartbeats_timestamp ON heartbeats(timestamp);
CREATE INDEX idx_downloads_platform ON downloads(platform);
