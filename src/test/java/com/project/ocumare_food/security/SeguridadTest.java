package com.project.ocumare_food.security;

import com.jayway.jsonpath.JsonPath;
import com.project.ocumare_food.model.Usuario;
import com.project.ocumare_food.model.enums.Rol;
import com.project.ocumare_food.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    private String login(String username, String password) throws Exception {
        String json = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(json, "$.token");
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
}
