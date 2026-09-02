package com.lms.certificate.tenancy;

import java.util.Optional;

/** Thread-local tenant selection used by certificate persistence and outbound LMS calls. */
public final class TenantContext {
    private static final ThreadLocal<TenantConnection> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(TenantConnection tenant) { CURRENT.set(tenant); }
    public static Optional<TenantConnection> current() { return Optional.ofNullable(CURRENT.get()); }
    public static void clear() { CURRENT.remove(); }
}
