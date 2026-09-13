# ADR 0005: Elección de Capacitor frente a Cordova y estrategia de migración de complementos

## Estado
Aceptado

## Contexto
La aplicación móvil para los técnicos de campo combina tecnologías web (Angular 19 e Ionic Framework 8) con acceso directo a capacidades de hardware nativo: cámara fotográfica para evidencias, receptor satelital de geolocalización, conectividad de red y motor de base de datos SQLite embebida.

Durante más de una década, Apache Cordova fue la solución dominante para empaquetar aplicaciones web en contenedores nativos para Android e iOS. No obstante, el ecosistema de Cordova adolece de problemas estructurales en el desarrollo contemporáneo. El modelo clásico de Cordova trata la carpeta del proyecto nativo (`platforms/android`) como un artefacto derivado que se destruye y se regenera frecuentemente a través de scripts de línea de comandos, dificultando la integración con herramientas modernas de construcción como Gradle o Android Studio. Asimismo, la mayoría de sus complementos dependen de firmas con funciones de devolución de llamada (*callbacks*) en lugar de Promesas modernas tipadas en TypeScript.

## Decisión
Se selecciona Capacitor 7 como contenedor nativo y puente de comunicación con el sistema operativo móvil:

1. **El proyecto nativo como código fuente de primera clase:** En Capacitor, el directorio `android/` es un proyecto Gradle real que se versiona directamente en el repositorio Git. Esta propiedad permite ajustar configuraciones del archivo `AndroidManifest.xml`, gestionar firmas de compilación, agregar dependencias nativas y depurar errores mediante Android Studio de forma directa y predecible.
2. **APIs modernas basadas en TypeScript y Promesas:** Los complementos oficiales de Capacitor (`@capacitor/camera`, `@capacitor/geolocation`, `@capacitor/network`, `@capacitor/preferences`) exponen interfaces tipadas basadas enteramente en `async/await`, eliminando la anidación excesiva de callbacks.
3. **Estrategia de compatibilidad y migración de complementos Cordova:** Capacitor incluye una capa de abstracción interna que permite ejecutar complementos tradicionales de Cordova sin modificaciones en caso de necesidad. No obstante, para migrar un complemento legado de Cordova hacia la arquitectura limpia de Capacitor, se define el siguiente procedimiento técnico:
   - **Paso 1:** En el código nativo en Java o Kotlin, se sustituye la herencia de `CordovaPlugin` por `com.getcapacitor.Plugin` y se decora la clase con la anotación `@CapacitorPlugin(name = "MiPlugin")`.
   - **Paso 2:** En lugar de canalizar todas las llamadas a través de un único método monolítico `execute(String action, JSONArray args, CallbackContext callbackContext)`, se define un método independiente para cada acción del dominio decorado con `@PluginMethod`.
   - **Paso 3:** Se recibe un objeto de llamada `PluginCall call`. Para obtener los parámetros se invocan métodos tipados (`call.getString("param")`). Para retornar resultados se utiliza `call.resolve(JSObject)` y para señalar fallos se invoca `call.reject(errorMessage)`.
   - **Paso 4:** En el código TypeScript del cliente, se registra el complemento mediante `registerPlugin<MiPluginInterface>('MiPlugin')`, obteniendo autocompletado y validación de tipos en tiempo de compilación.

## Consecuencias

### Positivas
- Estabilidad con las versiones actuales del sistema operativo Android (API level 34 y superiores), cumpliendo con los estándares de seguridad de permisos en tiempo de ejecución.
- Ciclo de desarrollo ágil en el navegador. Las interfaces web pueden depurarse directamente en Chrome o Edge sin requerir un emulador nativo para la mayoría de las pantallas de negocio.
- Reducción del código de integración gracias a la adopción uniforme de promesas y tipado estático en el frontend.

### Negativas y limitaciones asumidas
- El equipo debe poseer conocimientos básicos de Gradle y Android Studio para resolver configuraciones nativas puntuales, en vez de depender por completo de la abstracción simplista del CLI de Cordova.
- El binario resultante presenta un tamaño en megabytes ligeramente mayor que una aplicación construida puramente con lenguajes nativos como Kotlin o Swift.