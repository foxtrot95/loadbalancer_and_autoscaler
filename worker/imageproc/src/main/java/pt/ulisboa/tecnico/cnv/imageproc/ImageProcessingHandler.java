package pt.ulisboa.tecnico.cnv.imageproc;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import pt.ulisboa.tecnico.cnv.javassist.tools.ICount;
import pt.ulisboa.tecnico.cnv.utils.*;

import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;


public abstract class ImageProcessingHandler implements HttpHandler, RequestHandler<Map<String,String>, String> {

    abstract BufferedImage process(BufferedImage bi) throws IOException;

    private static final String TABLE_NAME = "RequestMetrics";
    private static DynamoDbClient ddb = DynamoDbClient.create();

    MetricsWriter metricsWriter;

    public ImageProcessingHandler() {
        this.metricsWriter = null;
    }

    public ImageProcessingHandler(MetricsWriter metricsWriter) {
        this.metricsWriter = metricsWriter;
    }

    private String handleRequest(String inputEncoded, String format) {
        byte[] decoded = {};
        decoded = Base64.getDecoder().decode(inputEncoded);
        
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(decoded);
            BufferedImage bi = ImageIO.read(bais);
            ICount.resetStatistics();

            System.out.println("Start");
            long start = System.currentTimeMillis();
            bi = process(bi);
            System.out.println("Finished in: " + (System.currentTimeMillis()-start) + "ms");
            Long[] statistics = ICount.getStatistics();
            System.out.println(String.format("[ICount] Number of executed methods: %s", statistics[2]));
            System.out.println(String.format("[ICount] Number of executed basic blocks: %s", statistics[1]));
            System.out.println(String.format("[ICount] Number of executed instructions: %s", statistics[0]));
            System.out.println("End");

            // TODO 
            // Compute complexity
            String requestId = new String(Arrays.copyOfRange(decoded, decoded.length - 10, decoded.length), StandardCharsets.ISO_8859_1); 
            System.out.println("Type: " + getName());
            System.out.println("Request identifier: " + requestId);
            double complexity = computeComplexity(statistics[1]);
            if (metricsWriter != null) {
                Metrics metrics = new Metrics(requestId, Instant.now().toString(), getName(), complexity);
                metricsWriter.synchronizedMetricsList.add(metrics);
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bi, format, baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            return e.toString();
        }
    }

    @Override
    public void handle(HttpExchange t) throws IOException {
        // Handling CORS
        t.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

        if (t.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            t.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
            t.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,Authorization");
            t.sendResponseHeaders(204, -1);
            return;
        }

        InputStream stream = t.getRequestBody();
        // Result syntax: data:image/<format>;base64,<encoded image>
        String result = new BufferedReader(new InputStreamReader(stream)).lines().collect(Collectors.joining("\n"));
        String[] resultSplits = result.split(",");
        String format = resultSplits[0].split("/")[1].split(";")[0];

        String output = handleRequest(resultSplits[1], format);
        output = String.format("data:image/%s;base64,%s", format, output);

        t.sendResponseHeaders(200, output.length());
        OutputStream os = t.getResponseBody();
        os.write(output.getBytes());
        os.close();
    }

    @Override
    public String handleRequest(Map<String,String> event, Context context) {
        return handleRequest(event.get("body"), event.get("fileFormat"));
    }

    public String getName() {
        return new String("imageprocessing");
    }

    public double computeComplexity( Long nBlocks) {
        // TODO:
        // divide by the average
        double complexity = nBlocks * 3.28992149e-05 / 425.1803402850727;

        return complexity;
    }
}
