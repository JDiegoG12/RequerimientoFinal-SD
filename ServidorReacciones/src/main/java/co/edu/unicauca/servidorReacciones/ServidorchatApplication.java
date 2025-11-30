package co.edu.unicauca.servidorReacciones;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class ServidorchatApplication {

	public static void main(String[] args) {
		SpringApplication.run(ServidorchatApplication.class, args);
	}

}
