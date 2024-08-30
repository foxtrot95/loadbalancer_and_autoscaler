package pt.ulisboa.tecnico.cnv.autoscaler; 

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import software.amazon.awssdk.services.lambda.LambdaClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.ScheduledExecutorService; 
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Optional;

import pt.ulisboa.tecnico.cnv.utils.*;

public class Autoscaler {

    String securityGroupID = System.getenv("AWS_SECURITY_GROUP_ID");
    String amiID = System.getenv("AWS_AMI_ID");

    private final int UPPER_CPU_THRESHOLD = 70; 
    private final int LOWER_CPU_THRESHOLD = 20;

    private final int NUM_THREADS = 1; 

    private Ec2Client ec2Client; 
    private CloudWatchClient cloudWatchClient;
    private LambdaClient lambdaClient;

    private ScheduledExecutorService scheduledExecutorService; 

    private List<VMWorker> list = new ArrayList<>(); 
    private List<VMWorker> workers = Collections.synchronizedList(list); 
    

    public Autoscaler() {
    
        this.ec2Client = Ec2Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(Utils.accessKey, Utils.secretKey)))
                .region(Region.of(Utils.regionStr))
                .build();
    
        this.cloudWatchClient = CloudWatchClient.builder()
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(Utils.accessKey, Utils.secretKey)))
                .region(Region.of(Utils.regionStr))
                .build();
        
        this.lambdaClient = LambdaClient.builder()
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(Utils.accessKey, Utils.secretKey)))
                .region(Region.of(Utils.regionStr))
                .build();
        
        printActiveInstances();
        // Add all active workers to the list
        addAllActiveWorkers();

        synchronized(workers) {
            if (workers.isEmpty()) {
                VMWorker worker = createWorker(); 
                workers.add(worker);
            }
        }
        
        // Add scheduled executor service to run periodic task 
        scheduledExecutorService = Executors.newScheduledThreadPool(NUM_THREADS); 
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            autoScale();
        }, 1, 2, TimeUnit.MINUTES);
    }

    public void printActiveInstances() {

        // Create a filter to find instances with the specific AMI ID
        Filter filterImageID = Filter.builder()
                .name("image-id")
                .values(amiID)
                .build();
        
        Filter filterRunningInstances = Filter.builder()
        .name("instance-state-name")
        .values("running")
        .build();
 
        // Describe EC2 instances
        DescribeInstancesRequest request = DescribeInstancesRequest.builder().filters(filterImageID, filterRunningInstances).build();
        DescribeInstancesResponse response = this.ec2Client.describeInstances(request);

        // Iterate through reservations (groups of instances)
        for (Reservation reservation : response.reservations()) {

            if (reservation.instances().isEmpty()) {
                System.out.println("No instance exists.");
            }

            // Iterate through instances in each reservation
            for (Instance instance : reservation.instances()) {
                // Print instance details
                System.out.println("Instance ID: " + instance.instanceId());
                System.out.println("Instance Type: " + instance.instanceType());
                System.out.println("Public IP Address: " + instance.publicIpAddress());
                System.out.println("Private IP Address: " + instance.privateIpAddress());
            }
        }
    }
   
    public void addAllActiveWorkers() {

        // Create a filter to find instances with the specific AMI ID
        Filter filterImageID = Filter.builder()
                .name("image-id")
                .values(amiID)
                .build();
        Filter filterRunningInstances = Filter.builder()
                .name("instance-state-name")
                .values("running")
                .build();
 
        // Describe EC2 instances
        DescribeInstancesRequest request = DescribeInstancesRequest.builder()
                .filters(filterImageID, filterRunningInstances)
                .build();
        DescribeInstancesResponse response = this.ec2Client.describeInstances(request);

        for (Reservation reservation : response.reservations()) {
            for (Instance instance : reservation.instances()) {
                if(instance.publicIpAddress() != null) {
                    VMWorker worker = new VMWorker(ec2Client, cloudWatchClient, "test", instance.instanceId(), instance.publicIpAddress());
                    workers.add(worker);  
                }
            }
        }
    }
    
    public void autoScale() {
        double averageCPUUsage = getAverageCPUUsage();
        System.out.println("Average CPU usage: " + averageCPUUsage);

        if(workers.size() > 1 && averageCPUUsage < LOWER_CPU_THRESHOLD) {
            scaleDown();
        } else if(averageCPUUsage > UPPER_CPU_THRESHOLD) {
            scaleUp(); 
        }
    }

    public double getAverageCPUUsage() {
        double averageCPUUsage = 0;
        synchronized(workers) {
            for(VMWorker worker:workers) {
                averageCPUUsage += worker.computeCPUUsage(); 
            }
            averageCPUUsage /= workers.size(); 
        }
       
        return averageCPUUsage;
    }

    public VMWorker createWorker() {
        try{
            RunInstancesRequest runRequest = RunInstancesRequest.builder()
                .imageId(amiID)
                .instanceType(InstanceType.T2_MICRO)
                .minCount(1)
                .maxCount(1)
                .securityGroupIds(securityGroupID)
                .build();

            RunInstancesResponse response = ec2Client.runInstances(runRequest); 
            Instance instance = response.instances().get(0); 
            ec2Client.waiter().waitUntilInstanceRunning(r -> r.instanceIds(instance.instanceId()));

            DescribeInstancesRequest requestUpdate = DescribeInstancesRequest.builder()
                                                                    .instanceIds(instance.instanceId())
                                                                    .build();

            DescribeInstancesResponse responseUpdated = ec2Client.describeInstances(requestUpdate);
            Instance instanceUpdated = responseUpdated.reservations().get(0).instances().get(0);

            // Enable monitoring
            MonitorInstancesRequest request = MonitorInstancesRequest.builder()
            .instanceIds(instanceUpdated.instanceId()).build();
            MonitorInstancesResponse monitoringResponse = ec2Client.monitorInstances(request);

            monitoringResponse.instanceMonitorings().forEach(monitoringState ->
                System.out.println("Monitoring state for instance " + monitoringState.instanceId() + ": " + monitoringState.toString())
            );

            VMWorker worker = new VMWorker(this.ec2Client, this.cloudWatchClient, "test", instanceUpdated.instanceId(), instanceUpdated.publicIpAddress()); 
            System.out.println(String.format("Created VM Worker %s %s", instanceUpdated.publicIpAddress(), instanceUpdated.instanceId()));
            return worker;
        } catch (Exception e) {
            System.err.println("Worker could not be created.");
            System.err.println(e);
            return null;
        }
    } 

    public void scaleDown() {
        System.out.println("Scale down.");
        // get lowest cpu usage 
        VMWorker lowestCPUWorker;
        synchronized(workers) {
            lowestCPUWorker = workers.stream()
                                    .min(Comparator.comparingDouble(VMWorker::getCPUUsage))
                                    .orElseThrow(() -> new RuntimeException("List is empty."));
            workers.remove(lowestCPUWorker); 
        }
        lowestCPUWorker.terminateWorker();
    }

    public Optional<VMWorker> scaleUp() {
        System.out.println("Scale up.");
        VMWorker worker = createWorker();
        workers.add(worker);
        return Optional.ofNullable(worker); // Return an Optional of Worker
    }

    public List<VMWorker> getWorkers() {
        return workers; 
    }

    public ImageProcessingLambdaWorker createImageProcessingLambdaWorker(String type) {
        return new ImageProcessingLambdaWorker(this.lambdaClient, type); 
    }

    public RaytracerLambdaWorker createRaytracerLambdaWorker(String type) {
        return new RaytracerLambdaWorker(this.lambdaClient, type);
    }

    public void removeWorker(Worker worker) {
        synchronized(workers) {
            if (workers.contains(worker))
                workers.remove(worker);
        }
    }
}
