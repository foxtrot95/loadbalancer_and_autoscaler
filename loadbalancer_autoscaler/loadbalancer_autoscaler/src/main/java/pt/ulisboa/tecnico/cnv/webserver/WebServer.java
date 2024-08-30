package pt.ulisboa.tecnico.cnv.webserver;

import java.net.InetSocketAddress;
import com.sun.net.httpserver.HttpServer;

import pt.ulisboa.tecnico.cnv.loadbalancer.*;

import java.io.IOException;


public class WebServer {
    public static final int PORT = 8000;
    public static void main(String[] args) throws Exception {
        LoadBalancer LoadBalancer = new LoadBalancer(); 
        
        HttpServer server; 

        try {
            server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        } catch (IOException e) {
            System.err.println("Webserver cannot start.");
            return;
        }

        server.createContext("/", LoadBalancer);
        server.createContext("/raytracer", LoadBalancer);
        server.createContext("/blurimage", LoadBalancer);
        server.createContext("/enhanceimage", LoadBalancer);
        server.start();
        System.out.println("Load Balancer is running on port:" + PORT); 
    }
}
