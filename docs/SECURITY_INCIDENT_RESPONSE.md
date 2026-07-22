# Respuesta al secreto histórico de firma

## Estado

Durante la auditoría del 21 de julio de 2026 se encontró una contraseña de firma
en un archivo local que había sido versionado. El archivo ya está eliminado del
árbol de trabajo e ignorado, pero borrar el archivo en un commit **no** elimina el
valor de commits anteriores. Este documento no reproduce la credencial.

## Orden obligatorio de respuesta

1. **Rotar primero.** Cambiar la contraseña comprometida y cualquier otro secreto
   donde se haya reutilizado. No asumir que un repositorio privado limita la exposición.
2. **Revisar la clave de subida.** Confirmar en Play Console si la credencial protegía
   el upload keystore y, si corresponde, solicitar el reinicio de la clave de subida.
   Play App Signing y la clave de firma de aplicaciones se gestionan fuera de este repo.
3. **Inventariar exposición.** Revisar forks, Actions artifacts/logs, cachés, backups,
   clones de colaboradores y gestores de secretos. Registrar fechas, responsables y
   evidencia de rotación sin copiar secretos al ticket.
4. **Coordinar la reescritura.** Anunciar una ventana de mantenimiento, proteger una
   copia forense de acceso restringido y acordar la invalidación de clones/ramas abiertas.
5. **Purgar todas las referencias.** Un responsable autorizado puede usar
   `git filter-repo` para retirar las rutas afectadas de todas las ramas y tags, revisar
   el resultado local y recién entonces hacer el force-push coordinado. No ejecutar una
   reescritura parcial ni antes de la rotación.
6. **Invalidar y volver a clonar.** Los colaboradores deben descartar clones antiguos;
   no deben hacer merge de ramas basadas en el historial previo porque reintroducirían
   el secreto.
7. **Verificar.** Ejecutar Gitleaks sobre el historial reescrito y confirmar que GitHub,
   los mirrors y los artifacts no conservan la credencial. Eliminar entonces la huella
   temporal de `.gitleaksignore`, volver a ejecutar el scan sin excepciones y mantener
   el workflow activo para prevenir reincidencias.

## Criterio de cierre

El incidente sólo puede marcarse cerrado cuando hay evidencia de rotación, decisión
sobre la clave de subida, purga coordinada de todas las referencias remotas, clones
anteriores invalidados y un escaneo histórico limpio. Los cambios locales de este
repositorio cubren prevención y documentación, pero no pueden completar esas acciones
operativas por sí solos.
