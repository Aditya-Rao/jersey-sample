package my.service;

import com.amazonaws.serverless.proxy.internal.LambdaContainerHandler;
import com.amazonaws.serverless.proxy.jersey.JerseyLambdaContainerHandler;
import com.amazonaws.serverless.proxy.model.AwsProxyRequest;
import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;

import com.sun.org.apache.xml.internal.security.utils.Base64;
import my.service.resource.WeatherResource;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;

import javax.json.Json;
import javax.json.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

import static javax.json.Json.createObjectBuilder;


/**
 *  Lambda function getting current weather (Mountain View) from OpenWeatherAPI.
 */
public class StreamLambdaHandler implements RequestStreamHandler {
    // Logger to log events.
    private static final Logger LOGGER = LogManager.getLogger(StreamLambdaHandler.class.getName());

    // Register our resources with Jersey
    private static final ResourceConfig jerseyApplication = new ResourceConfig().packages("my.service.resource").register(JacksonFeature.class);
    private static final JerseyLambdaContainerHandler<AwsProxyRequest, AwsProxyResponse> handler
            = JerseyLambdaContainerHandler.getAwsProxyHandler(jerseyApplication);

    @Override
    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context)
            throws IOException {
        handler.proxyStream(inputStream, outputStream, context);


        /*
            Conditional integration of Lambda calling Jira
         */
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        os = (ByteArrayOutputStream) outputStream;
        AwsProxyResponse response = LambdaContainerHandler.getObjectMapper().readValue(os.toByteArray(), AwsProxyResponse.class);
        if(StringUtils.isNotEmpty(response.getBody())){
            LOGGER.debug("ResponseBodyFrom AWS LAMBDA:"+response.getBody());
            if(Float.parseFloat(response.getBody()) > 100.00) {
                LOGGER.info("Weather is Hot!!! Creating Jira...");
                // create Jira in Jira Local instance
                createJiraTicket("SEC", "Bug", "Test Summary", "Test Desc");

                //create Jira in Intuit Jira instance
//                createIntuitJiraTicket();
            } else {
                LOGGER.info("Weather is Not so Hot! NOT creating the Jira...");
            }
        }

        // just in case it wasn't closed by the mapper
        outputStream.close();
    }

    /**
     * Create a Jira on local Jira Instance.
     *
     * @param projectkey
     * @param issueType
     * @param summary
     * @param description
     */
    public void createJiraTicket(String projectkey, String issueType, String summary, String description){

        String value1 = WeatherResource.creds.get("Ad_2019");
        String cred = "Ad_2019:"+value1;
        projectkey = StringUtils.isBlank(projectkey) ? "SEC" : projectkey;
        summary = StringUtils.isBlank(summary) ? "Test Summary" : summary;
        description = StringUtils.isBlank(description) ? "Test Description" : description;

        StringBuilder serverResponse = new StringBuilder();

        try {
//            URL jiraREST_URL = new URL("http://host.docker.internal:8080/rest/api/2/issue/");
            URL jiraREST_URL = new URL("http://127.0.0.1:8080/rest/api/2/issue/");
            URLConnection urlConnection = jiraREST_URL.openConnection();
            urlConnection.setDoInput(true);

            HttpURLConnection conn = (HttpURLConnection) jiraREST_URL.openConnection();
            conn.setDoOutput(true);
            conn.setDoInput(true);

            String encodedData = getJSON_Body(projectkey, issueType, summary, description);

            LOGGER.debug("Json Payload:" + encodedData);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Basic " + Base64.encode(cred.getBytes(), 0));
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Content-Length", String.valueOf(encodedData.length()));
            conn.getOutputStream().write(encodedData.getBytes());

            try {
                InputStream inputStream = conn.getInputStream();
                serverResponse.append(convertInputStreamToString(inputStream));
                LOGGER.info("Response Output: "+conn.getResponseMessage());
                LOGGER.info("Response Code:"+conn.getResponseCode());
            }
            catch (IOException e){
                LOGGER.error(e.getMessage());
            }

        } catch (MalformedURLException e) {
            LOGGER.error(e.getMessage());
        } catch (IOException e) {
            LOGGER.error(e.getMessage());
        }
    }

    /**
     * Create a Jira on Intuit Jira Instance.
     */
    public void createIntuitJiraTicket(){
        String projectkey = "ENGPR";
        String summary = "TestJiraSummary "+System.currentTimeMillis();
        String description = "TestJiraDescription "+System.currentTimeMillis();
        String issueType = "Bug";
        String value2 = WeatherResource.creds.get("arao3");
        String cred2 = "arao3:"+value2;

        StringBuilder serverResponse = new StringBuilder();
        int status = 0;

        try {
            URL jiraREST_URL = new URL("https://jira.intuit.com/rest/api/2/issue/");
            URLConnection urlConnection = jiraREST_URL.openConnection();
            urlConnection.setDoInput(true);

            HttpURLConnection conn = (HttpURLConnection) jiraREST_URL.openConnection();
            conn.setDoOutput(true);
            conn.setDoInput(true);

            String encodedData = getJSON_BodyIntuit(projectkey, issueType, summary, description);

            LOGGER.debug("Json Payload:" + encodedData);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Basic " + Base64.encode(cred2.getBytes(), 0));
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Content-Length", String.valueOf(encodedData.length()));
            conn.getOutputStream().write(encodedData.getBytes());

            try {
                InputStream inputStream = conn.getInputStream();
                serverResponse.append(convertInputStreamToString(inputStream));
                LOGGER.info("Response Output: "+conn.getResponseMessage());
                LOGGER.info("Response Code:"+conn.getResponseCode());
            }
            catch (IOException e){
                LOGGER.error(e.getMessage());
            }

        } catch (MalformedURLException e) {
            LOGGER.error(e.getMessage());
        } catch (IOException e) {
            LOGGER.error(e.getMessage());
        }
    }

    /**
     * Method to Create the JSON Body of local Jira ticket.
     *
     * @param projectkey
     * @param issueType
     * @param summary
     * @param description
     * @return
     */
    private static String getJSON_Body(String projectkey, String issueType, String summary, String description){
        JsonObject createIssue = Json.createObjectBuilder()
                .add("fields",
                        Json.createObjectBuilder().add("project",
                                Json.createObjectBuilder().add("key",projectkey))
                                .add("summary", summary+System.currentTimeMillis())
                                .add("description", description+System.currentTimeMillis())
                                .add("issuetype",
                                        Json.createObjectBuilder().add("name", issueType))
                ).build();
        return createIssue.toString();

    }

    /**
     *  Method to Create the JSON Body of Intuit Jira ticket.
     *
     * @param projectkey
     * @param issueType
     * @param summary
     * @param description
     * @return
     */
    private static String getJSON_BodyIntuit(String projectkey, String issueType, String summary, String description){
        JsonObject createIssue = createObjectBuilder()
                .add("fields",
                        createObjectBuilder().add("project",
                                createObjectBuilder().add("key",projectkey))
                                .add("summary", summary+System.currentTimeMillis())
                                .add("description", description+System.currentTimeMillis())
                                .add("issuetype",
                                        createObjectBuilder().add("name", issueType))
                                .add("customfield_10302", createObjectBuilder().add("value", "None"))
                                .add("customfield_10502", createObjectBuilder().add("value", "None"))
                                .add("customfield_10207", Json.createArrayBuilder().add(createObjectBuilder().add("value", "Test")))
                                .add("versions", Json.createArrayBuilder().add(createObjectBuilder().add("name", "1.0")))
                ).build();
        return createIssue.toString();

    }

    /**
     * Method to Convert InputStream to String.
     *
     * @param inputStream
     * @return
     * @throws IOException
     */
    private static String convertInputStreamToString(InputStream inputStream)
            throws IOException {

        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toString(StandardCharsets.UTF_8.name());
    }
}