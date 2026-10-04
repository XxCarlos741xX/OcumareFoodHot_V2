package com.project.ocumare_food.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Rutas de las dos páginas que sirve el mismo servidor:
 *  - "/"     → web de clientes (static/index.html)
 *  - "/pos/" → sistema del local: mesas, caja y gestión (static/pos/index.html)
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/pos", "/pos/");
        registry.addViewController("/pos/").setViewName("forward:/pos/index.html");
    }
}
