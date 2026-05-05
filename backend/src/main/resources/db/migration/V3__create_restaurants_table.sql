CREATE TABLE restaurants (
    id              BIGSERIAL    PRIMARY KEY,
    organization_id BIGINT       NOT NULL,
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(100) NOT NULL,
    description     TEXT,
    address         VARCHAR(500) NOT NULL,
    city            VARCHAR(100) NOT NULL,
    country         VARCHAR(2)   NOT NULL,
    latitude        DECIMAL(10,8),
    longitude       DECIMAL(11,8),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_restaurants_organization
        FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,

    CONSTRAINT uk_restaurants_organization_slug
        UNIQUE (organization_id, slug)
);

CREATE INDEX idx_restaurants_organization ON restaurants (organization_id);
CREATE INDEX idx_restaurants_city ON restaurants (city);
