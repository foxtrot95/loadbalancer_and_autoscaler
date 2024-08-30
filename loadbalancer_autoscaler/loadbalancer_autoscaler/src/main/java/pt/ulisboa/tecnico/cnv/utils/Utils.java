package pt.ulisboa.tecnico.cnv.utils;

public class Utils {
    public static String accessKey = System.getenv("AWS_ACCESS_KEY_ID");
    public static String secretKey = System.getenv("AWS_SECRET_ACCESS_KEY"); 
    public static String regionStr = System.getenv("AWS_DEFAULT_REGION"); 

    public static Double complexityMaximumThreshold = 20.0;
    public static Double complexityMinimumThreshold = 1.1;

    public static Double medianComplexity = 1.2;
}
