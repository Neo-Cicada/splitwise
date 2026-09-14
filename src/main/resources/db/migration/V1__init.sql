-- "users" and "groups" are reserved words in Postgres, so they stay quoted everywhere.

CREATE TABLE "users" (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    email      VARCHAR(255) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE "groups" (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    currency   CHAR(3)      NOT NULL DEFAULT 'PHP',
    created_by BIGINT       NOT NULL REFERENCES "users" (id),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE group_members (
    group_id  BIGINT      NOT NULL REFERENCES "groups" (id) ON DELETE CASCADE,
    user_id   BIGINT      NOT NULL REFERENCES "users" (id),
    role      VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    left_at   TIMESTAMPTZ,
    PRIMARY KEY (group_id, user_id)
);

CREATE TABLE expenses (
    id          BIGSERIAL PRIMARY KEY,
    group_id    BIGINT         NOT NULL REFERENCES "groups" (id),
    paid_by     BIGINT         NOT NULL REFERENCES "users" (id),
    amount      NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    description VARCHAR(255),
    split_type  VARCHAR(20)    NOT NULL,
    spent_at    DATE           NOT NULL,
    created_by  BIGINT         NOT NULL REFERENCES "users" (id),
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ
);

CREATE TABLE expense_shares (
    id           BIGSERIAL PRIMARY KEY,
    expense_id   BIGINT         NOT NULL REFERENCES expenses (id) ON DELETE CASCADE,
    user_id      BIGINT         NOT NULL REFERENCES "users" (id),
    share_amount NUMERIC(12, 2) NOT NULL CHECK (share_amount >= 0),
    UNIQUE (expense_id, user_id)
);

CREATE TABLE settlements (
    id        BIGSERIAL PRIMARY KEY,
    group_id  BIGINT         NOT NULL REFERENCES "groups" (id),
    from_user BIGINT         NOT NULL REFERENCES "users" (id),
    to_user   BIGINT         NOT NULL REFERENCES "users" (id),
    amount    NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    settled_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (from_user <> to_user)
);

CREATE INDEX idx_expenses_group_id ON expenses (group_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_expense_shares_user_id ON expense_shares (user_id);
CREATE INDEX idx_settlements_group_id ON settlements (group_id);
