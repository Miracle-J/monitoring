package com.baidu.monitoring.conf;

import org.springframework.stereotype.Component;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Component
@ConfigurationProperties(prefix = "local")
public class LocalProperties {

    private String path;
    //-PixelStreamingEncoderMinQP=1 -PixelStreamingEncoderMaxQP=1
    private String ueparam;
    private String ueparam2;

    public String getUeparam() {
        return ueparam;
    }

    public void setUeparam(String ueparam) {
        this.ueparam = ueparam;
    }

    public String getUeparam2() {
        return ueparam2;
    }

    public void setUeparam2(String ueparam2) {
        this.ueparam2 = ueparam2;
    }

    // getter 和 setter
    public String getPath() {
        return path;
    }
    public void setPath(String path) {
        this.path = path;
    }
}
