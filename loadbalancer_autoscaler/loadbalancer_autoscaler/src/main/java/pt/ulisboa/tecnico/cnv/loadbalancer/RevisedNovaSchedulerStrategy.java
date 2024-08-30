package pt.ulisboa.tecnico.cnv.loadbalancer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import pt.ulisboa.tecnico.cnv.autoscaler.Autoscaler;
import pt.ulisboa.tecnico.cnv.autoscaler.Job;
import pt.ulisboa.tecnico.cnv.autoscaler.VMWorker;
import pt.ulisboa.tecnico.cnv.autoscaler.Worker;

import pt.ulisboa.tecnico.cnv.utils.Utils;

public class RevisedNovaSchedulerStrategy implements LoadBalanceStrategy {

    public RevisedNovaSchedulerStrategy() {
    }

    public Worker selectWorker(List<VMWorker> workers, Job job, Autoscaler autoscaler) {
        synchronized (workers) {
            List<VMWorker> filteredWorkers = filterWorkers(workers, job);
            if (filteredWorkers.isEmpty()) {
                return invokeLambdaOrScaleUp(job, autoscaler);
            } else {
                // TODO:
                // filteredWorkers instead of workers?
                sortWorkersByComplexity(filteredWorkers);
                Double totalAvailableComplexity = 0.0;
                for (VMWorker worker : filteredWorkers) {
                    totalAvailableComplexity += Utils.complexityMaximumThreshold - worker.getTotalEstimatedCosts();
                }
                double randomValue = Math.random();
                double cumulativeProbability = 0.0;
                for (VMWorker worker : filteredWorkers) {
                    cumulativeProbability += (Utils.complexityMaximumThreshold - worker.getTotalEstimatedCosts())
                            / totalAvailableComplexity;
                    if (randomValue <= cumulativeProbability) {
                        return worker; // Return the selected Worker
                    }
                }
            }
        }
        System.out.println("Worker could not be selected properly. Returning worker with index 0.");
        return workers.get(0); // Just to satisfy compiler needs. It shouldn't be reachable.
    }

    private List<VMWorker> filterWorkers(List<VMWorker> workers, Job job) {
        List<VMWorker> filteredWorkers = new ArrayList<VMWorker>();
        for (VMWorker worker : workers) {
            if ((complexityBelowMaximumThreshold(worker,
                    (Utils.complexityMaximumThreshold - job.getEstimatedComplexity())))
                    && (complexityAboveMinimumThreshold(worker,
                            (Utils.complexityMinimumThreshold - job.getEstimatedComplexity())))) {
                filteredWorkers.add(worker);
            }
        }
        return filteredWorkers;
    }

    private Worker invokeLambdaOrScaleUp(Job job, Autoscaler autoscaler) {
        if (job.getEstimatedComplexity() < Utils.complexityMinimumThreshold) {
            return invokeLambda(job, autoscaler);
        } else {
            System.out.println("Complexity scale up");
            Optional<VMWorker> optionalWorker = autoscaler.scaleUp();
            if (optionalWorker.isPresent()) {
                System.out.println("Complexity scale up in process");
                return optionalWorker.get();
            } else {
                System.out.println("Complexity scale up failed. Return null.");
                return null;
            }
        }
    }

    private Worker invokeLambda(Job job, Autoscaler autoscaler) {
        System.out.println("Create lambda of type " + job.type);
        if (job.type.equals("raytracer")) {
            return autoscaler.createRaytracerLambdaWorker("raytracer");
        } else if (job.type.equals("blurimage")) {
            return autoscaler.createImageProcessingLambdaWorker("blurimage");
        } else if (job.type.equals("enhanceimage")) {
            return autoscaler.createImageProcessingLambdaWorker("enhanceimage");
        } else {
            return null;
        }
    }

    private void sortWorkersByComplexity(List<VMWorker> filteredWorkers) {
        Collections.sort(filteredWorkers, Comparator.comparing(VMWorker::getTotalEstimatedCosts));
    }

    private boolean complexityAboveMinimumThreshold(VMWorker worker, Double complexityMinimumThreshold) {
        return worker.getTotalEstimatedCosts() >= complexityMinimumThreshold;
    }

    private boolean complexityBelowMaximumThreshold(VMWorker worker, Double complexityMaximumThreshold) {
        return worker.getTotalEstimatedCosts() <= complexityMaximumThreshold;
    }

}
