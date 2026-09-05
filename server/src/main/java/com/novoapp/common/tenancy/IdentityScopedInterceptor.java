package com.novoapp.common.tenancy;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

@Interceptor
@IdentityScoped
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 300)
public class IdentityScopedInterceptor {

    @Inject
    TenantSession tenantSession;

    @AroundInvoke
    Object aroundInvoke(InvocationContext context) throws Exception {
        // Diferente do escopo de dominio, aqui o household pode nao existir
        // ainda -- e o caso normal de toda mensagem de numero desconhecido.
        return tenantSession.runWith(DatabaseRole.IDENTITY,
                TenantContext.currentHousehold().orElse(null), context::proceed);
    }
}
