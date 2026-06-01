package me.serenityline.api.export;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DataExportProperties.class)
public class DataExportConfig {
}