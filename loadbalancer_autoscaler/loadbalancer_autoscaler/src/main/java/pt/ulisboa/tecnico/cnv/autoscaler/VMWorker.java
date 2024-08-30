package pt.ulisboa.tecnico.cnv.autoscaler; 

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.time.Duration;

import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.ec2.waiters.Ec2Waiter;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import pt.ulisboa.tecnico.cnv.webserver.WebServer;

public class VMWorker implements Worker{

    public String name; 
    public String instanceID; 
    public String ipAddress;
    
    public Ec2Client ec2Client; 
    public CloudWatchClient cloudWatchClient;

    private double cpuUsage;

    List<Job> jobList = new CopyOnWriteArrayList<>();

    public VMWorker(Ec2Client ec2Client, CloudWatchClient cloudWatchClient, String name, String instanceID, String ipAddress) {
        this.ec2Client = ec2Client; 
        this.cloudWatchClient = cloudWatchClient;
        this.name = name; 
        this.instanceID = instanceID; 
        this.ipAddress = ipAddress; 
        this.cpuUsage = 50;
    }

    @Override
    public String forwardRequest(Job job, InputStream requestBody, URI requestedUri, Map<String, List<String>> headers, String requestMethod, int timeOutSeconds) throws Exception {
         // New uri
        String workerUriStr = String.format("http://%s:%d%s", this.ipAddress, WebServer.PORT, requestedUri.toString());
        URI workerUri;
        try{
            System.out.println(workerUriStr);
            workerUri = new URI(workerUriStr);
        } catch(URISyntaxException e) {
            throw(e);
        }

        addJob(job);
        HttpResponse<String> response;
        try{
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(workerUri)
                .method(requestMethod, HttpRequest.BodyPublishers.ofInputStream(() -> requestBody))
                .timeout(Duration.ofSeconds(timeOutSeconds)); // Set request timeout;
            
            for (Map.Entry<String, List<String>> header : headers.entrySet()) {
                for (String value : header.getValue()) {
                    try {
                        requestBuilder.header(header.getKey(), value);
                    } catch (IllegalArgumentException ignored) {
    
                    }
                }
            }
    
            HttpRequest request = requestBuilder.build();
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Status code " + response.statusCode());
            removeJob(job);
        } catch(Exception e) {
            removeJob(job);
            throw(e);
        }
        return response.body();
    }


    public double computeCPUUsage() {

        System.out.println(this.instanceID);

        // Define the instance ID and the time period for which you want to fetch the CPU utilization
        String instanceId = this.instanceID;
        Instant endTime = Instant.now();
        Instant startTime = endTime.minusSeconds(300); 

        // Create a GetMetricStatisticsRequest
        GetMetricStatisticsRequest request = GetMetricStatisticsRequest.builder()
        .namespace("AWS/EC2")
        .metricName("CPUUtilization")
        .dimensions(d -> d.name("InstanceId").value(instanceId))
        .startTime(startTime)
        .endTime(endTime)
        .period(60) 
        .statistics(Statistic.AVERAGE)
        .unit(StandardUnit.PERCENT)
        .build();

        // Fetch the metric statistics
        GetMetricStatisticsResponse response = cloudWatchClient.getMetricStatistics(request);

        // Process the data points
        List<Datapoint> dataPoints = response.datapoints();

        System.out.println("Data points: " + dataPoints.size());
        
        // set it to average cpu utilization (default)
        double result = 0; 

        for (Datapoint dp : dataPoints) {
            System.out.printf("Time: %s, Average CPU Utilization: %.2f%%\n",
                    dp.timestamp(), dp.average());
            result += dp.average(); 
        }

        if (!dataPoints.isEmpty()) {
            result = result / dataPoints.size();
        } else {
            result = 50;
        }

        return result;
    }

    public double getCPUUsage() {
        return this.cpuUsage;
    }

    public void addJob(Job job) { 
        jobList.add(job); 
    }

    public void removeJob(Job job) {
        jobList.remove(job); 
    }

    public void terminateWorker() {
        System.out.println("Terminate worker.");

        // Wait until all jobs are done 
        while (!jobList.isEmpty()) {
            try {
                TimeUnit.MILLISECONDS.sleep(20);
            } catch (InterruptedException e) {
                throw new RuntimeException();
            }
        }

        // Terminate the worker
        TerminateInstancesRequest terminateRequest = TerminateInstancesRequest.builder()
                .instanceIds(instanceID)
                .build();
        
        ec2Client.terminateInstances(terminateRequest); 

        DescribeInstancesRequest instanceRequest = DescribeInstancesRequest.builder()
                .instanceIds(instanceID)
                .build();

        Ec2Waiter ec2Waiter = Ec2Waiter.builder()
                            .overrideConfiguration(b -> b.maxAttempts(100))
                            .client(ec2Client)
                            .build();        

        WaiterResponse<DescribeInstancesResponse> waiterResponse = ec2Waiter
                .waitUntilInstanceTerminated(instanceRequest);
        
        // Print the termination status
        if (waiterResponse.matched().response().isEmpty()) {
            System.out.println("Instance " + instanceID + " is terminated.");
        } else {
            System.out.println("Instance " + instanceID + " is still running.");
        }

    }

    public Double getTotalEstimatedCosts() {
        return jobList.stream()
                .mapToDouble(Job::getEstimatedComplexity)
                .sum();
    }
}

