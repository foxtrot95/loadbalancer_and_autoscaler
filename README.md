# CNV Project: Load Balancer and Autoscaler Implementation

## Overview

This repository contains the implementation for a robust load balancer and autoscaler system, designed for efficient resource management and optimal performance in cloud environments. The system was developed as part of the CNV Project, focusing on dynamically distributing workloads and scaling resources based on real-time metrics.
Features

- Load Balancer: Utilizes a refined version of the Nova scheduler to ensure efficient task distribution among workers. The load balancer assesses the complexity of incoming requests and allocates them to the most suitable worker, optimizing resource usage and minimizing latency.
- Autoscaler: Dynamically adjusts the number of active virtual machines (VMs) based on system load. It scales up when the CPU utilization exceeds a defined threshold and scales down when the utilization drops below a certain level.
- Instrumentation: Captures key performance metrics such as executed instructions, basic blocks, and methods for each request, allowing for accurate complexity estimation and efficient load balancing.
- Metrics Storage System (MSS): A dedicated component to collect, store, and manage performance metrics from worker nodes using Amazon DynamoDB, facilitating better decision-making for both load balancing and autoscaling.

## Architecture

The system is built on Amazon Web Services (AWS) and comprises the following components:

- Workers: A combination of VMs and AWS Lambda functions to handle incoming tasks.
- Load Balancer (LB): Distributes tasks to workers based on their estimated complexity and current load.
- Autoscaler (AS): Monitors CPU usage and adjusts the number of active VMs accordingly.
- Metrics Storage System (MSS): Stores performance metrics that are crucial for optimizing load distribution and scaling operations.

## Key Design Choices

- Complexity Estimation: The system employs regression models to estimate the complexity of tasks, which is then used to predict service times and allocate resources more effectively.
- Efficient Data Management: Implements a Least Recently Used (LRU) cache for storing recent metrics to reduce database query overhead and improve performance.
- Thread Safety: Ensures data consistency and integrity in a multi-threaded environment through synchronized data structures and robust error-handling mechanisms.

## File Organization

## /loadbalancer_autoscaler

This directory contains the implementation for the load balancer, autoscaler, and database interaction:

**autoscaler/**: Contains the autoscaler implementation for managing AWS Lambda functions, processing requests, and handling VM workers.

**load_balancer/**: Includes the load balancer implementation and strategies for distributing workloads.

**db/**: Provides the code for interacting with DynamoDB.

**utils/**: Contains utility functions and tools for modifying system parameters.

## /deployment scripts

This directory contains scripts for deploying the system on AWS. Follow these steps to create an AWS AMI:

**1-Revise the config.sh with your own AWS credentials**

**2-Run launch-vm.sh to create a AWS VM**

**3-Run install-vm.sh to upload the code to the created VM, and prepare it for java to function on startup.**

**4-Run create-image.sh to create the AWS AMI**

## /worker

This directory includes the original imageproc and raytracer code, with added functionality for writing to DynamoDB and performance instrumentation.

Note: The resources/ subdirectories within image_proc and raytracer contain experiments for testing different workloads.
