package com.baidu.monitoring.util;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public class IPUtil {

    public static String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface ni = networkInterfaces.nextElement();
                if (ni.isLoopback() || ni.isVirtual() || !ni.isUp()) {
                    continue; // 跳过本地回环、虚拟网卡、关闭的接口
                }

                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress()
                            && addr instanceof java.net.Inet4Address
                            && !addr.getHostAddress().startsWith("127")) {
                        return addr.getHostAddress(); // 返回内网 IPv4 地址
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "127.0.0.1"; // 默认返回回环地址
    }

    public static void main(String[] args) {
        System.out.println("本机内网 IP: " + getLocalIpAddress());
    }
}
