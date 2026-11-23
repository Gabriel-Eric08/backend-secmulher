/**
 * Módulo de emendas parlamentares.
 *
 * <p>Pode usar tudo do {@code core} (exceções, {@code AccessControlService},
 * {@code CurrentUserProvider}, entidades). O {@code core} não deve importar nada daqui, e este
 * módulo não importa de outro setor.
 *
 * <p>Tabelas do módulo entram em {@code db/init.sql}, depois das tabelas do core
 * (o Hibernate roda com {@code ddl-auto=validate}).
 */
package com.secmulher.modules.amendments;
