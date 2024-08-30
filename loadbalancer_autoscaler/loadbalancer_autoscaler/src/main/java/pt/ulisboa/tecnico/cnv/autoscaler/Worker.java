package pt.ulisboa.tecnico.cnv.autoscaler;

import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;

public interface Worker {

    public String forwardRequest(Job job, InputStream requestBody, URI requestUri, Map<String, List<String>> headers, String requestMethod, int timeOutSeconds) throws Exception;
    
}
