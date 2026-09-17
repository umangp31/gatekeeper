package com.gatekeeper.security;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Loads the RSA keypair used to sign/verify access tokens. `gatekeeper.jwt.private-key` and
 * `-public-key` point at either a classpath/file resource (local profile) or hold the raw PEM
 * directly (prod profile, from JWT_PRIVATE_KEY/JWT_PUBLIC_KEY env vars). See doc/scope.md §3, §9.
 */
@Configuration
public class JwtConfig {

    private final ResourceLoader resourceLoader = new DefaultResourceLoader();

    @Value("${gatekeeper.jwt.private-key}")
    private String privateKeyLocation;

    @Value("${gatekeeper.jwt.public-key}")
    private String publicKeyLocation;

    @Bean
    public RSAPrivateKey jwtPrivateKey() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] der = readPemDer(privateKeyLocation);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    @Bean
    public RSAPublicKey jwtPublicKey() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] der = readPemDer(publicKeyLocation);
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    @Bean
    public JwtEncoder jwtEncoder(RSAPublicKey publicKey, RSAPrivateKey privateKey) {
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(UUID.randomUUID().toString())
                .algorithm(com.nimbusds.jose.JWSAlgorithm.RS256)
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new com.nimbusds.jose.jwk.JWKSet(java.util.List.<JWK>of(rsaKey))));
    }

    @Bean
    public JwtDecoder jwtDecoder(RSAPublicKey publicKey) {
        return NimbusJwtDecoder.withPublicKey(publicKey).signatureAlgorithm(SignatureAlgorithm.RS256).build();
    }

    private byte[] readPemDer(String location) throws IOException {
        String pem = readRaw(location);
        String cleaned = pem
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(cleaned);
    }

    private String readRaw(String location) throws IOException {
        if (location.contains("BEGIN")) {
            return location;
        }
        Resource resource = resourceLoader.getResource(location);
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes());
        }
    }
}
