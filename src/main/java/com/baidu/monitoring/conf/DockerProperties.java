package com.baidu.monitoring.conf;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "docker")
public class DockerProperties {
    private String containerName;
    private String imageName;
    private String ueVolumeBase;
    private String configMount;
    private String ueImageName;

    public String getContainerName() {
        return containerName;
    }

    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    public String getImageName() {
        return imageName;
    }

    public void setImageName(String imageName) {
        this.imageName = imageName;
    }

    public String getUeVolumeBase() {
        return ueVolumeBase;
    }

    public void setUeVolumeBase(String ueVolumeBase) {
        this.ueVolumeBase = ueVolumeBase;
    }

    public String getConfigMount() {
        return configMount;
    }

    public void setConfigMount(String configMount) {
        this.configMount = configMount;
    }

    public String getUeImageName() {
        return ueImageName;
    }

    public void setUeImageName(String ueImageName) {
        this.ueImageName = ueImageName;
    }
}
