ALTER TABLE app_user ADD failed_login_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE app_user ADD locked_until DATETIME2 NULL;
