package com.appcontrol.panel.config;
import com.google.firebase.auth.FirebaseAuth;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
@Configuration
class SecurityConfig {
    @Bean SecurityFilterChain security(HttpSecurity http, ObjectProvider<FirebaseAuth> auth) throws Exception {
        return http.csrf(c -> c.disable()).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET,"/api/mobile/config","/api/connection").permitAll()
                .requestMatchers("/api/mobile/**").authenticated().requestMatchers("/api/**").denyAll()
                .anyRequest().permitAll())
            .exceptionHandling(e -> e.authenticationEntryPoint((q,r,x) -> json(r,401,"Authentication required"))
                .accessDeniedHandler((q,r,x) -> json(r,403,"Administrator access required")))
            .addFilterBefore(new OncePerRequestFilter() {
                @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
                    String header = req.getHeader("Authorization");
                    if (header != null && header.startsWith("Bearer ")) {
                        var firebase = auth.getIfAvailable();
                        if (firebase == null) { json(res,503,"Firebase is not configured"); return; }
                        try {
                            var token = firebase.verifyIdToken(header.substring(7), true);
                            var roles = Boolean.TRUE.equals(token.getClaims().get("admin")) ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN")) : List.of(new SimpleGrantedAuthority("ROLE_USER"));
                            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(token.getUid(), null, roles));
                        } catch (Exception ex) { json(res,401,"Invalid or expired token"); return; }
                    }
                    chain.doFilter(req,res);
                }
            }, UsernamePasswordAuthenticationFilter.class).build();
    }
    static void json(HttpServletResponse res,int status,String message) throws IOException {
        res.setStatus(status); res.setContentType("application/json"); res.getWriter().write("{\"error\":\""+message+"\"}");
    }
}
