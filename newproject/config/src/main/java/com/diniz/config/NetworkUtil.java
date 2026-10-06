package com.diniz.config;
import java.net.*;import java.util.*;
public final class NetworkUtil{public static String ip(){try{for(NetworkInterface n:Collections.list(NetworkInterface.getNetworkInterfaces())){if(!n.isUp()||n.isLoopback())continue;for(InetAddress a:Collections.list(n.getInetAddresses()))if(a instanceof Inet4Address&&!a.isLoopbackAddress())return a.getHostAddress();}}catch(Exception ignored){}return "0.0.0.0";}}
