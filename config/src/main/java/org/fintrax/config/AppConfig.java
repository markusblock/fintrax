package org.fintrax.config;

import lombok.Data;

@Data
public class AppConfig {
    private String theme = "light";
    private String language = "en";
    private String dataDirectory = "~/.fintrax/data";
    private boolean sidebarExpanded = true;
}
