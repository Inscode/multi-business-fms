package com.multi.finance.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Base64;
import java.util.Set;

/**
 * Protects real business data from demo accounts (demo_admin / demo_acc / demo_owner).
 *
 * Rules applied when a demo user is detected:
 *  1. All writes (POST/PUT/PATCH/DELETE) → 403
 *  2. Business-scoped endpoints without ?business=DEMO → 200 []
 *  3. Any ?business=<real> param → 403
 *  4. Any real business name in the URI path → 403
 *
 * Note: the frontend tryDemo() issues fake JWTs that Spring Security rejects (invalid
 * signature), so these rules are only hit by real demo-account logins (defense-in-depth).
 */
@Component
public class DemoInterceptor implements HandlerInterceptor {

    private static final Set<String> DEMO_USERNAMES = Set.of("demo_admin", "demo_acc", "demo_owner");

    private static final Set<String> REAL_BUSINESSES = Set.of(
            "RAINCO", "RETAIL_SHOP", "PLASTIC", "HARDWARE", "STATIONERY");

    // URI prefixes whose data is always business-scoped.
    // A demo user hitting these WITHOUT ?business=DEMO gets an empty response.
    private static final Set<String> BUSINESS_SCOPED_PREFIXES = Set.of(
            "/api/bills",
            "/api/payments",
            "/api/dashboard",
            "/api/worker-collections",
            "/api/collection-notes",
            "/api/expenses",
            "/api/tasks",
            "/api/returns",
            "/api/stock",
            "/api/admin/time-log"
    );

    @Override
    public boolean preHandle(HttpServletRequest request,
                              HttpServletResponse response,
                              Object handler) throws Exception {

        String username = extractUsername(request);
        if (username == null || !DEMO_USERNAMES.contains(username)) return true;

        String method = request.getMethod().toUpperCase();
        String uri    = request.getRequestURI();

        // ── 1. Block all writes ──────────────────────────────────────────
        if (!"GET".equals(method)) {
            deny(response, "Actions are disabled in demo mode.");
            return false;
        }

        // ── 2. Block real business in ?business= param ───────────────────
        String biz = request.getParameter("business");
        if (biz != null && REAL_BUSINESSES.contains(biz.toUpperCase())) {
            deny(response, "Demo accounts are restricted to the DEMO business.");
            return false;
        }

        // ── 3. Block real business name anywhere in the URI path ─────────
        for (String b : REAL_BUSINESSES) {
            if (uri.contains("/" + b)) {
                deny(response, "Demo accounts are restricted to the DEMO business.");
                return false;
            }
        }

        // ── 4. Business-scoped endpoints require ?business=DEMO ──────────
        // If the endpoint belongs to a scoped prefix and business is absent/not DEMO,
        // return an empty array so real data is never leaked.
        boolean isScoped = BUSINESS_SCOPED_PREFIXES.stream().anyMatch(uri::startsWith);
        if (isScoped && !"DEMO".equalsIgnoreCase(biz)) {
            empty(response);
            return false;
        }

        return true;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private void deny(HttpServletResponse response, String msg) throws Exception {
        response.setStatus(403);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"demo\":true,\"message\":\"" + msg + "\"}");
    }

    private void empty(HttpServletResponse response) throws Exception {
        response.setStatus(200);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("[]");
    }

    private String extractUsername(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        try {
            String payload = header.split("\\.")[1];
            int mod = payload.length() % 4;
            if (mod != 0) payload = payload + "=".repeat(4 - mod);
            // Try URL-safe decoder first (real JWTs), fall back to standard
            String json;
            try {
                json = new String(Base64.getUrlDecoder().decode(payload));
            } catch (Exception e) {
                json = new String(Base64.getDecoder().decode(payload));
            }
            int idx = json.indexOf("\"sub\"");
            if (idx == -1) return null;
            int start = json.indexOf('"', idx + 5) + 1;
            int end   = json.indexOf('"', start);
            return json.substring(start, end);
        } catch (Exception e) {
            return null;
        }
    }
}
