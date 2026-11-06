
package org.example.compatibilityservice.client;

import org.example.compatibilityservice.dto.ComponentDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "product-service")
public interface ProductClient {

    @GetMapping("/components/{id}")
    ComponentDto getComponentById(@PathVariable("id") Long id);
}
