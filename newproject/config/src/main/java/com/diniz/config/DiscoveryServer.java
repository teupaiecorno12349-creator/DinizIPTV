package com.diniz.config;
import java.net.*;import java.nio.charset.StandardCharsets;
public final class DiscoveryServer implements Runnable{
 private volatile boolean running=true; private final ConfigServerService s;
 public DiscoveryServer(ConfigServerService s){this.s=s;}
 public void stop(){running=false;}
 public void run(){try(DatagramSocket sock=new DatagramSocket(45454,InetAddress.getByName("0.0.0.0"))){sock.setBroadcast(true);byte[] buf=new byte[256];while(running){DatagramPacket p=new DatagramPacket(buf,buf.length);sock.receive(p);String q=new String(p.getData(),p.getOffset(),p.getLength(),StandardCharsets.UTF_8);if("DINIZ_DISCOVER".equals(q)){String r="DINIZ_CONFIG|"+s.getPort()+"|"+s.getToken()+"|Diniz Config Server";byte[] out=r.getBytes(StandardCharsets.UTF_8);sock.send(new DatagramPacket(out,out.length,p.getAddress(),p.getPort()));}}}catch(Exception ignored){}}
}
