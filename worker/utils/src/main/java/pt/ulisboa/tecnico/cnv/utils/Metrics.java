package pt.ulisboa.tecnico.cnv.utils;

public class Metrics {
    String requestId; 
    String timestamp; 
    String requestType; 
    Double complexity; 

    public Metrics(String requestId, String timestamp, String requestType, Double complexity) {
        this.requestId = requestId; 
        this.timestamp = timestamp; 
        this.requestType = requestType; 
        this.complexity = complexity; 
    }
}
