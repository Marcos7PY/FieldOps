SET NOCOUNT ON;
SET XACT_ABORT ON;

USE fieldops_orders;
GO

IF (SELECT COUNT(*) FROM client) < 300
BEGIN
    WITH N10 AS (
        SELECT v FROM (VALUES (0),(1),(2),(3),(4),(5),(6),(7),(8),(9)) AS t(v)
    ),
    Numbers AS (
        SELECT a.v + b.v * 10 + c.v * 100 + 1 AS n
        FROM N10 a CROSS JOIN N10 b CROSS JOIN N10 c
        WHERE a.v + b.v * 10 + c.v * 100 + 1 <= 300
    )
    INSERT INTO client (business_name, tax_id, address, latitude, longitude, phone, active)
    SELECT 
        N'Cliente Corporativo ' + CAST(n AS NVARCHAR(10)),
        'B-' + RIGHT('00000000' + CAST(n AS VARCHAR(10)), 8),
        N'Avenida Industrial ' + CAST(n AS NVARCHAR(10)) + N', Polígono Norte',
        CAST(40.400000 + ((n % 100) * 0.002500) AS DECIMAL(9,6)),
        CAST(-3.700000 - ((n % 100) * 0.002100) AS DECIMAL(9,6)),
        '+34 91' + RIGHT('0000000' + CAST(n AS VARCHAR(10)), 7),
        1
    FROM Numbers
    WHERE NOT EXISTS (
        SELECT 1 FROM client WHERE tax_id = 'B-' + RIGHT('00000000' + CAST(Numbers.n AS VARCHAR(10)), 8)
    );
END
GO

IF DB_ID('fieldops_auth') IS NOT NULL
BEGIN
    DECLARE @TechRoleId BIGINT = (SELECT id FROM fieldops_auth.dbo.role WHERE name = 'ROLE_TECHNICIAN');
    
    IF @TechRoleId IS NOT NULL
    BEGIN
        WITH TechNumbers AS (
            SELECT v AS n FROM (VALUES 
                (1),(2),(3),(4),(5),(6),(7),(8),(9),(10),
                (11),(12),(13),(14),(15),(16),(17),(18),(19),(20)
            ) AS t(v)
        )
        INSERT INTO fieldops_auth.dbo.app_user (username, password_hash, full_name, email, active, created_at)
        SELECT 
            'tecnico' + CAST(n AS VARCHAR(5)),
            '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi',
            N'Técnico Operativo ' + CAST(n AS NVARCHAR(5)),
            'tecnico' + CAST(n AS VARCHAR(5)) + '@fieldops.com',
            1,
            SYSUTCDATETIME()
        FROM TechNumbers
        WHERE NOT EXISTS (
            SELECT 1 FROM fieldops_auth.dbo.app_user WHERE username = 'tecnico' + CAST(TechNumbers.n AS VARCHAR(5))
        );

        INSERT INTO fieldops_auth.dbo.user_role (user_id, role_id)
        SELECT u.id, @TechRoleId
        FROM fieldops_auth.dbo.app_user u
        WHERE u.username LIKE 'tecnico%'
          AND NOT EXISTS (
              SELECT 1 FROM fieldops_auth.dbo.user_role ur WHERE ur.user_id = u.id AND ur.role_id = @TechRoleId
          );
    END
END
GO

DECLARE @TargetOrders INT = 500000;
DECLARE @CurrentCount INT = (SELECT COUNT(*) FROM work_order);

IF @CurrentCount < @TargetOrders
BEGIN
    DECLARE @OrdersToInsert INT = @TargetOrders - @CurrentCount;
    DECLARE @BatchSize INT = 25000;
    DECLARE @TotalBatches INT = CEILING(CAST(@OrdersToInsert AS FLOAT) / @BatchSize);
    DECLARE @BatchIndex INT = 0;

    CREATE TABLE #ClientIds (
        row_id INT IDENTITY(1,1) PRIMARY KEY,
        client_id BIGINT NOT NULL
    );

    INSERT INTO #ClientIds (client_id)
    SELECT id FROM client ORDER BY id;

    DECLARE @ClientCount INT = (SELECT COUNT(*) FROM #ClientIds);

    CREATE TABLE #BatchOrders (
        work_order_id BIGINT NOT NULL,
        status VARCHAR(20) NOT NULL,
        assigned_technician_id BIGINT NULL,
        created_at DATETIME2 NOT NULL,
        scheduled_at DATETIME2 NULL,
        started_at DATETIME2 NULL,
        completed_at DATETIME2 NULL
    );

    DECLARE @BaseDate DATETIME2 = DATEADD(DAY, -730, SYSUTCDATETIME());

    WHILE @BatchIndex < @TotalBatches
    BEGIN
        DECLARE @BatchOffset INT = @CurrentCount + (@BatchIndex * @BatchSize);
        DECLARE @CurrentBatchLimit INT = CASE 
            WHEN (@BatchIndex + 1) = @TotalBatches THEN @OrdersToInsert - (@BatchIndex * @BatchSize)
            ELSE @BatchSize
        END;

        TRUNCATE TABLE #BatchOrders;

        BEGIN TRANSACTION;

        WITH 
        N10 AS (
            SELECT v FROM (VALUES (0),(1),(2),(3),(4),(5),(6),(7),(8),(9)) AS t(v)
        ),
        N1000 AS (
            SELECT a.v + (b.v * 10) + (c.v * 100) AS n
            FROM N10 a CROSS JOIN N10 b CROSS JOIN N10 c
        ),
        Tally AS (
            SELECT a.n + (b.n * 1000) AS row_num
            FROM N1000 a CROSS JOIN N1000 b
            WHERE a.n + (b.n * 1000) < @CurrentBatchLimit
        ),
        CalculatedOrders AS (
            SELECT
                @BatchOffset + row_num + 1 AS global_id,
                CASE 
                    WHEN (row_num % 100) < 70 THEN 'COMPLETED'
                    WHEN (row_num % 100) < 85 THEN 'CANCELLED'
                    WHEN (row_num % 100) < 91 THEN 'ASSIGNED'
                    WHEN (row_num % 100) < 96 THEN 'IN_PROGRESS'
                    ELSE 'DRAFT'
                END AS st,
                CASE 
                    WHEN (row_num % 20) < 10 THEN 'MEDIUM'
                    WHEN (row_num % 20) < 15 THEN 'LOW'
                    WHEN (row_num % 20) < 19 THEN 'HIGH'
                    ELSE 'CRITICAL'
                END AS prio,
                (((@BatchOffset + row_num) % @ClientCount) + 1) AS client_seq,
                (((@BatchOffset + row_num) % 20) + 2) AS tech_candidate,
                DATEADD(SECOND, ((@BatchOffset + row_num) % (730 * 86400)), @BaseDate) AS ord_created_at,
                row_num
            FROM Tally
        ),
        NormalizedOrders AS (
            SELECT 
                c.global_id,
                c.st,
                c.prio,
                ci.client_id AS cid,
                c.ord_created_at,
                CASE 
                    WHEN c.st = 'DRAFT' THEN NULL
                    WHEN c.st = 'CANCELLED' AND (c.row_num % 2 = 0) THEN NULL
                    ELSE c.tech_candidate
                END AS tech_id,
                CASE 
                    WHEN c.st = 'DRAFT' THEN NULL
                    ELSE DATEADD(MINUTE, 720 + ((c.global_id % 48) * 60), c.ord_created_at)
                END AS ord_scheduled_at,
                CASE 
                    WHEN c.st = 'DRAFT' THEN 0
                    WHEN c.st = 'ASSIGNED' THEN 1
                    WHEN c.st = 'IN_PROGRESS' THEN 2
                    ELSE 3
                END AS ver
            FROM CalculatedOrders c
            INNER JOIN #ClientIds ci ON ci.row_id = c.client_seq
        ),
        FinalOrders AS (
            SELECT 
                global_id,
                st,
                prio,
                cid,
                tech_id,
                ord_created_at,
                ord_scheduled_at,
                CASE 
                    WHEN st IN ('IN_PROGRESS', 'COMPLETED') THEN DATEADD(MINUTE, 15 + (global_id % 60), ord_scheduled_at)
                    ELSE NULL
                END AS ord_started_at,
                CASE 
                    WHEN st = 'COMPLETED' THEN DATEADD(MINUTE, 45 + (global_id % 240), DATEADD(MINUTE, 15 + (global_id % 60), ord_scheduled_at))
                    ELSE NULL
                END AS ord_completed_at,
                ver
            FROM NormalizedOrders
        )
        INSERT INTO work_order (
            code, title, description, status, priority, client_id,
            assigned_technician_id, created_by, created_at, scheduled_at,
            started_at, completed_at, version
        )
        OUTPUT 
            inserted.id,
            inserted.status,
            inserted.assigned_technician_id,
            inserted.created_at,
            inserted.scheduled_at,
            inserted.started_at,
            inserted.completed_at
        INTO #BatchOrders (
            work_order_id, status, assigned_technician_id,
            created_at, scheduled_at, started_at, completed_at
        )
        SELECT 
            'WO-' + CAST(YEAR(ord_created_at) AS VARCHAR(4)) + '-' + RIGHT('0000000' + CAST(global_id AS VARCHAR(10)), 7),
            N'Mantenimiento Técnico General #' + CAST(global_id AS NVARCHAR(10)),
            N'Inspección periódica y mantenimiento correctivo según protocolo operativo estándar.',
            st,
            prio,
            cid,
            tech_id,
            1,
            ord_created_at,
            ord_scheduled_at,
            ord_started_at,
            ord_completed_at,
            ver
        FROM FinalOrders;

        INSERT INTO work_order_status_history (work_order_id, previous_status, new_status, changed_by, changed_at, notes)
        SELECT 
            work_order_id,
            NULL,
            'DRAFT',
            1,
            created_at,
            N'Orden generada en el sistema'
        FROM #BatchOrders;

        INSERT INTO work_order_status_history (work_order_id, previous_status, new_status, changed_by, changed_at, notes)
        SELECT 
            work_order_id,
            'DRAFT',
            'ASSIGNED',
            1,
            DATEADD(MINUTE, 10, created_at),
            N'Orden asignada a cuadrilla técnica'
        FROM #BatchOrders
        WHERE status IN ('ASSIGNED', 'IN_PROGRESS', 'COMPLETED')
           OR (status = 'CANCELLED' AND assigned_technician_id IS NOT NULL);

        INSERT INTO work_order_status_history (work_order_id, previous_status, new_status, changed_by, changed_at, notes)
        SELECT 
            work_order_id,
            'ASSIGNED',
            'IN_PROGRESS',
            ISNULL(assigned_technician_id, 2),
            started_at,
            N'Técnico reporta inicio de labores en sitio'
        FROM #BatchOrders
        WHERE status IN ('IN_PROGRESS', 'COMPLETED');

        INSERT INTO work_order_status_history (work_order_id, previous_status, new_status, changed_by, changed_at, notes)
        SELECT 
            work_order_id,
            'IN_PROGRESS',
            'COMPLETED',
            ISNULL(assigned_technician_id, 2),
            completed_at,
            N'Servicio completado satisfactoriamente con evidencias registradas'
        FROM #BatchOrders
        WHERE status = 'COMPLETED';

        INSERT INTO work_order_status_history (work_order_id, previous_status, new_status, changed_by, changed_at, notes)
        SELECT 
            work_order_id,
            CASE WHEN assigned_technician_id IS NULL THEN 'DRAFT' ELSE 'ASSIGNED' END,
            'CANCELLED',
            1,
            DATEADD(MINUTE, 30, created_at),
            N'Cancelación administrativa por solicitud del cliente'
        FROM #BatchOrders
        WHERE status = 'CANCELLED';

        COMMIT TRANSACTION;

        SET @BatchIndex = @BatchIndex + 1;
        CHECKPOINT;
    END;

    DROP TABLE #BatchOrders;
    DROP TABLE #ClientIds;
END;
GO
