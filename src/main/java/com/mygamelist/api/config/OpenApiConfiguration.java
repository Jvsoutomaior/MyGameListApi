package com.mygamelist.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

  @Bean
  OpenAPI myGameListOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("MyGameList API")
                .description("API para catálogo compartilhado, perfis e listas pessoais de jogos")
                .version("v1"));
  }
}
