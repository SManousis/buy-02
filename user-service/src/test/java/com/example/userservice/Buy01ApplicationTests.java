package com.example.userservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.example.userservice.client.MediaOwnershipClient;
import com.example.userservice.repository.UserRepository;
import com.example.userservice.security.JwtService;
import com.example.userservice.service.UserService;

class Buy01ApplicationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(UserRepository.class, () -> mock(UserRepository.class))
            .withBean(org.springframework.security.crypto.password.PasswordEncoder.class,
                    () -> mock(org.springframework.security.crypto.password.PasswordEncoder.class))
            .withBean(JwtService.class, () -> mock(JwtService.class))
            .withBean(MediaOwnershipClient.class, () -> mock(MediaOwnershipClient.class))
            .withUserConfiguration(UserService.class);

    @Test
    void userDomainContextLoadsWithoutExternalInfrastructure() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(UserService.class);
        });
    }

}
