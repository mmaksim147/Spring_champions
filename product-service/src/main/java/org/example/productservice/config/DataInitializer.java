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
import java.util.*;

@Configuration
public class DataInitializer {

    private static final Map<String, List<String>> PRODUCT_IMAGES = Map.ofEntries(

            Map.entry("Ryzen 5 7600X", List.of(
                    "https://placehold.co/900x600?text=AMD+Ryzen+5+7600X"
            )),
            Map.entry("Ryzen 7 7700X", List.of(
                    "https://placehold.co/900x600?text=AMD+Ryzen+7+7700X"
            )),
            Map.entry("Ryzen 7 7800X3D", List.of(
                    "https://placehold.co/900x600?text=AMD+Ryzen+7+7800X3D"
            )),
            Map.entry("Ryzen 9 7900X", List.of(
                    "https://placehold.co/900x600?text=AMD+Ryzen+9+7900X"
            )),
            Map.entry("Ryzen 5 5600", List.of(
                    "https://placehold.co/900x600?text=AMD+Ryzen+5+5600"
            )),
            Map.entry("Core i5-12400F", List.of(
                    "https://placehold.co/900x600?text=Intel+Core+i5-12400F"
            )),
            Map.entry("Core i5-13400F", List.of(
                    "https://placehold.co/900x600?text=Intel+Core+i5-13400F"
            )),
            Map.entry("Core i7-13700K", List.of(
                    "https://placehold.co/900x600?text=Intel+Core+i7-13700K"
            )),

            Map.entry("GeForce RTX 4060", List.of(
                    "https://placehold.co/900x600?text=NVIDIA+RTX+4060"
            )),
            Map.entry("GeForce RTX 4060 Ti", List.of(
                    "https://placehold.co/900x600?text=MSI+RTX+4060+Ti"
            )),
            Map.entry("GeForce RTX 4070 SUPER", List.of(
                    "https://placehold.co/900x600?text=ASUS+RTX+4070+SUPER"
            )),
            Map.entry("GeForce RTX 4080 SUPER", List.of(
                    "https://placehold.co/900x600?text=Gigabyte+RTX+4080+SUPER"
            )),
            Map.entry("Radeon RX 7600", List.of(
                    "https://placehold.co/900x600?text=AMD+Radeon+RX+7600"
            )),
            Map.entry("Radeon RX 7800 XT", List.of(
                    "https://placehold.co/900x600?text=Sapphire+RX+7800+XT"
            )),

            Map.entry("MSI B650 Gaming Plus WiFi", List.of(
                    "https://placehold.co/900x600?text=MSI+B650+Gaming+Plus+WiFi"
            )),
            Map.entry("ASUS TUF Gaming B650-PLUS", List.of(
                    "https://placehold.co/900x600?text=ASUS+TUF+B650-PLUS"
            )),
            Map.entry("Gigabyte B650M DS3H", List.of(
                    "https://placehold.co/900x600?text=Gigabyte+B650M+DS3H"
            )),
            Map.entry("ASUS PRIME B760M-K", List.of(
                    "https://placehold.co/900x600?text=ASUS+PRIME+B760M-K"
            )),
            Map.entry("MSI PRO B760-P", List.of(
                    "https://placehold.co/900x600?text=MSI+PRO+B760-P"
            )),
            Map.entry("Gigabyte Z790 AORUS ELITE", List.of(
                    "https://placehold.co/900x600?text=Gigabyte+Z790+AORUS+ELITE"
            )),

            Map.entry("Kingston Fury Beast 16GB DDR5", List.of(
                    "https://placehold.co/900x600?text=Kingston+Fury+16GB+DDR5"
            )),
            Map.entry("Kingston Fury Beast 32GB DDR5", List.of(
                    "https://placehold.co/900x600?text=Kingston+Fury+32GB+DDR5"
            )),
            Map.entry("Corsair Vengeance 32GB DDR5", List.of(
                    "https://placehold.co/900x600?text=Corsair+Vengeance+32GB+DDR5"
            )),
            Map.entry("G.Skill Trident Z5 32GB", List.of(
                    "https://placehold.co/900x600?text=G.Skill+Trident+Z5+32GB"
            )),
            Map.entry("Crucial 16GB DDR4", List.of(
                    "https://placehold.co/900x600?text=Crucial+16GB+DDR4"
            )),
            Map.entry("Kingston Fury Beast 32GB DDR4", List.of(
                    "https://placehold.co/900x600?text=Kingston+Fury+32GB+DDR4"
            )),

            Map.entry("Corsair RM750e 750W", List.of(
                    "https://placehold.co/900x600?text=Corsair+RM750e+750W"
            )),
            Map.entry("be quiet! Pure Power 12 M 850W", List.of(
                    "https://placehold.co/900x600?text=be+quiet+Pure+Power+850W"
            )),
            Map.entry("MSI MAG A650BN 650W", List.of(
                    "https://placehold.co/900x600?text=MSI+MAG+A650BN+650W"
            )),
            Map.entry("DeepCool PQ1000M 1000W", List.of(
                    "https://placehold.co/900x600?text=DeepCool+PQ1000M+1000W"
            )),

            Map.entry("DeepCool CC560", List.of(
                    "https://placehold.co/900x600?text=DeepCool+CC560"
            )),
            Map.entry("NZXT H5 Flow", List.of(
                    "https://placehold.co/900x600?text=NZXT+H5+Flow"
            )),
            Map.entry("Corsair 4000D Airflow", List.of(
                    "https://placehold.co/900x600?text=Corsair+4000D+Airflow"
            )),
            Map.entry("Cooler Master Q300L", List.of(
                    "https://placehold.co/900x600?text=Cooler+Master+Q300L"
            )),

            Map.entry("Samsung 980 PRO 1TB", List.of(
                    "https://placehold.co/900x600?text=Samsung+980+PRO+1TB"
            )),
            Map.entry("Samsung 990 EVO 2TB", List.of(
                    "https://placehold.co/900x600?text=Samsung+990+EVO+2TB"
            )),
            Map.entry("WD Black SN850X 1TB", List.of(
                    "https://placehold.co/900x600?text=WD+Black+SN850X+1TB"
            )),
            Map.entry("Kingston NV2 1TB", List.of(
                    "https://placehold.co/900x600?text=Kingston+NV2+1TB"
            )),

            Map.entry("DeepCool AK400", List.of(
                    "https://placehold.co/900x600?text=DeepCool+AK400"
            )),
            Map.entry("DeepCool AK620", List.of(
                    "https://placehold.co/900x600?text=DeepCool+AK620"
            )),
            Map.entry("Noctua NH-D15", List.of(
                    "https://placehold.co/900x600?text=Noctua+NH-D15"
            )),
            Map.entry("Arctic Liquid Freezer III 360", List.of(
                    "https://placehold.co/900x600?text=Arctic+Liquid+Freezer+III+360"
            ))
    );

    @Bean
    CommandLineRunner initData(
            CategoryRepository categoryRepo,
            ComponentRepository componentRepo,
            BuildRepository buildRepo
    ) {
        return args -> {

            List<String> categoryNames = List.of(
                    "CPU", "GPU", "MOTHERBOARD", "RAM",
                    "PSU", "CASE", "STORAGE", "COOLER"
            );

            Map<String, Category> categories = new HashMap<>();

            for (String name : categoryNames) {
                Category category = categoryRepo.findByName(name)
                        .orElseGet(() -> categoryRepo.save(new Category(name)));
                categories.put(name, category);
            }

            Set<String> existingNames = new HashSet<>();
            componentRepo.findAll().forEach(c ->
                    existingNames.add(c.getName().toLowerCase(Locale.ROOT))
            );

            int added = 0;

            // CPU
            added += add(componentRepo, categories, "Ryzen 5 7600X", "AMD", "25000", 10,
                    "CPU", "AM5", null, null, null, null,
                    "6 ядер, 12 потоков. Отличный игровой процессор.");
            added += add(componentRepo, categories, "Ryzen 7 7700X", "AMD", "32000", 8,
                    "CPU", "AM5", null, null, null, null,
                    "8 ядер, 16 потоков для игр и работы.");
            added += add(componentRepo, categories, "Ryzen 7 7800X3D", "AMD", "42000", 5,
                    "CPU", "AM5", null, null, null, null,
                    "Игровой процессор с технологией 3D V-Cache.");
            added += add(componentRepo, categories, "Ryzen 9 7900X", "AMD", "39000", 4,
                    "CPU", "AM5", null, null, null, null,
                    "12 ядер, 24 потока для производительных систем.");
            added += add(componentRepo, categories, "Ryzen 5 5600", "AMD", "10500", 12,
                    "CPU", "AM4", null, null, null, null,
                    "Доступный процессор для игровых сборок.");
            added += add(componentRepo, categories, "Core i5-12400F", "Intel", "12500", 10,
                    "CPU", "LGA1700", null, null, null, null,
                    "6 ядер, 12 потоков.");
            added += add(componentRepo, categories, "Core i5-13400F", "Intel", "18000", 15,
                    "CPU", "LGA1700", null, null, null, null,
                    "10 ядер для универсального компьютера.");
            added += add(componentRepo, categories, "Core i7-13700K", "Intel", "35000", 5,
                    "CPU", "LGA1700", null, null, null, null,
                    "Высокая производительность для игр и работы.");

            // GPU
            added += add(componentRepo, categories, "GeForce RTX 4060", "NVIDIA", "32000", 10,
                    "GPU", null, null, null, null, "8GB",
                    "Видеокарта для современных игр в Full HD.");
            added += add(componentRepo, categories, "GeForce RTX 4060 Ti", "MSI", "43000", 7,
                    "GPU", null, null, null, null, "8GB",
                    "Производительная видеокарта с поддержкой DLSS.");
            added += add(componentRepo, categories, "GeForce RTX 4070 SUPER", "ASUS", "65000", 5,
                    "GPU", null, null, null, null, "12GB",
                    "Видеокарта для игр в высоком разрешении.");
            added += add(componentRepo, categories, "GeForce RTX 4080 SUPER", "Gigabyte", "105000", 3,
                    "GPU", null, null, null, null, "16GB",
                    "Мощная видеокарта для 4K.");
            added += add(componentRepo, categories, "Radeon RX 7600", "AMD", "27000", 8,
                    "GPU", null, null, null, null, "8GB",
                    "Доступная видеокарта для Full HD.");
            added += add(componentRepo, categories, "Radeon RX 7800 XT", "Sapphire", "52000", 5,
                    "GPU", null, null, null, null, "16GB",
                    "Видеокарта для комфортного гейминга в 1440p.");

            // MOTHERBOARD
            added += add(componentRepo, categories, "MSI B650 Gaming Plus WiFi", "MSI", "15000", 8,
                    "MOTHERBOARD", "AM5", "DDR5", "ATX", null, null,
                    "Материнская плата AM5 с поддержкой DDR5.");
            added += add(componentRepo, categories, "ASUS TUF Gaming B650-PLUS", "ASUS", "19000", 6,
                    "MOTHERBOARD", "AM5", "DDR5", "ATX", null, null,
                    "Надёжная игровая материнская плата.");
            added += add(componentRepo, categories, "Gigabyte B650M DS3H", "Gigabyte", "12500", 9,
                    "MOTHERBOARD", "AM5", "DDR5", "mATX", null, null,
                    "Компактная плата для процессоров AMD.");
            added += add(componentRepo, categories, "ASUS PRIME B760M-K", "ASUS", "10500", 10,
                    "MOTHERBOARD", "LGA1700", "DDR4", "mATX", null, null,
                    "Материнская плата Intel с поддержкой DDR4.");
            added += add(componentRepo, categories, "MSI PRO B760-P", "MSI", "14500", 7,
                    "MOTHERBOARD", "LGA1700", "DDR5", "ATX", null, null,
                    "Плата для современных процессоров Intel.");
            added += add(componentRepo, categories, "Gigabyte Z790 AORUS ELITE", "Gigabyte", "28000", 4,
                    "MOTHERBOARD", "LGA1700", "DDR5", "ATX", null, null,
                    "Производительная плата на чипсете Z790.");

            // RAM
            added += add(componentRepo, categories, "Kingston Fury Beast 16GB DDR5", "Kingston", "6000", 20,
                    "RAM", null, "DDR5", null, null, "16GB",
                    "Оперативная память DDR5.");
            added += add(componentRepo, categories, "Kingston Fury Beast 32GB DDR5", "Kingston", "11000", 15,
                    "RAM", null, "DDR5", null, null, "32GB",
                    "Комплект памяти для современных ПК.");
            added += add(componentRepo, categories, "Corsair Vengeance 32GB DDR5", "Corsair", "12500", 10,
                    "RAM", null, "DDR5", null, null, "32GB",
                    "Высокопроизводительная оперативная память.");
            added += add(componentRepo, categories, "G.Skill Trident Z5 32GB", "G.Skill", "14500", 6,
                    "RAM", null, "DDR5", null, null, "32GB",
                    "Память DDR5 для игровых систем.");
            added += add(componentRepo, categories, "Crucial 16GB DDR4", "Crucial", "4000", 25,
                    "RAM", null, "DDR4", null, null, "16GB",
                    "Доступная оперативная память DDR4.");
            added += add(componentRepo, categories, "Kingston Fury Beast 32GB DDR4", "Kingston", "7500", 12,
                    "RAM", null, "DDR4", null, null, "32GB",
                    "Комплект оперативной памяти DDR4.");

            // PSU
            added += add(componentRepo, categories, "Corsair RM750e 750W", "Corsair", "10500", 8,
                    "PSU", null, null, null, 750, null,
                    "Блок питания мощностью 750 Вт.");
            added += add(componentRepo, categories, "be quiet! Pure Power 12 M 850W", "be quiet!", "14500", 5,
                    "PSU", null, null, null, 850, null,
                    "Модульный блок питания 850 Вт.");
            added += add(componentRepo, categories, "MSI MAG A650BN 650W", "MSI", "5500", 12,
                    "PSU", null, null, null, 650, null,
                    "Блок питания для игровых сборок.");
            added += add(componentRepo, categories, "DeepCool PQ1000M 1000W", "DeepCool", "15500", 4,
                    "PSU", null, null, null, 1000, null,
                    "Мощный блок питания 1000 Вт.");

            // CASE
            added += add(componentRepo, categories, "DeepCool CC560", "DeepCool", "5500", 10,
                    "CASE", null, null, "ATX", null, null,
                    "Корпус с хорошей вентиляцией.");
            added += add(componentRepo, categories, "NZXT H5 Flow", "NZXT", "9500", 6,
                    "CASE", null, null, "ATX", null, null,
                    "Корпус с сетчатой передней панелью.");
            added += add(componentRepo, categories, "Corsair 4000D Airflow", "Corsair", "11000", 5,
                    "CASE", null, null, "ATX", null, null,
                    "Популярный корпус с отличным охлаждением.");
            added += add(componentRepo, categories, "Cooler Master Q300L", "Cooler Master", "5000", 8,
                    "CASE", null, null, "mATX", null, null,
                    "Компактный корпус формата Micro-ATX.");

            // STORAGE
            added += add(componentRepo, categories, "Samsung 980 PRO 1TB", "Samsung", "9500", 10,
                    "STORAGE", null, null, null, null, "1TB",
                    "Быстрый NVMe SSD PCIe 4.0.");
            added += add(componentRepo, categories, "Samsung 990 EVO 2TB", "Samsung", "15500", 6,
                    "STORAGE", null, null, null, null, "2TB",
                    "SSD большого объёма для игр и работы.");
            added += add(componentRepo, categories, "WD Black SN850X 1TB", "Western Digital", "10500", 7,
                    "STORAGE", null, null, null, null, "1TB",
                    "Высокоскоростной NVMe SSD.");
            added += add(componentRepo, categories, "Kingston NV2 1TB", "Kingston", "5500", 15,
                    "STORAGE", null, null, null, null, "1TB",
                    "Доступный SSD для повседневных задач.");

            // COOLER
            added += add(componentRepo, categories, "DeepCool AK400", "DeepCool", "2800", 12,
                    "COOLER", null, null, null, null, null,
                    "Башенный кулер с 120-мм вентилятором.");
            added += add(componentRepo, categories, "DeepCool AK620", "DeepCool", "5500", 8,
                    "COOLER", null, null, null, null, null,
                    "Двухбашенный кулер для мощных процессоров.");
            added += add(componentRepo, categories, "Noctua NH-D15", "Noctua", "11500", 4,
                    "COOLER", null, null, null, null, null,
                    "Высокопроизводительное воздушное охлаждение.");
            added += add(componentRepo, categories, "Arctic Liquid Freezer III 360", "Arctic", "11500", 5,
                    "COOLER", null, null, null, null, null,
                    "Жидкостная система охлаждения 360 мм.");

            System.out.println(">>> Product-service: added " + added +
                    " new components. Total: " + componentRepo.count());

            if (buildRepo.count() == 0 && componentRepo.count() > 0) {
                createDemoBuild(componentRepo, buildRepo);
            }
        };
    }

    private int add(
            ComponentRepository repo,
            Map<String, Category> categories,
            String name,
            String manufacturer,
            String price,
            int stock,
            String categoryName,
            String socket,
            String ramType,
            String formFactor,
            Integer wattage,
            String capacity,
            String description
    ) {
        if (repo.findAll().stream().anyMatch(c -> c.getName().equalsIgnoreCase(name))) {
            return 0;
        }

        Component c = new Component();
        c.setName(name);
        c.setManufacturer(manufacturer);
        c.setPrice(new BigDecimal(price));
        c.setStockQuantity(stock);
        c.setDescription(description);
        c.setCategory(categories.get(categoryName));
        c.setSocket(socket);
        c.setRamType(ramType);
        c.setFormFactor(formFactor);
        c.setWattage(wattage);
        c.setCapacity(capacity);

        List<String> urls = PRODUCT_IMAGES.get(name);
        if (urls == null || urls.isEmpty()) {
            urls = List.of("https://placehold.co/900x600?text=" +
                    name.replace(" ", "+").replace("!", "%21"));
        }
        c.setImageUrls(new ArrayList<>(urls));

        repo.save(c);
        return 1;
    }

    private void createDemoBuild(ComponentRepository componentRepo, BuildRepository buildRepo) {
        Build build = new Build();
        build.setUserId(1L);
        build.setName("Игровой ПК (demo)");

        Component cpu = find(componentRepo, "Ryzen 5 7600X");
        Component mb = find(componentRepo, "MSI B650 Gaming Plus WiFi");
        Component ram = find(componentRepo, "Kingston Fury Beast 16GB DDR5");

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

        System.out.println(">>> Demo build created: " + total + " RUB");
    }

    private Component find(ComponentRepository repo, String name) {
        return repo.findAll().stream()
                .filter(c -> c.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }
}