-- El usuario admin de la V2 se creó con una contraseña de prueba cuyo hash está en el
-- repositorio, y antes el administrador no podía cambiarla. Ahora sí puede: si sigue
-- con esa contraseña de prueba, se le obliga a cambiarla en su próximo inicio de sesión.
-- No afecta a un admin que ya la haya cambiado.
UPDATE usuario
SET debe_cambiar_contrasena = 1
WHERE nombre_usuario = 'admin'
  AND contrasena_hash = '$2a$10$72E8Rtxz1gWvE.TVLJUyde8qQla9i7E/k8.mlbVJgcizih03pEHnK';
