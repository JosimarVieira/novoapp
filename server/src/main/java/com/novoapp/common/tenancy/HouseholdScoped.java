package com.novoapp.common.tenancy;

import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Roda o metodo sob o papel de dominio, com <code>app.household_id</code>
 * setado a partir do {@link TenantContext} (ADR-0003).
 *
 * <p>Exige transacao ativa: <code>SET LOCAL</code> fora de transacao nao tem
 * efeito, e RLS sem household setado nega tudo. O interceptor falha alto em vez
 * de deixar a query voltar vazia -- retorno vazio silencioso e justamente a
 * consequencia negativa que a propria ADR-0003 registra como risco.
 */
@InterceptorBinding
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface HouseholdScoped {
}
