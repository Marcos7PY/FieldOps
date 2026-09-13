CREATE TABLE app_user (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    full_name NVARCHAR(120) NOT NULL,
    email VARCHAR(120) NOT NULL,
    active BIT NOT NULL DEFAULT 1,
    created_at DATETIME2 NOT NULL
);

CREATE TABLE role (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    name VARCHAR(30) NOT NULL UNIQUE
);

CREATE TABLE user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    CONSTRAINT pk_user_role PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE
);

CREATE TABLE refresh_token (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    expires_at DATETIME2 NOT NULL,
    revoked BIT NOT NULL DEFAULT 0,
    created_at DATETIME2 NOT NULL,
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);