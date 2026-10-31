package com.project.ocumare_food;

import com.project.ocumare_food.compartido.RelojConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OcumareFoodApplication {

	public static void main(String[] args) {
		RelojConfig.usarZonaDelNegocio();
		SpringApplication.run(OcumareFoodApplication.class, args);
	}

}
