package com.project.ocumare_food.usuarios.seguridad;

import com.project.ocumare_food.usuarios.model.Usuario;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Emite el token JWT después de un login correcto. */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final int horas;

    public TokenService(JwtEncoder encoder, OcumareProperties props) {
        this.encoder = encoder;
        this.horas = props.jwt().horas() > 0 ? props.jwt().horas() : 12;
    }

    public record TokenEmitido(String token, Instant expiraEn) {
    }

    public TokenEmitido emitir(Usuario usuario) {
        Instant ahora = Instant.now();
        Instant expira = ahora.plus(horas, ChronoUnit.HOURS);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(SecurityConfig.EMISOR)
                .issuedAt(ahora)
                .expiresAt(expira)
                .subject(usuario.getUsername())
                .claim(SecurityConfig.CLAIM_USUARIO_ID, usuario.getId())
                .claim(SecurityConfig.CLAIM_ROLES, List.of(usuario.getRol().name()))
                .claim(SecurityConfig.CLAIM_VERSION, usuario.getVersionSesion())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenEmitido(token, expira);
    }
}
