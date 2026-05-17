package com.product.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "product.admin")
public class AdminSecurityProperties {

    private String username = "admin";
    private String password = "admin";
}
