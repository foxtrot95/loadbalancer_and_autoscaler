package pt.ulisboa.tecnico.cnv.utils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;

import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutRequest;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemResponse;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.concurrent.ScheduledExecutorService; 

public class MetricsWriter {

    public List<Metrics> synchronizedMetricsList = Collections.synchronizedList(new ArrayList<>());
    private static final String TABLE_NAME = "RequestMetrics";
    private static DynamoDbClient ddb = DynamoDbClient.create();
    private ScheduledExecutorService scheduledExecutorService; 

    private static final int BATCH_SIZE = 25;

    public MetricsWriter() {
        // Add scheduled executor service to run periodic task 
        scheduledExecutorService = Executors.newScheduledThreadPool(1); 
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            writeListToDB();
            //writeListToDBInBatch();
        }, 0, 30, TimeUnit.SECONDS);
    }

    public void writeListToDB() {
        synchronized (synchronizedMetricsList) {
            for(Metrics metric:synchronizedMetricsList) {

                System.out.println(metric.requestType + metric.timestamp);
                processAndStoreMetrics(metric.requestId, metric.timestamp, metric.requestType, metric.complexity);
            }
            synchronizedMetricsList.clear();
        }
        System.out.println("Write to DB.");
    }

    public void writeListToDBInBatch() {
        List<WriteRequest> writeRequests = new ArrayList<>();
        synchronized (synchronizedMetricsList) {
            for(Metrics metric:synchronizedMetricsList) {
                Map<String, AttributeValue> item = new HashMap<>();
                item.put("RequestID", AttributeValue.builder().s(metric.requestId).build());
                item.put("Timestamp", AttributeValue.builder().s(metric.timestamp).build());
                item.put("RequestType", AttributeValue.builder().s(metric.requestType).build());
                item.put("Complexity", AttributeValue.builder().n(String.valueOf(metric.complexity)).build());
                PutRequest putRequest = PutRequest.builder().item(item).build();
                WriteRequest writeRequest = WriteRequest.builder().putRequest(putRequest).build();
                writeRequests.add(writeRequest);

                if (writeRequests.size() == BATCH_SIZE) {
                    batchWriteChunk(ddb, TABLE_NAME, writeRequests);
                    writeRequests.clear();
                }
            }
            synchronizedMetricsList.clear();
        }
    }

    private void batchWriteChunk(DynamoDbClient dynamoDbClient, String tableName, List<WriteRequest> writeRequests) {
        Map<String, List<WriteRequest>> requestItems = new HashMap<>();
        requestItems.put(tableName, writeRequests);

        BatchWriteItemRequest batchWriteItemRequest = BatchWriteItemRequest.builder()
                .requestItems(requestItems)
                .build();

        try {
            BatchWriteItemResponse response = dynamoDbClient.batchWriteItem(batchWriteItemRequest);
            if (!response.unprocessedItems().isEmpty()) {
                System.out.println("Some items were not processed. Retrying unprocessed items.");
                batchWriteChunk(dynamoDbClient, tableName, response.unprocessedItems().get(tableName));
            }
        } catch (DynamoDbException e) {
            System.err.println(e.getMessage());
        }
    }

    public void processAndStoreMetrics(String requestId, String timestamp, String requestType, Double complexity) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("RequestID", AttributeValue.builder().s(requestId).build());
        item.put("Timestamp", AttributeValue.builder().s(timestamp).build());
        item.put("RequestType", AttributeValue.builder().s(requestType).build());
        item.put("Complexity", AttributeValue.builder().n(String.valueOf(complexity)).build());

        PutItemRequest request = PutItemRequest.builder()
            .tableName(TABLE_NAME)
            .item(item)
            .build();

        try {
            ddb.putItem(request);
        
            System.out.println("Metrics stored successfully for request: " + requestId);
        } catch (DynamoDbException e) {
            e.printStackTrace();
        }
    }

}
