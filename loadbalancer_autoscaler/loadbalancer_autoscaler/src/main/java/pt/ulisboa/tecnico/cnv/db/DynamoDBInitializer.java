package pt.ulisboa.tecnico.cnv.db;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

public class DynamoDBInitializer {

    private static final String TABLE_NAME = "RequestMetrics";

    public static void initialize(DynamoDbClient ddb) {
        try {
            CreateTableRequest request = CreateTableRequest.builder()
                .attributeDefinitions(
                    AttributeDefinition.builder()
                        .attributeName("RequestID")
                        .attributeType(ScalarAttributeType.S)
                        .build(),
                    AttributeDefinition.builder()
                        .attributeName("Timestamp")
                        .attributeType(ScalarAttributeType.S)
                        .build())
                .keySchema(
                    KeySchemaElement.builder()
                        .attributeName("RequestID")
                        .keyType(KeyType.HASH)
                        .build(),
                    KeySchemaElement.builder()
                        .attributeName("Timestamp")
                        .keyType(KeyType.RANGE)
                        .build())
                .provisionedThroughput(
                    ProvisionedThroughput.builder()
                        .readCapacityUnits(100L)
                        .writeCapacityUnits(100L)
                        .build())
                .tableName(TABLE_NAME)
                .build();

            CreateTableResponse response = ddb.createTable(request);
            System.out.println("Table created successfully: " + response.tableDescription().tableName());

        } catch (ResourceInUseException e) {
            System.out.println("Table already exists.");
        } catch (DynamoDbException e) {
            e.printStackTrace();
        }
    }
}