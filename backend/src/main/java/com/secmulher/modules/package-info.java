/**
 * Módulos da aplicação.
 *
 * <ul>
 *   <li>{@code core}: tudo que é geral e compartilhado (categorias, dynamic entities, clients,
 *       usuários, permissões, arquivos, linha do tempo, segurança, exceções).</li>
 *   <li>Um pacote por setor (ex.: {@code finance}), com seus próprios models, rotas e regras.</li>
 * </ul>
 *
 * <p>Todo módulo segue o mesmo formato: {@code controllers/}, {@code domain/} e {@code infra/}.
 * Módulos de setor podem depender do {@code core}; o {@code core} nunca depende de um setor, e um
 * setor não depende de outro. Como {@code Application} fica em {@code com.secmulher}, componentes,
 * entidades e repositórios de qualquer módulo são encontrados automaticamente pelo Spring.
 */
package com.secmulher.modules;
