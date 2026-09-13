USE fieldops_orders;

-- Clientes de demostración
IF NOT EXISTS (SELECT 1 FROM clients WHERE email = 'contacto@acmeindustrias.com')
BEGIN
    INSERT INTO clients (name, email, phone, address, created_at)
    VALUES ('Acme Industrias S.A.', 'contacto@acmeindustrias.com', '+34 912 345 678', 'Parque Tecnológico Nave 12, Madrid', GETDATE());
END;

IF NOT EXISTS (SELECT 1 FROM clients WHERE email = 'mantenimiento@logisticsiberia.com')
BEGIN
    INSERT INTO clients (name, email, phone, address, created_at)
    VALUES ('Logistics Iberia S.L.', 'mantenimiento@logisticsiberia.com', '+34 934 567 890', 'Av. del Puerto 45, Barcelona', GETDATE());
END;

IF NOT EXISTS (SELECT 1 FROM clients WHERE email = 'soporte@retailexpress.es')
BEGIN
    INSERT INTO clients (name, email, phone, address, created_at)
    VALUES ('Retail Express Distribución', 'soporte@retailexpress.es', '+34 963 852 741', 'Polígono Industrial Este Calle 4, Valencia', GETDATE());
END;

-- Técnicos de demostración (vinculados a los usuarios de auth-service)
IF NOT EXISTS (SELECT 1 FROM technicians WHERE user_id = 2)
BEGIN
    INSERT INTO technicians (user_id, full_name, specialty, active, created_at)
    VALUES (2, 'Carlos Tecnico 1', 'Electromecánica y Climatización', 1, GETDATE());
END;

IF NOT EXISTS (SELECT 1 FROM technicians WHERE user_id = 3)
BEGIN
    INSERT INTO technicians (user_id, full_name, specialty, active, created_at)
    VALUES (3, 'Ana Tecnico 2', 'Telecomunicaciones y Redes', 1, GETDATE());
END;

IF NOT EXISTS (SELECT 1 FROM technicians WHERE user_id = 4)
BEGIN
    INSERT INTO technicians (user_id, full_name, specialty, active, created_at)
    VALUES (4, 'Luis Tecnico 3', 'Sistemas Hidráulicos y Neumáticos', 1, GETDATE());
END;

-- Órdenes de demostración en diversos estados
DECLARE @ClientId1 BIGINT = (SELECT TOP 1 id FROM clients WHERE email = 'contacto@acmeindustrias.com');
DECLARE @ClientId2 BIGINT = (SELECT TOP 1 id FROM clients WHERE email = 'mantenimiento@logisticsiberia.com');
DECLARE @TechId1 BIGINT = (SELECT TOP 1 id FROM technicians WHERE user_id = 2);
DECLARE @TechId2 BIGINT = (SELECT TOP 1 id FROM technicians WHERE user_id = 3);

IF NOT EXISTS (SELECT 1 FROM work_orders WHERE code = 'WO-2026-0001')
BEGIN
    INSERT INTO work_orders (code, title, description, client_id, assigned_technician_id, status, priority, scheduled_at, started_at, completed_at, created_at, updated_at)
    VALUES ('WO-2026-0001', 'Mantenimiento preventivo de grupo electrógeno', 'Revisión periódica y cambio de filtros del generador principal.', @ClientId1, @TechId1, 'ASSIGNED', 'HIGH', DATEADD(hour, 2, GETDATE()), NULL, NULL, GETDATE(), GETDATE());
END;

IF NOT EXISTS (SELECT 1 FROM work_orders WHERE code = 'WO-2026-0002')
BEGIN
    INSERT INTO work_orders (code, title, description, client_id, assigned_technician_id, status, priority, scheduled_at, started_at, completed_at, created_at, updated_at)
    VALUES ('WO-2026-0002', 'Reparación de switch troncal de comunicaciones', 'Pérdida intermitente de paquetes en enlace de fibra nave 3.', @ClientId2, @TechId2, 'IN_PROGRESS', 'CRITICAL', DATEADD(hour, -2, GETDATE()), DATEADD(hour, -1, GETDATE()), NULL, DATEADD(hour, -3, GETDATE()), GETDATE());
END;

IF NOT EXISTS (SELECT 1 FROM work_orders WHERE code = 'WO-2026-0003')
BEGIN
    INSERT INTO work_orders (code, title, description, client_id, assigned_technician_id, status, priority, scheduled_at, started_at, completed_at, created_at, updated_at)
    VALUES ('WO-2026-0003', 'Calibración de sensores de temperatura en cámara fría', 'Ajuste termométrico y verificación de sondas PT100.', @ClientId1, @TechId1, 'COMPLETED', 'MEDIUM', DATEADD(day, -1, GETDATE()), DATEADD(hour, -20, GETDATE()), DATEADD(hour, -18, GETDATE()), DATEADD(day, -1, GETDATE()), DATEADD(hour, -18, GETDATE()));
END;

IF NOT EXISTS (SELECT 1 FROM work_orders WHERE code = 'WO-2026-0004')
BEGIN
    INSERT INTO work_orders (code, title, description, client_id, assigned_technician_id, status, priority, scheduled_at, started_at, completed_at, created_at, updated_at)
    VALUES ('WO-2026-0004', 'Inspección de compresor de aire comprimido', 'Revisión de presión y purga de condensados en línea de montaje.', @ClientId2, NULL, 'DRAFT', 'LOW', DATEADD(day, 1, GETDATE()), NULL, NULL, GETDATE(), GETDATE());
END;