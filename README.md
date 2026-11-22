# Actividad 3.2: Firebase y Firestore

Aplicación Android en Java para registrar mascotas en Cloud Firestore.

## Abrir el proyecto
1. Abre la carpeta `FirebaseMascotas` en Android Studio y espera la sincronización de Gradle.
2. Ejecuta la app en un emulador o teléfono con Internet.
3. Ingresa nombre, especie, raza y edad, y pulsa **Guardar mascota**.

La lista se actualiza sola cuando se agrega o borra un registro. También puedes usar **Actualizar lista**. Los registros idénticos aparecen agrupados; al eliminarlos se pide confirmación y se borra el grupo completo. La app no permite guardar otra copia exacta.

## Firebase
La app usa el paquete `cl.stomas.firebase` y la colección `mascotas`. Cada registro queda asociado al usuario anónimo que lo creó. Las reglas de Firestore permiten consultar y borrar solo registros propios.

El archivo `app/google-services.json` está incluido en esta copia para abrirla conectada. Está ignorado por GitHub; si clonas el repositorio, descarga el archivo de Firebase Console y ponlo en `app/`.

## Preguntas de cierre

**¿Qué aprendiste?** Aprendí a guardar información en Firestore desde una app Android y a cargarla para mostrarla en pantalla.

**¿En qué ámbitos puedes aplicar lo realizado?** Se puede usar en aplicaciones que necesiten guardar datos, por ejemplo, para inventarios, reservas o fichas.

**¿Pudiste realizar toda la actividad con los ejemplos entregados?** Usé los ejemplos como guía para conectar Firebase y trabajar con la colección de mascotas. La app queda preparada para ejecutarse desde Android Studio.
