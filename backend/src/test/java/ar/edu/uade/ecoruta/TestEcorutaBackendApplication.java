package ar.edu.uade.ecoruta;

import org.springframework.boot.SpringApplication;

public class TestEcorutaBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(EcorutaBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
