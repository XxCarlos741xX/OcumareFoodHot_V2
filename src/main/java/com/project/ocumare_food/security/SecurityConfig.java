package com.project.ocumare_food.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Seguridad sin sesión: cada petición trae "Authorization: Bearer <token>".
 *
 * Matriz de permisos (lo que no está aquí exige al menos estar autenticado):
 *  - Público:            login, menú (GET) y envío de pedidos desde la web.
 *  - MESERO/GERENTE/ADMIN: pedidos, ver mesas, ver tasa vigente, ver turno abierto.
 *  - GERENTE/ADMIN:      menú (crear/editar), mesas (crear/editar), tasa de cambio,
 *                        abrir/cerrar turno, usuarios, dashboard.
 *  - ADMIN:              /api/admin/**.
 * Reglas finas (p. ej. "un gerente solo crea meseros") se validan en el servicio.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(OcumareProperties.class)
public class SecurityConfig {

    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_USUARIO_ID = "uid";


    @Bean
    SecurityFilterChain filtros(HttpSecurity http, JwtAuthenticationConverter jwtConverter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())                 // sin cookies de sesión, CSRF no aplica
            .cors(cors -> {})
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(h -> h.frameOptions(f -> f.sameOrigin()))   // para la consola H2 en desarrollo
            .authorizeHttpRequests(auth -> auth
                // La app web (index.html, JS, CSS, logo) es pública: los datos se protegen en /api.
                .requestMatchers(req -> !req.getRequestURI().startsWith(req.getContextPath() + "/api/")).permitAll()
                // Público
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/menu/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/web/pedidos").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/web/pedidos/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/imagenes/*").permitAll()
                .requestMatchers("/api/imagenes/**").hasAnyRole("GERENTE", "ADMIN")
                .requestMatchers("/h2-console/**").permitAll()   // solo existe con el perfil dev
                .requestMatchers("/error").permitAll()
                // Solo administrador
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // Gerencia
                .requestMatchers("/api/usuarios/**", "/api/dashboard/**").hasAnyRole("GERENTE", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/productos/**", "/api/categorias/**", "/api/mesas/**",
                                 "/api/tasas/vigente", "/api/turnos/actual").authenticated()
                .requestMatchers("/api/productos/**", "/api/categorias/**", "/api/mesas/**",
                                 "/api/tasas/**", "/api/turnos/**").hasAnyRole("GERENTE", "ADMIN")
                // Operación diaria
                .requestMatchers("/api/pedidos/**").hasAnyRole("MESERO", "GERENTE", "ADMIN")
                .anyRequest().authenticated())
            .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(jwtConverter)));
        return http.build();
    }

    /** Lee el claim "roles" del token y lo convierte en ROLE_MESERO / ROLE_GERENTE / ROLE_ADMIN. */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName(CLAIM_ROLES);
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter conv = new JwtAuthenticationConverter();
        conv.setJwtGrantedAuthoritiesConverter(roles);
        return conv;
    }

    @Bean
    SecretKey claveJwt(OcumareProperties props) {
        String secreto = props.jwt() == null ? null : props.jwt().secret();
        if (secreto == null || secreto.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "Falta ocumare.jwt.secret (variable JWT_SECRET) o tiene menos de 32 caracteres");
        }
        return new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey clave) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(clave));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey clave) {
        return NimbusJwtDecoder.withSecretKey(clave).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService usuarios, PasswordEncoder encoder) {
        DaoAuthenticationProvider proveedor = new DaoAuthenticationProvider(usuarios);
        proveedor.setPasswordEncoder(encoder);
        return new ProviderManager(proveedor);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(OcumareProperties props) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(props.cors() == null || props.cors().origenes() == null
                ? List.of() : props.cors().origenes());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cfg);
        return source;
    }
}
