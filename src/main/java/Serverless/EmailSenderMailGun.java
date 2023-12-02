package Serverless;

import com.amazonaws.services.lambda.runtime.Context;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemResult;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;

import java.time.Instant;

public class EmailSenderMailGun {

    public static JsonNode sendSimpleMessage(Context ctx, String APIKey, String receiverEmail, String emailSubject, String emailContent, String DynamoDBTableName) throws UnirestException {
        HttpResponse<JsonNode> request = Unirest.post("https://api.mailgun.net/v3/" + "demo.fishdog.me" + "/messages")
			.basicAuth("api", APIKey)
                .queryString("from", "Excited User <mailgun@demo.fishdog.me>")
                .queryString("to", receiverEmail)
                .queryString("subject", emailSubject)
                .queryString("text", emailContent)
                .asJson();

        ctx.getLogger().log("Email successfully built.");

        // Save to DynamoDB
        saveEmailDetailsToDynamoDB(ctx, receiverEmail, emailSubject, emailContent, DynamoDBTableName);

        return request.getBody();
    }

    private static void saveEmailDetailsToDynamoDB(Context ctx, String receiverEmail, String emailSubject, String emailContent, String dynamoDBName) {
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
        DynamoDB dynamoDB = new DynamoDB(client);
        Table table = dynamoDB.getTable(dynamoDBName);

        try {
            Item item = new Item()
                    .withPrimaryKey("emailId", Instant.now().toString()) // Use a unique identifier, like a timestamp
                    .withString("receiverEmail", receiverEmail)
                    .withString("subject", emailSubject)
                    .withString("content", emailContent);

            table.putItem(item);
            ctx.getLogger().log("Email successfully saved in DynamoDB.");

        } catch (Exception e) {
            ctx.getLogger().log("Fail: Email NOT saved in DynamoDB." + e.getMessage());
            // Handle exceptions
        }
    }
}