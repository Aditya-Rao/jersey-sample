package my.service.resource;


import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.services.secretsmanager.model.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.security.InvalidParameterException;

import java.util.Base64;
import java.util.Map;
import java.util.HashMap;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/weather")
public class WeatherResource {

    private static final Logger LOGGER = LogManager.getLogger(WeatherResource.class.getName());

    public static String awscredentials;
    public static Map<String,String> creds;

    public WeatherResource() {
        awscredentials = getSecret();
        creds = getCredsinMap(awscredentials);
    }
    /**
     * REST GET call to Return current temperature from OpenWeatherMap website.
     *
     * @return
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.WILDCARD)
    public Response getCurrWeather() {
        WeatherAPI api = new WeatherAPI();
        String temp = api.getTemp();
        LOGGER.info("GOT TEMP from GET call"+temp);
        return Response.status(200).entity(temp).build();
    }


    /**
     *
     * Static nested class to get the current weather.
     *
     */
    static class WeatherAPI {


        StringBuilder sb = new StringBuilder();

        // Convert from Json String to Map
        public Map<String, Object> jsonToMap(String str) {
            Map<String, Object> map = new Gson().fromJson(str, new TypeToken<HashMap<String, Object>>() {}.getType());
            return map;
        }

        public String getTemp() {

//            String API_KEY = System.getenv("OWAPIKEY");
            String API_KEY = creds.get("OWAPIKEY");
            String Zip = "94043,us";
            String urlString = "http://api.openweathermap.org/data/2.5/weather?appid="+API_KEY+"&zip="+Zip+"&units=imperial";
            try {
                StringBuilder result = new StringBuilder();
                URL url = new URL(urlString);
                URLConnection conn = url.openConnection();
                BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                while ((line = rd.readLine()) != null) {
                    result.append(line);
                }
                rd.close();
                LOGGER.debug(result.toString());

                Map<String, Object> respMap = jsonToMap(result.toString());
                Map<String, Object> mainMap = jsonToMap(respMap.get("main").toString());
                sb.append(mainMap.get("temp"));
            } catch (IOException e){
                LOGGER.error(e.getMessage());
            }
            return sb.toString();
        }
    }


    /**
     * Method to return Secrets from AWS Secrets Manager
     * @return
     */
    public static String getSecret() {
        LOGGER.info(">>>>>>>>>>>  Getting Secrets from AWS Secret Manager............");

        String secretName = "prod/OWAPIKEY";
        String region = "us-west-1";
        StringBuilder secret = new StringBuilder();
        StringBuilder decodedBinarySecret = new StringBuilder();

        // Create a Secrets Manager client
        AWSSecretsManager client  = AWSSecretsManagerClientBuilder.standard()
                .withRegion(region)
                .build();

        GetSecretValueRequest getSecretValueRequest = new GetSecretValueRequest()
                .withSecretId(secretName);
        GetSecretValueResult getSecretValueResult = null;

        try {
            getSecretValueResult = client.getSecretValue(getSecretValueRequest);
        } catch (DecryptionFailureException e) {
            // Secrets Manager can't decrypt the protected secret text using the provided KMS key.
            // Deal with the exception here, and/or rethrow at your discretion.
            throw e;
        } catch (InternalServiceErrorException e) {
            // An error occurred on the server side.
            // Deal with the exception here, and/or rethrow at your discretion.
            throw e;
        } catch (InvalidParameterException e) {
            // You provided an invalid value for a parameter.
            // Deal with the exception here, and/or rethrow at your discretion.
            throw e;
        } catch (InvalidRequestException e) {
            // You provided a parameter value that is not valid for the current state of the resource.
            // Deal with the exception here, and/or rethrow at your discretion.
            throw e;
        } catch (ResourceNotFoundException e) {
            // We can't find the resource that you asked for.
            // Deal with the exception here, and/or rethrow at your discretion.
            throw e;
        }

        // Decrypts secret using the associated KMS CMK.
        // Depending on whether the secret is a string or binary, one of these fields will be populated.
        if (getSecretValueResult.getSecretString() != null) {
            secret.append(getSecretValueResult.getSecretString());
        }
        else {
            decodedBinarySecret.append(new String(Base64.getDecoder().decode(getSecretValueResult.getSecretBinary()).array()));
        }

        return secret.toString();
    }

    /**
     * Method to convert the credentials list(String)to Map.
     *
     * @param credentials
     * @return
     */
    public Map<String, String> getCredsinMap(String credentials) {
        if(StringUtils.isEmpty(credentials)){
            return null;
        }
        credentials = credentials.replace("{", "");
        credentials = credentials.replace("}", "");
        credentials = credentials.replace("\"", "");

        Map<String, String> maps = new HashMap<>();
        String[] strList = credentials.split(",");
        for(int i=0; i<strList.length; i++){
            String[] temp = strList[i].split(":");
            for(int j=0; j<temp.length; j++){
                if(j+1!=temp.length){
                    maps.put(temp[j],temp[j+1]);
                }
            }
        }
        return maps;
    }


}