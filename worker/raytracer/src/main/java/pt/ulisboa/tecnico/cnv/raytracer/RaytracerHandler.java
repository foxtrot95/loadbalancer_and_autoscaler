package pt.ulisboa.tecnico.cnv.raytracer;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

import pt.ulisboa.tecnico.cnv.utils.MetricsWriter;
import pt.ulisboa.tecnico.cnv.utils.Metrics;
import pt.ulisboa.tecnico.cnv.javassist.tools.ICount;


public class RaytracerHandler implements HttpHandler, RequestHandler<Map<String, String>, String> {

    private final static ObjectMapper mapper = new ObjectMapper();

    private MetricsWriter metricsWriter;

    public RaytracerHandler() {
        metricsWriter = null;
    }

    public RaytracerHandler(MetricsWriter metricsWriter) {
        this.metricsWriter = metricsWriter;
    }

    @Override
    public void handle(HttpExchange he) throws IOException {
        // Handling CORS
        he.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

        if (he.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            he.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
            he.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,Authorization");
            he.sendResponseHeaders(204, -1);
            return;
        }

        // Parse request
        URI requestedUri = he.getRequestURI();
        String query = requestedUri.getRawQuery();
        Map<String, String> parameters = queryToMap(query);

        int scols = Integer.parseInt(parameters.get("scols"));
        int srows = Integer.parseInt(parameters.get("srows"));
        int wcols = Integer.parseInt(parameters.get("wcols"));
        int wrows = Integer.parseInt(parameters.get("wrows"));
        int coff = Integer.parseInt(parameters.get("coff"));
        int roff = Integer.parseInt(parameters.get("roff"));
        Main.ANTI_ALIAS = Boolean.parseBoolean(parameters.getOrDefault("aa", "false"));
        Main.MULTI_THREAD = Boolean.parseBoolean(parameters.getOrDefault("multi", "false"));

        InputStream stream = he.getRequestBody();
        Map<String, Object> body = mapper.readValue(stream, new TypeReference<>() {});

        byte[] input = ((String) body.get("scene")).getBytes();
        byte[] texmap = null;
        if (body.containsKey("texmap")) {
            // Convert ArrayList<Integer> to byte[]
            ArrayList<Integer> texmapBytes = (ArrayList<Integer>) body.get("texmap");
            texmap = new byte[texmapBytes.size()];
            for (int i = 0; i < texmapBytes.size(); i++) {
                texmap[i] = texmapBytes.get(i).byteValue();
            }
        }

        String inputId = ""; 
        try{
            inputId = hashString(input);
        } catch (NoSuchAlgorithmException e) {
            System.err.println("No such hashing algorithm.");
        }

        String texmapId = ""; 
        if (texmap != null){
            try {
                texmapId = hashString(texmap);
            } catch (NoSuchAlgorithmException e) {
                System.err.println("No such hashing algorithm.");
            }
        }

        String patternId = ""; 
        try {
            patternId = hashString(getParameteString(parameters).getBytes());
        } catch (NoSuchAlgorithmException e) {
            System.err.println("No such hashing algorithm.");
        }

        String requestId = inputId + patternId + texmapId; 
        System.out.println("RequestID: " + requestId);
        
        System.out.println("Start");
        ICount.resetStatistics();
        byte[] result = handleRequest(input, texmap, scols, srows, wcols, wrows, coff, roff);
        String response = String.format("data:image/bmp;base64,%s", Base64.getEncoder().encodeToString(result));
        Long[] statistics = ICount.getStatistics();
        System.out.println(scols);
        System.out.println(srows);
        System.out.println(wcols);
        System.out.println(wrows);
        System.out.println(coff);
        System.out.println(roff);
        System.out.println(Main.ANTI_ALIAS);
        System.out.println(String.format("[ICount] Number of executed methods: %s", statistics[2]));
        System.out.println(String.format("[ICount] Number of executed basic blocks: %s", statistics[1]));
        System.out.println(String.format("[ICount] Number of executed instructions: %s", statistics[0]));
        System.out.println("End");

        // TODO 
        // Compute complexity
        String type = new String("raytracer");
        System.out.println("Type: " + type);
        System.out.println("Request identifier: " + requestId);
        Double complexity = computeComplexity(statistics[2], statistics[1], statistics[0]);

        if (metricsWriter != null) {
            Metrics metrics = new Metrics(requestId, Instant.now().toString(), type, complexity);
            metricsWriter.synchronizedMetricsList.add(metrics);
        }
        
        he.sendResponseHeaders(200, response.length());
        OutputStream os = he.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }

    public Map<String, String> queryToMap(String query) {
        if (query == null) {
            return null;
        }
        Map<String, String> result = new HashMap<>();
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                result.put(entry[0], entry[1]);
            } else {
                result.put(entry[0], "");
            }
        }
        return result;
    }

    private byte[] handleRequest(byte[] input, byte[] texmap, int scols, int srows, int wcols, int wrows, int coff, int roff) {
        try {
            RayTracer rayTracer = new RayTracer(scols, srows, wcols, wrows, coff, roff);
            rayTracer.readScene(input, texmap);
            BufferedImage image = null;
            try{
                image = rayTracer.draw();
            }catch(Exception e) {
                System.err.println(e.toString());
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "bmp", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return e.getMessage().getBytes();
        }
    }

    @Override
    public String handleRequest(Map<String,String> event, Context context) {
        Main.ANTI_ALIAS = Boolean.parseBoolean(event.getOrDefault("aa", "false"));
        Main.MULTI_THREAD = Boolean.parseBoolean(event.getOrDefault("multi", "false"));
        int scols = Integer.parseInt(event.get("scols"));
        int srows = Integer.parseInt(event.get("srows"));
        int wcols = Integer.parseInt(event.get("wcols"));
        int wrows = Integer.parseInt(event.get("wrows"));
        int coff = Integer.parseInt(event.get("coff"));
        int roff = Integer.parseInt(event.get("roff"));
        Base64.Decoder decoder = Base64.getDecoder();
        byte[] input = decoder.decode(event.get("input"));
        byte[] texmap = event.containsKey("texmap") ? decoder.decode(event.get("texmap")) : null;
        byte[] byteArrayResult = handleRequest(input, texmap, scols, srows, wcols, wrows, coff, roff);
        return Base64.getEncoder().encodeToString(byteArrayResult);
    }

    public String hashString(byte[] input) throws NoSuchAlgorithmException {
        MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
        messageDigest.update(input);
        return new String(messageDigest.digest());
    }

    public String getParameteString(Map<String, String> parameters) {
        String scols = parameters.get("scols");
        String srows = parameters.get("srows");
        String wcols = parameters.get("wcols");
        String wrows = parameters.get("wrows");
        String coff = parameters.get("coff");
        String roff = parameters.get("roff");
        return scols + srows + wcols + wrows + coff + roff; 
    }

    public double computeComplexity(Long nMethods, Long nBlocks, Long nInsts) {
        // TODO:
        // divide by the average
        double complexity = (nMethods * 9.03453830e-06 + nBlocks * 2.21379894e-06 + nInsts * 1.21117014e-06) / 425.1803402850727;
        return complexity;
    }
}
