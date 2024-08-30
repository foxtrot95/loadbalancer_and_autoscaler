package pt.ulisboa.tecnico.cnv.webserver;

import java.net.InetSocketAddress;
import com.sun.net.httpserver.HttpServer;

import pt.ulisboa.tecnico.cnv.imageproc.BlurImageHandler;
import pt.ulisboa.tecnico.cnv.imageproc.EnhanceImageHandler;
import pt.ulisboa.tecnico.cnv.raytracer.RaytracerHandler;
import pt.ulisboa.tecnico.cnv.utils.*;


public class WebServer {

    public static void main(String[] args) throws Exception {

        MetricsWriter metricsWriter = new MetricsWriter(); 

        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.createContext("/", new RootHandler());
        server.createContext("/raytracer", new RaytracerHandler(metricsWriter));
        server.createContext("/blurimage", new BlurImageHandler(metricsWriter));
        server.createContext("/enhanceimage", new EnhanceImageHandler(metricsWriter));
        server.start();
    }
}
