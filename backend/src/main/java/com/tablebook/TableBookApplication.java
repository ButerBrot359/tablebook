package com.tablebook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {
		org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class
})public class TableBookApplication {

	public static void main(String[] args) {
		SpringApplication.run(TableBookApplication.class, args);
	}

}
