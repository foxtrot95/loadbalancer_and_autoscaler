package pt.ulisboa.tecnico.cnv.loadbalancer;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import pt.ulisboa.tecnico.cnv.autoscaler.*;
import pt.ulisboa.tecnico.cnv.db.MetricsFetcher;
import pt.ulisboa.tecnico.cnv.utils.Utils;

import org.apache.commons.io.IOUtils; 

public class LoadBalancer implements HttpHandler {

    protected Autoscaler autoscaler;
    protected MetricsFetcher metricsFetcher; 
    protected LoadBalanceStrategy loadBalanceStrategy;

    private final static ObjectMapper mapper = new ObjectMapper();

    public LoadBalancer() {
        this.autoscaler = new Autoscaler();
        this.metricsFetcher = new MetricsFetcher(); 
        //this.loadBalanceStrategy = new RoundRobinStrategy(); 
        this.loadBalanceStrategy = new RevisedNovaSchedulerStrategy(); 
    }


    @Override
    public void handle(HttpExchange he) throws IOException {

        // parse request
        URI requestedUri = he.getRequestURI();
        String type = requestedUri.getRawPath().replace("/", "");
        System.out.println("Type: " + type);

        // parameters 
        String query = requestedUri.getRawQuery();
        Map<String, String> parameters = queryToMap(query); 

        // Get identifier 
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        IOUtils.copy(he.getRequestBody(), baos);
        byte[] bytes = baos.toByteArray();

        String requestId = ""; 
        try{
            ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
            requestId = getRequestId(type, stream, parameters);
        } catch(Exception e) {
            System.err.println(e.toString());
            return;
        }
        System.out.println("RequestId: " + requestId);
        
        Double estimatedComplexity = retrieveComplexity(requestId);

        if (estimatedComplexity > Utils.complexityMaximumThreshold) {
            estimatedComplexity = Utils.complexityMaximumThreshold;
        }
        //Create job
        Job job = createJob(requestId, type, estimatedComplexity);

        // Select worker
        List<VMWorker> workers = autoscaler.getWorkers();
        Worker worker = loadBalanceStrategy.selectWorker(workers, job, autoscaler); 

        // testing lambda functions
        //Worker worker = autoscaler.createImageProcessingLambdaWorker(type);
        //Worker worker = autoscaler.creatRaytracerLambdaWorker(type);
        
        // Get reponse of worker
        String response = "";
        Map<String, List<String>> requestHeaders = he.getRequestHeaders();
        ByteArrayInputStream stream;

        boolean notServed = true;
        Integer timeOutSeconds = 60;
        while(notServed) {
            try{
                stream = new ByteArrayInputStream(bytes);
                System.out.println("Forward request.");
                response = worker.forwardRequest(job, stream, requestedUri, requestHeaders, he.getRequestMethod(), timeOutSeconds);
                System.out.println("Got response.");
                notServed = false;
            } catch (Exception e) { 
                autoscaler.removeWorker(worker);
                worker = loadBalanceStrategy.selectWorker(workers, job, autoscaler);
                Random random = new Random();

                // Generate a random sleep duration between 1000 and 5000 milliseconds (1 to 5 seconds)
                int minSleepTime = 1000; // minimum sleep time in milliseconds
                int maxSleepTime = 5000; // maximum sleep time in milliseconds
                int sleepTime = minSleepTime + random.nextInt(maxSleepTime - minSleepTime + 1);

                System.out.println("Sleeping for " + sleepTime + " milliseconds.");

                try {
                    // Sleep for the randomly generated duration
                    Thread.sleep(sleepTime);
                } catch (InterruptedException interruptedException) {
                    // Handle the interrupted exception
                    interruptedException.printStackTrace();
                }

                System.err.println("Error while forwarding request. Try again with time out " + timeOutSeconds.toString());
                System.err.println(e.toString());

                if(timeOutSeconds >= 600) {
                    timeOutSeconds = 600;
                } else {
                    timeOutSeconds *= 2;
                }
            }
        }
        
        // Send response back 
        byte[] output = response.getBytes();

        // Handling CORS
        he.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

        if (he.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            he.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
            he.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,Authorization");
            he.sendResponseHeaders(204, -1);
            return;
        }

        he.sendResponseHeaders(200, output.length);

        OutputStream os = he.getResponseBody(); 
        os.write(output);
        os.close();
        
        he.close();
    }

    private Double retrieveComplexity(String requestId) {
        // try to catch from cache 
        Double estimatedComplexity = metricsFetcher.fetchFromCache(requestId);
        if(estimatedComplexity == null) {
            // request it from DB 
            estimatedComplexity = metricsFetcher.getRequestComplexity(requestId); 
            if (estimatedComplexity == null) { // if not in DB assume median (standard) complexity
                estimatedComplexity = Utils.medianComplexity;
                System.out.println("Assuming median complexity");
            } else {
                metricsFetcher.cache.put(requestId, estimatedComplexity); // else put it in the cache
                System.out.println("Retrieved complexity " + estimatedComplexity + " from database");
            }
        } else {
            System.out.println("Retrieved complexity " + estimatedComplexity + " from cache");
        } 
        return estimatedComplexity;
    }

    private String getRequestId(String type, InputStream stream, Map<String, String> parameters) throws IOException{
        String requestId = ""; 
        if(type.equals("enhanceimage") || type.equals("blurimage")) {
            System.out.println("get identifier");
            String result = new BufferedReader(new InputStreamReader(stream)).lines().collect(Collectors.joining("\n"));
            String[] inputEncoded = result.split(",");

            byte[] decoded = Base64.getDecoder().decode(inputEncoded[1]);
            requestId = new String(Arrays.copyOfRange(decoded, decoded.length - 10, decoded.length), StandardCharsets.ISO_8859_1); 
        } else if (type.equals("raytracer")) {
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

            requestId = inputId + patternId + texmapId; 
            System.out.println("RequestID: " + requestId);
        }
        return requestId; 
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

    public Job createJob(String identifier, String type, Double estimatedComplexity){
        Job job = new Job(identifier, type, estimatedComplexity);
        return job;
    }
}