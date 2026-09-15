package com.srm.creditengine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Habilita CORS para o frontend Angular (localhost:4200) consumir a API
 * (localhost:8080) -- sem isso, o navegador bloqueia as requisições com
 * erro "blocked by CORS policy", mesmo com a API funcionando normalmente
 * (confirmável via curl/Postman, que não aplicam a mesma política).
 *
 * Escopo deliberadamente permissivo (localhost, qualquer porta comum de
 * dev) -- adequado para desenvolvimento local. Em produção, restringir
 * allowedOrigins ao domínio real do frontend implantado.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:4200")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
