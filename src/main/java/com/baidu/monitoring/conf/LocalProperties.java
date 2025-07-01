package com.baidu.monitoring.conf;

import org.springframework.stereotype.Component;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Component
@ConfigurationProperties(prefix = "local")
public class LocalProperties {

    private String path;

    // getter 和 setter
    public String getPath() {
        return path;
    }
    public void setPath(String path) {
        this.path = path;
    }
}
