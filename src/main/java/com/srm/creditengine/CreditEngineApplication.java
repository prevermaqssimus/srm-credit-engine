package com.srm.creditengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Classe principal da aplicação Spring Boot.
 *
 * @ConfigurationPropertiesScan: habilita a descoberta automática de classes
 * anotadas com @ConfigurationProperties em todo o pacote base
 * (com.srm.creditengine e subpacotes) -- é isso que faz o PricingProperties
 * (pacote config/) ser registrado como bean e ficar disponível pra injeção
 * via construtor em qualquer @Service, sem precisar de @Component na
 * própria classe de properties.
 *
 * Nota: esta classe não fazia parte da numeração do plano de 15 passos,
 * mas precisa existir a partir do Passo 2 -- é ela que "liga" o
 * @ConfigurationPropertiesScan que o PricingProperties depende para
 * funcionar. Sem essa classe, "mvn package"/"mvn spring-boot:run" falham
 * com "Unable to find main class".
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CreditEngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(CreditEngineApplication.class, args);
    }
}
