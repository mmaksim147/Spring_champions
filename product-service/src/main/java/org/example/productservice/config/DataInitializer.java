package org.example.productservice.config;

import org.example.productservice.model.Build;
import org.example.productservice.model.BuildItem;
import org.example.productservice.model.Category;
import org.example.productservice.model.Component;
import org.example.productservice.repository.BuildRepository;
import org.example.productservice.repository.CategoryRepository;
import org.example.productservice.repository.ComponentRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initData(CategoryRepository categoryRepo,
                               ComponentRepository componentRepo,
                               BuildRepository buildRepo) {
        return args -> {
            if (categoryRepo.count() == 0) {
                List<String> names = List.of(
                        "CPU", "GPU", "MOTHERBOARD", "RAM",
                        "PSU", "CASE", "STORAGE", "COOLER"
                );
                names.forEach(n -> categoryRepo.save(new Category(n)));
                System.out.println(">>> Product-service: 8 categories created");
            }

            if (componentRepo.count() == 0) {
                Category cpu = categoryRepo.findByName("CPU").orElseThrow();
                Category mb = categoryRepo.findByName("MOTHERBOARD").orElseThrow();
                Category ram = categoryRepo.findByName("RAM").orElseThrow();

                Component c1 = new Component();
                c1.setName("Ryzen 5 7600X");
                c1.setManufacturer("AMD");
                c1.setPrice(new BigDecimal("25000"));
                c1.setStockQuantity(10);
                c1.setCategory(cpu);
                c1.setSocket("AM5");
                componentRepo.save(c1);

                Component c2 = new Component();
                c2.setName("Core i5-13400F");
                c2.setManufacturer("Intel");
                c2.setPrice(new BigDecimal("18000"));
                c2.setStockQuantity(15);
                c2.setCategory(cpu);
                c2.setSocket("LGA1700");
                componentRepo.save(c2);

                Component m1 = new Component();
                m1.setName("MSI B650 Gaming Plus");
                m1.setManufacturer("MSI");
                m1.setPrice(new BigDecimal("15000"));
                m1.setStockQuantity(8);
                m1.setCategory(mb);
                m1.setSocket("AM5");
                m1.setRamType("DDR5");
                m1.setFormFactor("ATX");
                componentRepo.save(m1);

                Component m2 = new Component();
                m2.setName("ASUS B760M");
                m2.setManufacturer("ASUS");
                m2.setPrice(new BigDecimal("12000"));
                m2.setStockQuantity(10);
                m2.setCategory(mb);
                m2.setSocket("LGA1700");
                m2.setRamType("DDR4");
                m2.setFormFactor("mATX");
                componentRepo.save(m2);

                Component r1 = new Component();
                r1.setName("Kingston Fury 16GB DDR5");
                r1.setManufacturer("Kingston");
                r1.setPrice(new BigDecimal("6000"));
                r1.setStockQuantity(20);
                r1.setCategory(ram);
                r1.setRamType("DDR5");
                r1.setCapacity("16GB");
                componentRepo.save(r1);

                Component r2 = new Component();
                r2.setName("Crucial 16GB DDR4");
                r2.setManufacturer("Crucial");
                r2.setPrice(new BigDecimal("4000"));
                r2.setStockQuantity(25);
                r2.setCategory(ram);
                r2.setRamType("DDR4");
                r2.setCapacity("16GB");
                componentRepo.save(r2);

                System.out.println(">>> Product-service: 6 test components created");
            }

            if (buildRepo.count() == 0 && componentRepo.count() > 0) {
                Build build = new Build();
                build.setUserId(1L);
                build.setName("Игровой ПК (demo)");

                Component cpu = componentRepo.findByNameContainingIgnoreCase("Ryzen").stream().findFirst().orElse(null);
                Component mb = componentRepo.findByNameContainingIgnoreCase("MSI").stream().findFirst().orElse(null);
                Component ram = componentRepo.findByNameContainingIgnoreCase("Kingston").stream().findFirst().orElse(null);

                List<BuildItem> items = new ArrayList<>();
                BigDecimal total = BigDecimal.ZERO;

                if (cpu != null) {
                    items.add(new BuildItem(build, cpu, 1));
                    total = total.add(cpu.getPrice());
                }
                if (mb != null) {
                    items.add(new BuildItem(build, mb, 1));
                    total = total.add(mb.getPrice());
                }
                if (ram != null) {
                    items.add(new BuildItem(build, ram, 2));
                    total = total.add(ram.getPrice().multiply(BigDecimal.valueOf(2)));
                }

                build.setItems(items);
                build.setTotalPrice(total);
                buildRepo.save(build);
                System.out.println(">>> Product-service: demo build created with totalPrice=" + total);
            }
        };
    }
}