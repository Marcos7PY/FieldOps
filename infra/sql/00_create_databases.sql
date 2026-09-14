IF DB_ID('fieldops_auth') IS NULL CREATE DATABASE fieldops_auth;
IF DB_ID('fieldops_orders') IS NULL CREATE DATABASE fieldops_orders;
IF DB_ID('fieldops_notifications') IS NULL CREATE DATABASE fieldops_notifications;
IF DB_ID('fieldops_analytics') IS NULL CREATE DATABASE fieldops_analytics;
GO

-- Usuario con privilegios mínimos para fieldops_auth
IF NOT EXISTS (SELECT * FROM sys.server_principals WHERE name = 'fieldops_auth_app')
    CREATE LOGIN fieldops_auth_app WITH PASSWORD = '$(AUTH_DB_PASSWORD)';
GO
USE fieldops_auth;
GO
IF NOT EXISTS (SELECT * FROM sys.database_principals WHERE name = 'fieldops_auth_app')
    CREATE USER fieldops_auth_app FOR LOGIN fieldops_auth_app;
ALTER ROLE db_datareader ADD MEMBER fieldops_auth_app;
ALTER ROLE db_datawriter ADD MEMBER fieldops_auth_app;
ALTER ROLE db_ddladmin  ADD MEMBER fieldops_auth_app;
GO

-- Usuario con privilegios mínimos para fieldops_orders
IF NOT EXISTS (SELECT * FROM sys.server_principals WHERE name = 'fieldops_orders_app')
    CREATE LOGIN fieldops_orders_app WITH PASSWORD = '$(ORDERS_DB_PASSWORD)';
GO
USE fieldops_orders;
GO
IF NOT EXISTS (SELECT * FROM sys.database_principals WHERE name = 'fieldops_orders_app')
    CREATE USER fieldops_orders_app FOR LOGIN fieldops_orders_app;
ALTER ROLE db_datareader ADD MEMBER fieldops_orders_app;
ALTER ROLE db_datawriter ADD MEMBER fieldops_orders_app;
ALTER ROLE db_ddladmin  ADD MEMBER fieldops_orders_app;
GO

-- Usuario con privilegios mínimos para fieldops_notifications
IF NOT EXISTS (SELECT * FROM sys.server_principals WHERE name = 'fieldops_notifications_app')
    CREATE LOGIN fieldops_notifications_app WITH PASSWORD = '$(NOTIFICATIONS_DB_PASSWORD)';
GO
USE fieldops_notifications;
GO
IF NOT EXISTS (SELECT * FROM sys.database_principals WHERE name = 'fieldops_notifications_app')
    CREATE USER fieldops_notifications_app FOR LOGIN fieldops_notifications_app;
ALTER ROLE db_datareader ADD MEMBER fieldops_notifications_app;
ALTER ROLE db_datawriter ADD MEMBER fieldops_notifications_app;
ALTER ROLE db_ddladmin  ADD MEMBER fieldops_notifications_app;
GO

-- Usuario con privilegios mínimos para fieldops_analytics
IF NOT EXISTS (SELECT * FROM sys.server_principals WHERE name = 'fieldops_analytics_app')
    CREATE LOGIN fieldops_analytics_app WITH PASSWORD = '$(ANALYTICS_DB_PASSWORD)';
GO
USE fieldops_analytics;
GO
IF NOT EXISTS (SELECT * FROM sys.database_principals WHERE name = 'fieldops_analytics_app')
    CREATE USER fieldops_analytics_app FOR LOGIN fieldops_analytics_app;
ALTER ROLE db_datareader ADD MEMBER fieldops_analytics_app;
ALTER ROLE db_datawriter ADD MEMBER fieldops_analytics_app;
ALTER ROLE db_ddladmin  ADD MEMBER fieldops_analytics_app;
GO