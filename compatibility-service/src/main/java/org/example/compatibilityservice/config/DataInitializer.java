package org.example.compatibilityservice.config;

import org.example.compatibilityservice.model.CompatibilityRule;
import org.example.compatibilityservice.repository.CompatibilityRuleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {
    @Bean
    CommandLineRunner initData(CompatibilityRuleRepository repo) {
        return args -> {
            if (repo.count() == 0) {
                repo.save(new CompatibilityRule("CPU", "MOTHERBOARD", "socket", "Процессор и материнская плата должны иметь одинаковый сокет"));
                repo.save(new CompatibilityRule("MOTHERBOARD", "RAM", "ramType", "Материнская плата и ОЗУ должны поддерживать один тип памяти"));
                repo.save(new CompatibilityRule("MOTHERBOARD", "CASE", "formFactor", "Материнская плата должна подходить по форм-фактору к корпусу"));
            }
        };
    }
}