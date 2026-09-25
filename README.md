# Fan Bridge v0.1

Prueba Android para usar un Samsung Galaxy Z Flip 4 como transmisor BLE hacia un ventilador compatible con FanLamp Pro.

## Funciones de esta versión
- ENCENDER: reproduce el comando BLE capturado y validado para **velocidad 3**.
- APAGAR: reproduce el comando BLE capturado y validado para **FAN OFF**.
- No necesita FanLamp Pro para transmitir.
- Todavía no incluye Alexa ni ejecución permanente con pantalla bloqueada; eso se agrega después de validar esta prueba local.

## Paquetes usados
### Velocidad 3
`0201011B03F00820823639FD5FC739F96D1B64770FB184FF00A5C6532BB458`

### FAN OFF
`0201011B03F0082082361FFD5FC739F96D1B64770FB180FF9B72C6532B932E`

Los bytes posteriores a `1B03` se representan como 13 UUID Bluetooth de 16 bits, tal como hizo nRF Connect al clonar el anuncio que ya controló correctamente el ventilador.

## Compilar
Abrir el proyecto en Android Studio y ejecutar **Build > Build APK(s)**.

Requisitos sugeridos:
- Android Studio reciente
- Android SDK 35
- JDK 17+

El APK debug queda normalmente en:
`app/build/outputs/apk/debug/app-debug.apk`

## Primer uso
1. Instalar en el Flip 4.
2. Conceder permiso de Bluetooth / dispositivos cercanos.
3. Activar Bluetooth.
4. Mantener el teléfono a una distancia similar a la usada con nRF Connect.
5. Pulsar ENCENDER y comprobar que el ventilador entra en velocidad 3.
6. Pulsar APAGAR y comprobar que el motor se detiene.

Si ambos funcionan, la siguiente versión puede añadir un servicio en segundo plano y el puente con Alexa.

## Compilar sin instalar Android Studio (GitHub Actions)
Este proyecto incluye `.github/workflows/build-apk.yml`.

1. Sube el contenido de la carpeta `FanBridge` a un repositorio de GitHub.
2. Abre la pestaña **Actions** del repositorio.
3. Entra a **Build Fan Bridge APK**.
4. Pulsa **Run workflow**.
5. Cuando termine en verde, abre la ejecución y descarga el artefacto **FanBridge-debug-apk**.
6. Dentro del ZIP del artefacto estará `app-debug.apk`.
