package com.project.ocumare_food.flujo;

import com.jayway.jsonpath.JsonPath;
import com.project.ocumare_food.model.Usuario;
import com.project.ocumare_food.model.enums.Rol;
import com.project.ocumare_food.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Atajos para las pruebas de punta a punta: login, llamadas con token y lectura de JSON. */
abstract class ApiTest {

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder encoder;

    String tokenAdmin;

    String login(String username, String password) throws Exception {
        var cookie = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("ocumare_sesion");
        return "Bearer " + cookie.getValue();
    }

    String crearYEntrar(String username, Rol rol) throws Exception {
        usuarios.save(Usuario.builder().nombre(username).username(username)
                .passwordHash(encoder.encode("clave-segura-1")).rol(rol).build());
        return login(username, "clave-segura-1");
    }

    ResultActions get(String token, String url) throws Exception {
        var req = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url);
        if (token != null) req.header("Authorization", token);
        return mvc.perform(req);
    }

    ResultActions post(String token, String url, String json) throws Exception {
        var req = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON).content(json == null ? "{}" : json);
        if (token != null) req.header("Authorization", token);
        return mvc.perform(req);
    }

    ResultActions put(String token, String url, String json) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url)
                .header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    ResultActions patch(String token, String url, String json) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(url)
                .header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    ResultActions delete(String token, String url) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(url)
                .header("Authorization", token));
    }

    static <T> T leer(ResultActions r, String ruta) throws Exception {
        return JsonPath.read(r.andReturn().getResponse().getContentAsString(), ruta);
    }

    static long id(ResultActions r) throws Exception {
        return ((Number) leer(r, "$.id")).longValue();
    }
}
