package com.appcontrol.panel;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication(exclude = org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class)
public class PanelApplication {
    public static void main(String[] args) { SpringApplication.run(PanelApplication.class, args); }
}
