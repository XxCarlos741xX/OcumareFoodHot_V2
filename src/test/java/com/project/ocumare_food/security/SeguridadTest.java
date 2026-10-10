package com.project.ocumare_food.security;

import com.jayway.jsonpath.JsonPath;
import com.project.ocumare_food.model.Usuario;
import com.project.ocumare_food.model.enums.Rol;
import com.project.ocumare_food.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pruebas de punta a punta: login real, token real y permisos por rol. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SeguridadTest {

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder encoder;

    private void crearEnBase(String username, Rol rol, boolean activo) {
        usuarios.save(Usuario.builder().nombre(username).username(username)
                .passwordHash(encoder.encode("clave-segura-1")).rol(rol).activo(activo).build());
    }

    private Cookie loginCookie(String username, String password) throws Exception {
        Cookie c = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("ocumare_sesion");
        assertThat(c).isNotNull();
        return c;
    }

    private String login(String username, String password) throws Exception {
        return "Bearer " + loginCookie(username, password).getValue();
    }

    private String nuevoUsuarioJson(String username, String rol) {
        return "{\"nombre\":\"Prueba\",\"username\":\"" + username + "\",\"password\":\"clave-segura-1\",\"rol\":\"" + rol + "\"}";
    }

    @Test
    void elAdminInicialPuedeEntrarYVerSusDatos() throws Exception {
        String token = login("admin", "admin123");
        mvc.perform(get("/api/auth/yo").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.rol").value("ADMIN"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void credencialesMalasDan401ConMensajeGenerico() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"no-existe\",\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Usuario o contraseña incorrectos"));
    }

    @Test
    void unUsuarioDesactivadoNoPuedeEntrar() throws Exception {
        crearEnBase("pedro-inactivo", Rol.MESERO, false);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"pedro-inactivo\",\"password\":\"clave-segura-1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sinTokenOTokenFalsoDa401() throws Exception {
        mvc.perform(get("/api/usuarios")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/usuarios").header("Authorization", "Bearer token.falso.xyz"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elMeseroNoPuedeGestionarUsuarios() throws Exception {
        crearEnBase("mesero1", Rol.MESERO, true);
        String token = login("mesero1", "clave-segura-1");
        mvc.perform(get("/api/usuarios").header("Authorization", token)).andExpect(status().isForbidden());
        mvc.perform(post("/api/usuarios").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(nuevoUsuarioJson("otro", "MESERO")))
                .andExpect(status().isForbidden());
    }

    @Test
    void elGerenteSoloCreaMeseros() throws Exception {
        crearEnBase("gerente1", Rol.GERENTE, true);
        String token = login("gerente1", "clave-segura-1");

        mvc.perform(post("/api/usuarios").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(nuevoUsuarioJson("mesero-nuevo", "MESERO")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("MESERO"));

        mvc.perform(post("/api/usuarios").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(nuevoUsuarioJson("gerente-nuevo", "GERENTE")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/usuarios").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(nuevoUsuarioJson("admin-nuevo", "ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void elAdminCreaGerentesYElNuevoUsuarioPuedeEntrar() throws Exception {
        String token = login("admin", "admin123");
        mvc.perform(post("/api/usuarios").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(nuevoUsuarioJson("Maria.G", "GERENTE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("maria.g"));
        login("maria.g", "clave-segura-1");

        // Usuario repetido → 409
        mvc.perform(post("/api/usuarios").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(nuevoUsuarioJson("maria.g", "MESERO")))
                .andExpect(status().isConflict());
    }

    @Test
    void validaLosDatosDelUsuarioNuevo() throws Exception {
        String token = login("admin", "admin123");
        mvc.perform(post("/api/usuarios").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\",\"username\":\"a b\",\"password\":\"123\",\"rol\":\"MESERO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nombre").exists())
                .andExpect(jsonPath("$.campos.username").exists())
                .andExpect(jsonPath("$.campos.password").exists());
    }

    @Test
    void bloqueaElLoginTrasCincoFallos() throws Exception {
        crearEnBase("victima", Rol.MESERO, true);
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"victima\",\"password\":\"mala\"}"))
                    .andExpect(status().isUnauthorized());
        }
        // Ni con la clave correcta entra mientras está bloqueado
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"victima\",\"password\":\"clave-segura-1\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void elMenuEsPublicoYLasRutasDeGerenciaNo() throws Exception {
        // El menú se ve sin iniciar sesión
        mvc.perform(get("/api/menu")).andExpect(status().isOk());   // público, sin token
        crearEnBase("mesero2", Rol.MESERO, true);
        String token = login("mesero2", "clave-segura-1");
        mvc.perform(post("/api/productos").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/dashboard/hoy").header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    // -------------------- Sesión en cookie --------------------

    @Test
    void elLoginEntregaUnaCookieHttpOnlyYNoDevuelveElToken() throws Exception {
        var r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.usuario.username").value("admin"))
                .andExpect(jsonPath("$.expiraEn").exists())
                .andReturn().getResponse();
        String setCookie = r.getHeader("Set-Cookie");
        assertThat(setCookie).contains("ocumare_sesion=").contains("HttpOnly").contains("SameSite=Strict").contains("Path=/api");
    }

    @Test
    void laCookieSirveSinEncabezadoYSalirLaBorra() throws Exception {
        Cookie c = loginCookie("admin", "admin123");
        mvc.perform(get("/api/auth/yo").cookie(c)).andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"));
        String borrar = mvc.perform(post("/api/auth/salir").cookie(c))
                .andExpect(status().isNoContent()).andReturn().getResponse().getHeader("Set-Cookie");
        assertThat(borrar).contains("ocumare_sesion=").contains("Max-Age=0");
    }

    @Test
    void unaCookieVencidaNoRompeLasRutasPublicas() throws Exception {
        Cookie basura = new Cookie("ocumare_sesion", "token.vencido.xyz");
        mvc.perform(get("/api/menu").cookie(basura)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").cookie(basura).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/yo").cookie(basura)).andExpect(status().isUnauthorized());
    }

    @Test
    void desactivarAUnMeseroLoSacaEnSuSiguientePeticion() throws Exception {
        crearEnBase("mesero-sale", Rol.MESERO, true);
        String tokenMesero = login("mesero-sale", "clave-segura-1");
        String tokenAdmin = login("admin", "admin123");
        long id = usuarios.findByUsername("mesero-sale").orElseThrow().getId();
        mvc.perform(get("/api/auth/yo").header("Authorization", tokenMesero)).andExpect(status().isOk());

        mvc.perform(put("/api/usuarios/" + id).header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/yo").header("Authorization", tokenMesero)).andExpect(status().isUnauthorized());
    }

    @Test
    void cambiarElRolORestablecerLaClaveCierraLasSesionesViejas() throws Exception {
        crearEnBase("gerente-baja", Rol.GERENTE, true);
        String tokenAdmin = login("admin", "admin123");
        long id = usuarios.findByUsername("gerente-baja").orElseThrow().getId();

        String viejo = login("gerente-baja", "clave-segura-1");
        mvc.perform(put("/api/usuarios/" + id).header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rol\":\"MESERO\"}"))
                .andExpect(status().isOk());
        // Con el token viejo ya no puede usar permisos de gerente (ni nada)
        mvc.perform(get("/api/usuarios").header("Authorization", viejo)).andExpect(status().isUnauthorized());

        String otro = login("gerente-baja", "clave-segura-1");
        mvc.perform(put("/api/usuarios/" + id + "/password").header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"passwordNueva\":\"clave-nueva-22\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/yo").header("Authorization", otro)).andExpect(status().isUnauthorized());
    }

    @Test
    void cambiarLaClavePropiaDaUnaSesionNuevaYAnulaLaVieja() throws Exception {
        crearEnBase("cambia-clave", Rol.MESERO, true);
        String viejo = login("cambia-clave", "clave-segura-1");
        Cookie nueva = mvc.perform(put("/api/auth/password").header("Authorization", viejo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passwordActual\":\"clave-segura-1\",\"passwordNueva\":\"clave-nueva-33\"}"))
                .andExpect(status().isNoContent()).andReturn().getResponse().getCookie("ocumare_sesion");
        assertThat(nueva).isNotNull();
        mvc.perform(get("/api/auth/yo").header("Authorization", viejo)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/yo").cookie(nueva)).andExpect(status().isOk());
    }

    @Test
    void elBloqueoPorIntentosEsPorIpYNoDejaFueraAlLocal() throws Exception {
        crearEnBase("cajera", Rol.MESERO, true);
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").with(r -> { r.setRemoteAddr("203.0.113.9"); return r; })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"cajera\",\"password\":\"mala\"}"))
                    .andExpect(status().isUnauthorized());
        }
        // El atacante queda bloqueado…
        mvc.perform(post("/api/auth/login").with(r -> { r.setRemoteAddr("203.0.113.9"); return r; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cajera\",\"password\":\"clave-segura-1\"}"))
                .andExpect(status().isTooManyRequests());
        // …pero la cajera entra normal desde el local
        mvc.perform(post("/api/auth/login").with(r -> { r.setRemoteAddr("198.51.100.20"); return r; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cajera\",\"password\":\"clave-segura-1\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void elLoginRechazaDatosGigantes() throws Exception {
        String largo = "x".repeat(200);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + largo + "\",\"password\":\"" + largo + "\"}"))
                .andExpect(status().isBadRequest());
    }

    // -------------------- Cabeceras de seguridad --------------------

    @Test
    void lasPaginasYLaApiLlevanCabecerasDeSeguridad() throws Exception {
        for (String url : new String[]{"/", "/pos/", "/api/menu"}) {
            mvc.perform(get(url))
                    .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("script-src 'self'")))
                    .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("frame-ancestors 'none'")))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                    .andExpect(header().exists("Permissions-Policy"));
        }
    }

    @Test
    void porHttpsSeExigeHttpsEnLasSiguientesVisitas() throws Exception {
        mvc.perform(get("/api/menu").secure(true))
                .andExpect(header().string("Strict-Transport-Security", org.hamcrest.Matchers.containsString("max-age=31536000")));
    }

    @Test
    void laPaginaPublicaNoTieneScriptsNiEventosEnLinea() throws Exception {
        String html = new String(getClass().getResourceAsStream("/static/index.html").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        // Con la CSP "script-src 'self'" cualquier script en línea dejaría la página sin funcionar.
        assertThat(html).doesNotContainPattern("<script>|<script(?![^>]*\\bsrc=)[^>]*>");
        assertThat(html).doesNotContainPattern("\\son[a-z]+\\s*=\\s*[\"']");
        assertThat(html).contains("<script src=\"js/web.js\"");
    }
}
