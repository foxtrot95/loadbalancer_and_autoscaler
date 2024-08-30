package pt.ulisboa.tecnico.cnv.autoscaler;

public class Job {

    public String requestId;
    public String type; 
    public Double estimatedComplexity; 

    public Job(String requestId, String type, Double estimatedComplexity) {
        this.requestId = requestId; 
        this.type = type; 
        this.estimatedComplexity = estimatedComplexity; 
    }

    public Double getEstimatedComplexity() {
        return estimatedComplexity;
    }
    
}
