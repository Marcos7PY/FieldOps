# FieldOps

Sistema para la gestión y seguimiento del ciclo de vida de órdenes de servicio técnico en campo. La plataforma permite a los supervisores coordinar trabajos desde un panel web y a los técnicos reportar avances, capturar evidencias multimedia y registrar coordenadas geográficas desde una aplicación móvil con soporte sin conexión.

La arquitectura desacopla el procesamiento operativo mediante eventos distribuidos publicados bajo el patrón outbox transaccional, alimentando servicios de notificación y un modelo de lectura optimizado para analítica histórica y consultas agregadas.