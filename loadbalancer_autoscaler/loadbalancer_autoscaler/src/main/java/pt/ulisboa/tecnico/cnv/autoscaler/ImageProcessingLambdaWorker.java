package pt.ulisboa.tecnico.cnv.autoscaler;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

public class ImageProcessingLambdaWorker implements Worker{

    public String type; 

    public LambdaClient lambdaClient;

    public ImageProcessingLambdaWorker(LambdaClient lambdaClient, String type) {
        this.lambdaClient = lambdaClient;
        this.type = type; 
    }

    @Override
    public String forwardRequest(Job job, InputStream requestBody, URI requestedUri, Map<String, List<String>> headers, String requestMethod, int timeOutSeconds) throws Exception {
        String result = new BufferedReader(new InputStreamReader(requestBody)).lines().collect(Collectors.joining("\n"));
        String[] resultSplits = result.split(",");
        String fileFormat = resultSplits[0].split("/")[1].split(";")[0];
        String body = resultSplits[1];

        // Create a map to hold the JSON structure
        Map<String, String> payloadMap = new HashMap<>();
        payloadMap.put("body", body);
        payloadMap.put("fileFormat", fileFormat);
        
        // Convert the map to a JSON string
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonPayload = objectMapper.writeValueAsString(payloadMap);
        
        SdkBytes input = SdkBytes.fromUtf8String(jsonPayload);

        String functionName = "";
        if (type.equals("enhanceimage")) {
            functionName = "imageEnhanceLambda";
        } else {
            functionName = "imageBlurLambda"; 
        }
        
        InvokeRequest invokeRequest = InvokeRequest.builder()
                .functionName(functionName)
                .payload(input)
                .build();
        InvokeResponse invokeResponse = lambdaClient.invoke(invokeRequest); 

        String response = invokeResponse.payload().asUtf8String();
        response = response.substring(1, response.length()-1);      
        return  String.format("data:image/%s;base64,%s", fileFormat, response);
    }
    
}
