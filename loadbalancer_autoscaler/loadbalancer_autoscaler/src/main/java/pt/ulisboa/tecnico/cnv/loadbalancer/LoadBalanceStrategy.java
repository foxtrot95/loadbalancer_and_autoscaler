package pt.ulisboa.tecnico.cnv.loadbalancer;

import java.util.ArrayList;
import java.util.List;
import pt.ulisboa.tecnico.cnv.autoscaler.*;

public interface LoadBalanceStrategy {
    Worker selectWorker(List<VMWorker> workers, Job job, Autoscaler autoscaler);
}
