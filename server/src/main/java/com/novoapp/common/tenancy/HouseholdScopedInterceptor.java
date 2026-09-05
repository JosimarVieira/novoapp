package com.novoapp.common.tenancy;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

@Interceptor
@HouseholdScoped
// Roda DENTRO do interceptor de @Transactional (PLATFORM_BEFORE + 200): o
// SET LOCAL precisa acontecer com a transacao ja aberta.
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 300)
public class HouseholdScopedInterceptor {

    @Inject
    TenantSession tenantSession;

    @AroundInvoke
    Object aroundInvoke(InvocationContext context) throws Exception {
        return tenantSession.runWith(DatabaseRole.APP, TenantContext.requireHousehold(), context::proceed);
    }
}
