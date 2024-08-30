package pt.ulisboa.tecnico.cnv.loadbalancer;

import java.util.ArrayList;
import java.util.List;

import pt.ulisboa.tecnico.cnv.autoscaler.*;

public class RoundRobinStrategy implements LoadBalanceStrategy{

    private int count;
    
    public RoundRobinStrategy() {
        this.count = 0; 
    }

    @Override
    public VMWorker selectWorker(List<VMWorker> workers, Job job, Autoscaler autoscaler){
        int index = this.count % workers.size();
        VMWorker worker;
        synchronized(workers) {
            worker = workers.get(index);
        }
        return worker;
    }
    
}
