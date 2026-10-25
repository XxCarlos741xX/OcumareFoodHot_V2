package com.project.ocumare_food.usuarios.seguridad;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.project.ocumare_food.usuarios.repository.UsuarioRepository;
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
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Seguridad sin sesión en el servidor: el token JWT viaja en la cookie HttpOnly "ocumare_sesion"
 * (ver SesionCookie). También se acepta "Authorization: Bearer <token>" (pruebas y herramientas).
 *
 * Matriz de permisos (lo que no está aquí exige al menos estar autenticado):
 *  - Público:            login, menú (GET), envío de pedidos desde la web y las rutas de las impresoras
 *                        (/api/impresion/**, que se identifican con su propio token).
 *  - Todo el personal:   pedidos (tomar, agregar, enviar a cocina, reimprimir), ver mesas, tasa y turno, comandas.
 *  - CAJERO/GERENTE/ADMIN: cobrar, bitácora de pedidos, abrir y cerrar turno, ver el corte del turno.
 *  - GERENTE/ADMIN:      menú, mesas, tasa, historial de turnos y corrección del conteo, usuarios,
 *                        dashboard, impresoras.
 *  - ADMIN:              /api/admin/**.
 * Reglas finas (p. ej. "un gerente solo crea meseros y cajeros", "el mesero no acepta pedidos web")
 * se validan en el servicio.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(OcumareProperties.class)
public class SecurityConfig {

    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_USUARIO_ID = "uid";
    public static final String CLAIM_VERSION = "ver";
    static final String EMISOR = "ocumare-food";

    /**
     * Política de contenido: la página solo ejecuta scripts propios (nada en línea ni de terceros),
     * solo habla con este mismo servidor y no puede ser metida en un iframe de otro sitio.
     * Los estilos en línea y Google Fonts sí se permiten (no ejecutan código).
     */
    static final String CSP = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
            "font-src 'self' https://fonts.gstatic.com",
            "img-src 'self' data: blob:",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    private static final PathPatternRequestMatcher.Builder RUTAS = PathPatternRequestMatcher.withDefaults();

    /** Rutas de la API que no piden sesión. Aquí nunca se lee la cookie (una cookie vencida no las rompe). */
    static final RequestMatcher API_PUBLICA = new OrRequestMatcher(
            RUTAS.matcher(HttpMethod.POST, "/api/auth/login"),
            RUTAS.matcher(HttpMethod.POST, "/api/auth/salir"),
            RUTAS.matcher(HttpMethod.GET, "/api/menu/**"),
            RUTAS.matcher(HttpMethod.POST, "/api/web/pedidos"),
            RUTAS.matcher(HttpMethod.GET, "/api/web/pedidos/*"),
            RUTAS.matcher(HttpMethod.GET, "/api/imagenes/*"),
            RUTAS.matcher("/api/impresion/**"));

    private static boolean esApi(jakarta.servlet.http.HttpServletRequest req) {
        return req.getRequestURI().startsWith(req.getContextPath() + "/api/");
    }


    @Bean
    SecurityFilterChain filtros(HttpSecurity http, JwtAuthenticationConverter jwtConverter,
                                BearerTokenResolver tokenResolver, OcumareProperties props) throws Exception {
        RequestMatcher consolaH2 = RUTAS.matcher("/h2-console/**");
        http
            // CSRF: la cookie es SameSite=Strict, así que otro sitio no puede usarla para mandar peticiones.
            .csrf(csrf -> csrf.disable())
            .cors(cors -> {})
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(h -> h
                .frameOptions(f -> f.sameOrigin())   // la consola H2 de desarrollo usa frames propios
                .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(new NegatedRequestMatcher(consolaH2),
                        new StaticHeadersWriter("Content-Security-Policy", CSP)))
                .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
                        "camera=(), microphone=(), geolocation=(), payment=(), usb=()"))
                .addHeaderWriter(new StaticHeadersWriter("Cross-Origin-Opener-Policy", "same-origin")))
            .addFilterBefore(new LimitePeticiones(props), BearerTokenAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                // La app web (index.html, JS, CSS, logo) es pública: los datos se protegen en /api.
                .requestMatchers(req -> !esApi(req)).permitAll()
                // Público
                .requestMatchers(API_PUBLICA).permitAll()
                .requestMatchers("/api/imagenes/**").hasAnyRole("GERENTE", "ADMIN")
                .requestMatchers("/error").permitAll()
                // Solo administrador
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // Gerencia
                .requestMatchers("/api/usuarios/**", "/api/dashboard/**", "/api/impresoras/**").hasAnyRole("GERENTE", "ADMIN")
                // Caja (cajero y gerencia)
                .requestMatchers(HttpMethod.POST, "/api/pedidos/*/pagos").hasAnyRole("CAJERO", "GERENTE", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/pedidos/bitacora", "/api/pedidos/*/bitacora")
                        .hasAnyRole("CAJERO", "GERENTE", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/turnos/abrir", "/api/turnos/cerrar")
                        .hasAnyRole("CAJERO", "GERENTE", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/turnos/*/corte").hasAnyRole("CAJERO", "GERENTE", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/productos/**", "/api/categorias/**", "/api/mesas/**",
                                 "/api/tasas/vigente", "/api/turnos/actual").authenticated()
                .requestMatchers("/api/productos/**", "/api/categorias/**", "/api/mesas/**",
                                 "/api/tasas/**", "/api/turnos/**").hasAnyRole("GERENTE", "ADMIN")
                // Operación diaria
                .requestMatchers("/api/pedidos/**", "/api/comandas/**").hasAnyRole("MESERO", "CAJERO", "GERENTE", "ADMIN")
                .anyRequest().authenticated())
            .oauth2ResourceServer(o -> o
                .bearerTokenResolver(tokenResolver)
                .jwt(j -> j.jwtAuthenticationConverter(jwtConverter)));
        return http.build();
    }

    /** Toma el token del encabezado Authorization o, si no viene, de la cookie (solo en rutas con sesión). */
    @Bean
    BearerTokenResolver tokenResolver() {
        DefaultBearerTokenResolver encabezado = new DefaultBearerTokenResolver();
        return req -> {
            String token = encabezado.resolve(req);
            if (token != null) return token;
            if (!esApi(req) || API_PUBLICA.matches(req)) return null;
            return SesionCookie.leer(req);
        };
    }

    /** Lee el claim "roles" del token y lo convierte en ROLE_MESERO / ROLE_CAJERO / ROLE_GERENTE / ROLE_ADMIN. */
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
    JwtDecoder jwtDecoder(SecretKey clave, UsuarioRepository usuarios) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(clave).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(EMISOR), new VerificadorSesion(usuarios)));
        return decoder;
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
