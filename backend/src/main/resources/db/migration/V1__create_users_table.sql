CREATE TABLE users (
                       id              BIGSERIAL PRIMARY KEY,
                       email           VARCHAR(255) NOT NULL UNIQUE,
                       password_hash   VARCHAR(255) NOT NULL,
                       full_name       VARCHAR(255) NOT NULL,
                       phone           VARCHAR(50),
                       platform_role   VARCHAR(32)  NOT NULL DEFAULT 'USER',
                       email_verified  BOOLEAN      NOT NULL DEFAULT FALSE,
                       created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                       updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users (email);