package pt.ulisboa.tecnico.cnv.db;

import pt.ulisboa.tecnico.cnv.utils.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;

import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class MetricsFetcher {

    private DynamoDbClient ddb;

    private static final String TABLE_NAME = "RequestMetrics";
    public LRUCache<String, Double> cache;

    int CACHE_SIZE = 500;

    public MetricsFetcher() {
        this.ddb = DynamoDbClient.builder()
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(Utils.accessKey, Utils.secretKey)))
        .region(Region.of(Utils.regionStr))
        .build();

        DynamoDBInitializer.initialize(ddb);

        cache = new LRUCache<>(CACHE_SIZE);
    }

    public Double getRequestComplexity(String requestId) {
        HashMap<String, AttributeValue> keyToGet = new HashMap<>();
        keyToGet.put(":v_request_id", AttributeValue.builder().s(requestId).build());

        QueryRequest queryReq = QueryRequest.builder()
            .tableName(TABLE_NAME)
            .keyConditionExpression("RequestID = :v_request_id")
            .expressionAttributeValues(keyToGet)
            .limit(1)
            .scanIndexForward(false) // Get the latest entry
            .build();

        try {
            QueryResponse response = ddb.query(queryReq);
            if (!response.items().isEmpty()) {
                Map<String, AttributeValue> item = response.items().get(0);
                return Double.parseDouble(item.get("Complexity").n());
            }
        } catch (DynamoDbException e) {
            e.printStackTrace();
        }

        return null; // Indicates this request is new
    }

    public Double fetchFromCache(String requestId) {
        return cache.get(requestId);
    }

    
}
