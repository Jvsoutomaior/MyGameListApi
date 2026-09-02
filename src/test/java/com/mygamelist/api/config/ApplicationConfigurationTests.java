package com.mygamelist.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.springframework.security.crypto.password.PasswordEncoder;

class ApplicationConfigurationTests {

    @Test
    void configuresArgon2idWithAnIndividualSalt() {
        PasswordEncoder passwordEncoder = new ApplicationConfiguration().passwordEncoder();

        String firstHash = passwordEncoder.encode("a secure password");
        String secondHash = passwordEncoder.encode("a secure password");

        assertThat(firstHash).startsWith("$argon2id$");
        assertThat(passwordEncoder.matches("a secure password", firstHash)).isTrue();
        assertThat(passwordEncoder.matches("wrong password", firstHash)).isFalse();
        assertThat(secondHash).isNotEqualTo(firstHash);
    }
}
