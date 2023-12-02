package Serverless;

import java.io.IOException;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.json.JSONException;
import org.json.JSONObject;


public class Serverless implements RequestHandler<SNSEvent, String> {
    private static final String bucketName = System.getenv("GCP_STORAGE_BUCKET_NAME");
    private String objectName;
    private String submitterEmail;
    private String assignmentId;
    private String submissionAttempt;
    private String submissionUrl;

    private final String mailgunAPIKey = System.getenv("MAILGUN_API");
    public String handleRequest(SNSEvent event, Context context) {

        ObjectMapper objectMapper = new ObjectMapper();
        try {

            String dynamoDBTableName = System.getenv("DYNAMODB_TABLE_NAME");
            // Loop through the records, there can be more than one record in SNS Event
            for (SNSEvent.SNSRecord record : event.getRecords()) {
                // Get the SNS message
                SNSEvent.SNS sns = record.getSNS();
                String messageMessage = sns.getMessage();

                // Log the SNS message
                context.getLogger().log("Received SNS message: " + messageMessage);

//                try {
//                    JSONObject messageJson = new JSONObject(messageMessage); // Parse the string to a JSON object
//                    // Now, extract data from messageJson as needed
//                    assignmentId = messageJson.getString("assignmentId");
//                    submitterEmail = messageJson.getString("submitterEmail");
//                    submissionAttempt = messageJson.getString("submissionAttempt");
//                    submissionUrl = messageJson.getString("submissionUrl");
//
//                    // Process the extracted information...
//                } catch (JSONException e) {
//                    context.getLogger().log("Error parsing message JSON: " + e.getMessage());
//                }
//                objectName = "submissions/" + submitterEmail + assignmentId + "Attempt" + submissionAttempt;

                // Parse the message JSON to get submissionUrl
                JsonNode messageNode = objectMapper.readTree(messageMessage);
                String submissionUrl = messageNode.get("submissionUrl").asText();
                submitterEmail = messageNode.get("submitterEmail").asText();
                objectName = "submissions/" + submitterEmail + messageNode.get("assignmentId").asText() + "Attempt" + messageNode.get("submissionAttempt").asText();

                context.getLogger().log("objectName" + objectName);
//                context.getLogger().log("Mailgun API key" + mailgunAPIKey);
                FileTransmitter.downloadFile(context, submissionUrl, submitterEmail, mailgunAPIKey, dynamoDBTableName);
                context.getLogger().log("download succeeded from " + submissionUrl);


            }

            return FileTransmitter.uploadToGCS(context, bucketName, objectName, submitterEmail, mailgunAPIKey, dynamoDBTableName);

        } catch (IOException e) {
            return e.getMessage();
        } catch (UnirestException e) {
            throw new RuntimeException(e);
        }
    }


    public static void main(String[] args){

        new Serverless().handleRequest(null, null);

    }
}
