package pt.ulisboa.tecnico.cnv.autoscaler;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Base64;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

public class RaytracerLambdaWorker implements Worker{

    public String type;

    public LambdaClient lambdaClient;

    public RaytracerLambdaWorker(LambdaClient lambdaClient, String type) {
        this.lambdaClient = lambdaClient; 
        this.type = type;
    }

    @Override
    public String forwardRequest(Job job, InputStream requestBody, URI requestedUri, Map<String, List<String>> headers, String requestMethod, int timeOutSeconds) throws Exception{
        String query = requestedUri.getRawQuery();
        Map<String, String> parameters = queryToMap(query);
        ObjectMapper mapper = new ObjectMapper();

        Integer scols = Integer.parseInt(parameters.get("scols"));
        Integer srows = Integer.parseInt(parameters.get("srows"));
        Integer wcols = Integer.parseInt(parameters.get("wcols"));
        Integer wrows = Integer.parseInt(parameters.get("wrows"));
        Integer coff = Integer.parseInt(parameters.get("coff"));
        Integer roff = Integer.parseInt(parameters.get("roff"));
        Boolean anitAlias = Boolean.parseBoolean(parameters.getOrDefault("aa", "false"));
        Boolean multiThread = Boolean.parseBoolean(parameters.getOrDefault("multi", "false"));

        Map<String, Object> body = mapper.readValue(requestBody, new TypeReference<>() {});

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

        // Create a map to hold the JSON structure
        Map<String, String> payloadMap = new HashMap<>();
        payloadMap.put("aa", anitAlias.toString());
        payloadMap.put("multi", multiThread.toString());

        payloadMap.put("scols", scols.toString());
        payloadMap.put("srows", srows.toString());
        payloadMap.put("wcols", wcols.toString());
        payloadMap.put("wrows", wrows.toString());
        payloadMap.put("coff", coff.toString());
        payloadMap.put("roff", roff.toString());

        String inputStr = Base64.getEncoder().encodeToString(input);
        
        payloadMap.put("input", inputStr);
        if (texmap != null) {
            String inputTexmap = Base64.getEncoder().encodeToString(texmap);
            payloadMap.put("texmap", inputTexmap);
        } 
        
        // Convert the map to a JSON string
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonPayload = objectMapper.writeValueAsString(payloadMap);
        
        SdkBytes payloadInput = SdkBytes.fromUtf8String(jsonPayload);
        
        InvokeRequest invokeRequest = InvokeRequest.builder()
                .functionName("raytracerLambda")
                .payload(payloadInput)
                .build();
        InvokeResponse invokeResponse = lambdaClient.invoke(invokeRequest); 

        String response = invokeResponse.payload().asUtf8String();
        response = response.substring(1, response.length()-1);      
        return  String.format("data:image/bmp;base64,%s", response);
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
    
}
