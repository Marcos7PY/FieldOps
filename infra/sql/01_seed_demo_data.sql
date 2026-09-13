USE fieldops_orders;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;

IF NOT EXISTS (SELECT 1 FROM client WHERE tax_id = 'B-12345678')
BEGIN
    INSERT INTO client (business_name, tax_id, address, latitude, longitude, phone, active)
    VALUES ('Acme Industrias S.A.', 'B-12345678', 'Parque Tecnológico Nave 12, Madrid', 40.416775, -3.703790, '+34 912 345 678', 1);
END;

IF NOT EXISTS (SELECT 1 FROM client WHERE tax_id = 'B-87654321')
BEGIN
    INSERT INTO client (business_name, tax_id, address, latitude, longitude, phone, active)
    VALUES ('Logistics Iberia S.L.', 'B-87654321', 'Av. del Puerto 45, Barcelona', 41.385064, 2.173404, '+34 934 567 890', 1);
END;

IF NOT EXISTS (SELECT 1 FROM client WHERE tax_id = 'B-11223344')
BEGIN
    INSERT INTO client (business_name, tax_id, address, latitude, longitude, phone, active)
    VALUES ('Retail Express Distribución', 'B-11223344', 'Polígono Industrial Este Calle 4, Valencia', 39.469907, -0.376288, '+34 963 852 741', 1);
END;

DECLARE @ClientId1 BIGINT = (SELECT TOP 1 id FROM client WHERE tax_id = 'B-12345678');
DECLARE @ClientId2 BIGINT = (SELECT TOP 1 id FROM client WHERE tax_id = 'B-87654321');
DECLARE @ClientId3 BIGINT = (SELECT TOP 1 id FROM client WHERE tax_id = 'B-11223344');

IF NOT EXISTS (SELECT 1 FROM work_order WHERE code = 'WO-2026-0001')
BEGIN
    INSERT INTO work_order (code, title, description, client_id, assigned_technician_id, created_by, status, priority, scheduled_at, started_at, completed_at, created_at, version)
    VALUES ('WO-2026-0001', 'Mantenimiento preventivo de grupo electrógeno', 'Revisión periódica y cambio de filtros del generador principal.', @ClientId1, 2, 1, 'ASSIGNED', 'HIGH', DATEADD(hour, 2, GETDATE()), NULL, NULL, GETDATE(), 0);
END;

IF NOT EXISTS (SELECT 1 FROM work_order WHERE code = 'WO-2026-0002')
BEGIN
    INSERT INTO work_order (code, title, description, client_id, assigned_technician_id, created_by, status, priority, scheduled_at, started_at, completed_at, created_at, version)
    VALUES ('WO-2026-0002', 'Reparación de switch troncal de comunicaciones', 'Pérdida intermitente de paquetes en enlace de fibra nave 3.', @ClientId2, 3, 1, 'IN_PROGRESS', 'CRITICAL', DATEADD(hour, -2, GETDATE()), DATEADD(hour, -1, GETDATE()), NULL, DATEADD(hour, -3, GETDATE()), 0);
END;

IF NOT EXISTS (SELECT 1 FROM work_order WHERE code = 'WO-2026-0003')
BEGIN
    INSERT INTO work_order (code, title, description, client_id, assigned_technician_id, created_by, status, priority, scheduled_at, started_at, completed_at, created_at, version)
    VALUES ('WO-2026-0003', 'Calibración de sensores de temperatura en cámara fría', 'Ajuste termométrico y verificación de sondas PT100.', @ClientId1, 2, 1, 'COMPLETED', 'MEDIUM', DATEADD(day, -1, GETDATE()), DATEADD(hour, -20, GETDATE()), DATEADD(hour, -18, GETDATE()), DATEADD(day, -1, GETDATE()), 0);
END;

IF NOT EXISTS (SELECT 1 FROM work_order WHERE code = 'WO-2026-0004')
BEGIN
    INSERT INTO work_order (code, title, description, client_id, assigned_technician_id, created_by, status, priority, scheduled_at, started_at, completed_at, created_at, version)
    VALUES ('WO-2026-0004', 'Inspección de compresor de aire comprimido', 'Revisión de presión y purga de condensados en línea de montaje.', @ClientId2, NULL, 1, 'DRAFT', 'LOW', DATEADD(day, 1, GETDATE()), NULL, NULL, GETDATE(), 0);
END;