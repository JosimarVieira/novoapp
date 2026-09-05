package com.novoapp.common.tenancy;

import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Roda o metodo sob o papel pre-tenant (ADR-0022): resolucao de identidade,
 * onboarding e ingestao de mensagem.
 *
 * <p>Este e o unico caminho que pode enxergar linha antes de existir household
 * resolvido. Se o {@link TenantContext} ja tiver um household -- caso da
 * atualizacao de <code>inbound_message.household_id</code> depois que a
 * identidade resolveu -- ele tambem e aplicado, porque a policy da tabela e
 * <code>household_id = tenant() OR household_id IS NULL</code> e o
 * <code>WITH CHECK</code> do valor novo precisa casar.
 *
 * <p>Nunca use em codigo de <code>finance</code>, <code>shopping</code> ou
 * <code>tasks</code>: barrado por teste ArchUnit.
 */
@InterceptorBinding
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface IdentityScoped {
}
